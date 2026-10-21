package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.entity.StoreOrderSales;
import br.com.nuvemcustomfields.entity.StoreOrderSalesId;
import br.com.nuvemcustomfields.entity.StoreSalesSync;
import br.com.nuvemcustomfields.repository.StoreOrderSalesRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.repository.StoreSalesSyncRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BackofficeSalesService {
    @org.springframework.beans.factory.annotation.Autowired
    private OrderPersonalizationCatalog personalizationCatalog;

    private static final Logger LOGGER = LoggerFactory.getLogger(BackofficeSalesService.class);
    private static final int PAGE_SIZE = 200;
    private static final int MAX_PAGES_PER_RANGE = 50; // Limite de 10 mil resultados da API.

    private final br.com.nuvemcustomfields.repository.PersonalizationFieldRepository fields;
    private final StoreRepository stores;
    private final StoreOrderSalesRepository orderSales;
    private final StoreSalesSyncRepository syncStates;
    private final NuvemshopApiClient apiClient;
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.beans.factory.annotation.Qualifier("deploymentBackgroundExecutor")
    private java.util.concurrent.ExecutorService backgroundExecutor;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile boolean stopping;
    @org.springframework.beans.factory.annotation.Value("${analytics.sales-sync-stores-per-run:5}")
    private int storesPerRun = 5;

    @org.springframework.context.event.EventListener(org.springframework.context.event.ContextClosedEvent.class)
    @org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
    public synchronized void stopSync() {
        stopping = true;
    }

    private void checkStopping() {
        if (stopping || Thread.currentThread().isInterrupted()) throw new SyncPaused();
    }

    private static final class SyncPaused extends RuntimeException {}

    public BackofficeSalesService(StoreRepository stores, StoreOrderSalesRepository orderSales,
                                  StoreSalesSyncRepository syncStates, NuvemshopApiClient apiClient,
                                  br.com.nuvemcustomfields.repository.PersonalizationFieldRepository fields) {
        this.fields = fields;
        this.stores = stores;
        this.orderSales = orderSales;
        this.syncStates = syncStates;
        this.apiClient = apiClient;
    }

    public SalesSummary summary() {
        List<Store> active = stores.findAll().stream().filter(Store::isActive).toList();
        Map<Long, StoreSalesSync> states = syncStates.findAll().stream()
                .collect(Collectors.toMap(StoreSalesSync::getStoreId, Function.identity()));
        long synced = active.stream().filter(store -> {
            StoreSalesSync state = states.get(store.getStoreId());
            return state != null && state.isComplete() && state.getLastError() == null;
        }).count();
        long personalizedValueBackfilled = active.stream().filter(store -> {
            StoreSalesSync state = states.get(store.getStoreId());
            return state != null && state.isPersonalizedValueBackfilled();
        }).count();
        return new SalesSummary(orderSales.personalizedOrdersFromSyncedActiveStores(),
                orderSales.personalizedItemsFromSyncedActiveStores(), synced, personalizedValueBackfilled, active.size());
    }

    public Page<SalesRow> salesPage(int page) {
        Page<StoreOrderSales> sales = orderSales.findSales(PageRequest.of(Math.max(1, page) - 1, 50));
        Set<Long> storeIds = sales.stream().map(sale -> sale.getId().getStoreId()).collect(Collectors.toSet());
        Map<Long, Store> storesById = stores.findByStoreIdIn(storeIds).stream()
                .collect(Collectors.toMap(Store::getStoreId, Function.identity()));
        return sales.map(sale -> {
            Store store = storesById.get(sale.getId().getStoreId());
            String currency = sale.getCurrency();
            if (currency == null && store != null) currency = store.getStoreCurrency();
            return new SalesRow(sale.getId().getStoreId(),
                    store == null || store.getStoreName() == null || store.getStoreName().isBlank()
                            ? "Loja sem nome" : store.getStoreName(),
                    currency, sale.getId().getOrderId(),
                    sale.getCreatedAt(), sale.getPersonalizedItems(), sale.getPersonalizedProductValue());
        });
    }

    @Scheduled(initialDelayString = "${analytics.sales-sync-initial-delay-ms:120000}",
               fixedDelayString = "${analytics.sales-sync-delay-ms:120000}")
    public synchronized void scheduleSync() {
        if (stopping) return;
        if (!running.compareAndSet(false, true)) {
            return;
        }
        // A importacao historica pode durar minutos; ela nao deve bloquear os jobs de pagamento.
        try {
            backgroundExecutor.execute(() -> {
                try {
                    for (int index = 0; index < Math.max(1, storesPerRun); index++) {
                        checkStopping();
                        syncOnePendingStore();
                    }
                } catch (SyncPaused paused) {
                    LOGGER.info("backoffice.sales_sync.paused reason=shutdown");
                } catch (RuntimeException ex) {
                    LOGGER.warn("backoffice.sales_sync.unexpected_error error={}", ex.toString());
                } finally {
                    running.set(false);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            running.set(false);
            if (!stopping) throw ex;
        }
    }

    void syncOnePendingStore() {
        checkStopping();
        Instant now = Instant.now();
        Map<Long, StoreSalesSync> states = syncStates.findAll().stream()
                .collect(Collectors.toMap(StoreSalesSync::getStoreId, Function.identity()));
        Store store = stores.findAll().stream()
                .filter(Store::isActive)
                .filter(candidate -> candidate.getAccessToken() != null && !candidate.getAccessToken().isBlank())
                .filter(candidate -> hasReadOrdersScope(candidate.getScope()))
                .filter(candidate -> {
                    StoreSalesSync state = states.get(candidate.getStoreId());
                    if (state == null) return true;
                    if (state.getLastError() != null && state.getLastAttemptAt() != null
                            && state.getLastAttemptAt().isAfter(now.minus(Duration.ofHours(1)))) return false;
                    return !state.isComplete() || !state.isPersonalizedValueBackfilled() || state.getLastSyncedAt() == null
                            || state.getLastSyncedAt().isBefore(now.minus(Duration.ofHours(24)));
                })
                .min(Comparator.<Store, Instant>comparing(candidate -> {
                    StoreSalesSync state = states.get(candidate.getStoreId());
                    return state == null || state.getLastAttemptAt() == null ? Instant.EPOCH : state.getLastAttemptAt();
                }).thenComparing(candidate -> candidate.getPlan().isBillable() ? 0 : 1)
                        .thenComparing(Store::getStoreId))
                .orElse(null);
        if (store == null) return;

        StoreSalesSync state = states.getOrDefault(store.getStoreId(), new StoreSalesSync(store.getStoreId()));
        state.setLastAttemptAt(now);
        state.setLastError(null);
        syncStates.save(state);
        try {
            checkStopping();
            var personalization = personalizationCatalog == null ? new OrderPersonalization(fields.findWithRuleByStoreId(store.getStoreId()))
                    : personalizationCatalog.forStore(store.getStoreId());
            if (state.isComplete() && state.isPersonalizedValueBackfilled() && state.getLastSyncedAt() != null) {
                importRange(store, null, null, state.getLastSyncedAt().minus(Duration.ofHours(1)), now, personalization);
            } else {
                Instant start = state.getHistoricalThrough() == null
                        ? Instant.parse("2000-01-01T00:00:00Z") : state.getHistoricalThrough();
                while (start.isBefore(now)) {
                    var date = start.atOffset(ZoneOffset.UTC);
                    Instant end = (date.getYear() < 2025 ? date.plusYears(5) : date.plusMonths(3)).toInstant();
                    if (end.isAfter(now)) end = now;
                    importRange(store, start, end, null, null, personalization);
                    checkStopping();
                    state.setHistoricalThrough(end);
                    syncStates.save(state);
                    start = end;
                }
            }
            checkStopping();
            state.setComplete(true);
            state.setPersonalizedValueBackfilled(true);
            state.setLastSyncedAt(now);
            syncStates.save(state);
            LOGGER.info("backoffice.sales_sync.done store_id={}", store.getStoreId());
        } catch (SyncPaused paused) {
            LOGGER.info("backoffice.sales_sync.paused store_id={} reason=shutdown", store.getStoreId());
        } catch (RuntimeException ex) {
            if (stopping || Thread.currentThread().isInterrupted()) {
                LOGGER.info("backoffice.sales_sync.paused store_id={} reason=shutdown", store.getStoreId());
                return;
            }
            state.setLastError(syncError(ex));
            syncStates.save(state);
            LOGGER.warn("backoffice.sales_sync.failed store_id={} error={}", store.getStoreId(), ex.toString());
        }
    }

    private static String syncError(RuntimeException ex) {
        if (ex instanceof org.springframework.web.client.RestClientResponseException http) {
            return switch (http.getStatusCode().value()) {
                case 401 -> "AUTH_REQUIRED";
                case 402 -> "PLATFORM_BLOCKED";
                case 403 -> "PERMISSION_REQUIRED";
                case 429 -> "RATE_LIMITED";
                default -> "HTTP_" + http.getStatusCode().value();
            };
        }
        return ex.getClass().getSimpleName();
    }

    private void importRange(Store store, Instant createdMin, Instant createdMax,
                             Instant updatedMin, Instant updatedMax, OrderPersonalization personalization) {
        for (int page = 1; page <= MAX_PAGES_PER_RANGE; page++) {
            checkStopping();
            JsonNode orders = apiClient.listOrdersForSales(store, page, PAGE_SIZE,
                    createdMin, createdMax, updatedMin, updatedMax);
            checkStopping();
            if (orders == null || !orders.isArray()) {
                throw new IllegalStateException("Resposta de pedidos invalida");
            }
            List<StoreOrderSales> batch = new ArrayList<>();
            List<StoreOrderSalesId> irrelevantOrders = new ArrayList<>();
            for (JsonNode order : orders) {
                long orderId = order.path("id").asLong();
                if (orderId <= 0) continue;
                StoreOrderSales sale = summarizeOrder(store.getStoreId(), order, personalization);
                if (sale.getPersonalizedItems() > 0) {
                    batch.add(sale);
                } else {
                    irrelevantOrders.add(sale.getId());
                }
            }
            if (!batch.isEmpty()) orderSales.saveAll(batch);
            if (!irrelevantOrders.isEmpty()) orderSales.deleteAllByIdInBatch(irrelevantOrders);
            if (orders.size() < PAGE_SIZE) return;
            if (page == MAX_PAGES_PER_RANGE) {
                Instant rangeMin = createdMin != null ? createdMin : updatedMin;
                Instant rangeMax = createdMax != null ? createdMax : updatedMax;
                if (rangeMin == null || rangeMax == null
                        || Duration.between(rangeMin, rangeMax).compareTo(Duration.ofHours(1)) <= 0) {
                    throw new IllegalStateException("Intervalo de pedidos excede o limite da API");
                }
                Instant midpoint = rangeMin.plusMillis(Duration.between(rangeMin, rangeMax).toMillis() / 2);
                if (createdMin != null) {
                    importRange(store, createdMin, midpoint, null, null, personalization);
                    importRange(store, midpoint, createdMax, null, null, personalization);
                } else {
                    importRange(store, null, null, updatedMin, midpoint, personalization);
                    importRange(store, null, null, midpoint, updatedMax, personalization);
                }
                return;
            }
        }
    }

    static StoreOrderSales summarizeOrder(Long storeId, JsonNode order, OrderPersonalization personalization) {
        long total = 0;
        long personalized = 0;
        BigDecimal personalizedProductValue = BigDecimal.ZERO;
        boolean missingPrice = false;
        if ("paid".equalsIgnoreCase(order.path("payment_status").asText())
                && !"cancelled".equalsIgnoreCase(order.path("status").asText())) {
            for (JsonNode product : order.path("products")) {
                long quantity = Math.max(0, product.path("quantity").asLong());
                total += quantity;
                if (!personalization.productProperties(product).isEmpty()) {
                    personalized += quantity;
                    if (quantity > 0) {
                        try {
                            BigDecimal price = new BigDecimal(product.path("price").asText());
                            personalizedProductValue = personalizedProductValue.add(price.multiply(BigDecimal.valueOf(quantity)));
                        } catch (NumberFormatException ex) {
                            missingPrice = true;
                        }
                    }
                }
            }
        }
        Instant createdAt = null;
        try {
            if (order.path("created_at").isTextual()) {
                String raw = order.path("created_at").asText();
                // A API tambem envia offsets como +0000, sem os dois-pontos do ISO padrao.
                if (raw.matches(".*[+-]\\d{4}$")) {
                    raw = raw.substring(0, raw.length() - 2) + ":" + raw.substring(raw.length() - 2);
                }
                createdAt = OffsetDateTime.parse(raw).toInstant();
            }
        } catch (RuntimeException ex) {
            LOGGER.debug("backoffice.sales_sync.invalid_created_at order_id={}", order.path("id").asLong());
        }
        String currency = order.path("currency").asText(null);
        if (currency != null && currency.length() == 3) currency = currency.toUpperCase(java.util.Locale.ROOT);
        else currency = null;
        return new StoreOrderSales(storeId, order.path("id").asLong(), total, personalized, createdAt,
                missingPrice ? null : personalizedProductValue.setScale(2, RoundingMode.HALF_UP), currency);
    }

    private static boolean hasReadOrdersScope(String scopes) {
        if (scopes == null) return false;
        for (String scope : scopes.split(",")) {
            if ("read_orders".equals(scope.strip())) return true;
        }
        return false;
    }

    public record SalesSummary(long personalizedOrders, long personalizedItems, long syncedStores,
                               long personalizedValueBackfilledStores, long activeStores) {
        public boolean complete() {
            return syncedStores == activeStores;
        }
        public String ordersDisplay() { return display(personalizedOrders); }
        public String itemsDisplay() { return display(personalizedItems); }
        private String display(long value) {
            return complete() ? Long.toString(value) : value > 0 ? value + " (parcial)" : "—";
        }
    }

    public record SalesRow(Long storeId, String storeName, String currency, Long orderId,
                           Instant createdAt, long personalizedItems, BigDecimal personalizedProductValue) {
    }
}
