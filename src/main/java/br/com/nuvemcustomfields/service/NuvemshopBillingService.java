package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.StoreProfile;
import br.com.nuvemcustomfields.entity.PlanEvent;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopBillingProperties;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.repository.PlanEventRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

@Service
public class NuvemshopBillingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NuvemshopBillingService.class);

    private final NuvemshopBillingProperties billingProperties;
    private final NuvemshopProperties nuvemshopProperties;
    private final StoreRepository storeRepository;
    private final PlanEventRepository planEventRepository;
    private final NuvemshopApiClient apiClient;
    private final RestClient restClient;

    public NuvemshopBillingService(
            NuvemshopBillingProperties billingProperties,
            NuvemshopProperties nuvemshopProperties,
            StoreRepository storeRepository,
            PlanEventRepository planEventRepository,
            NuvemshopApiClient apiClient,
            RestClient.Builder builder
    ) {
        this.billingProperties = billingProperties;
        this.nuvemshopProperties = nuvemshopProperties;
        this.storeRepository = storeRepository;
        this.planEventRepository = planEventRepository;
        this.apiClient = apiClient;
        this.restClient = builder.defaultHeader("User-Agent", nuvemshopProperties.userAgent()).build();
    }

    public boolean isEnabled() {
        return billingProperties.enabled();
    }

    public BigDecimal amountFor(PlanType plan) {
        return switch (plan) {
            case PREMIUM -> priceForCountry("BR").premiumAmount();
            case PREMIUM_PLUS -> priceForCountry("BR").premiumPlusAmount();
            case FREE, FREE_GRATIS -> BigDecimal.ZERO;
        };
    }

    public String currency() {
        return priceForCountry("BR").currency();
    }

    public BigDecimal amountFor(Store store, PlanType plan) {
        refreshStoreMarketIfMissing(store);
        return switch (plan) {
            case PREMIUM -> billingPrice(store).premiumAmount();
            case PREMIUM_PLUS -> billingPrice(store).premiumPlusAmount();
            case FREE, FREE_GRATIS -> BigDecimal.ZERO;
        };
    }

    public String currencyFor(Store store) {
        refreshStoreMarketIfMissing(store);
        return billingPrice(store).currency();
    }

    public Store subscribe(Store store, PlanType targetPlan) {
        LOGGER.info(
                "nuvemshop.billing.subscribe.start store_id={} target_plan={} enabled={} concept_code_present={} api_base_url={}",
                store.getStoreId(),
                targetPlan,
                billingProperties.enabled(),
                billingProperties.conceptCode() != null && !billingProperties.conceptCode().isBlank(),
                billingProperties.apiBaseUrl()
        );
        validateTargetPlan(targetPlan);
        Store managedStore = storeRepository.findActiveByStoreId(store.getStoreId())
                .orElseThrow(() -> rejected(store.getStoreId(), targetPlan, "active_store_not_found", "Loja ativa nao encontrada."));
        if (!billingProperties.enabled()) {
            throw rejected(store.getStoreId(), targetPlan, "billing_disabled", "Billing automatico ainda nao esta ativo.");
        }
        if (managedStore.isCourtesyPremium()) {
            throw rejected(store.getStoreId(), targetPlan, "courtesy_premium", "Premium Cortesia nao pode gerar cobranca automatica.");
        }
        if (billingProperties.conceptCode() == null || billingProperties.conceptCode().isBlank()) {
            throw rejected(
                    store.getStoreId(),
                    targetPlan,
                    "concept_code_missing",
                    "NUVEMSHOP_BILLING_CONCEPT_CODE nao configurado."
            );
        }

        PlanType previousPlan = managedStore.getPlan();
        refreshStoreMarketIfMissing(managedStore);
        PlanDefinition plan = planDefinition(targetPlan, managedStore);
        try {
            ensureRemotePlan(plan);
            JsonNode subscription = updateSubscription(managedStore, plan);
            applySubscription(managedStore, targetPlan, plan, subscription, false, null);
            Store saved = storeRepository.save(managedStore);
            savePlanEvent(saved.getStoreId(), previousPlan, targetPlan, "BILLING");
            LOGGER.info("nuvemshop.billing.subscribe.done store_id={} plan={}", saved.getStoreId(), targetPlan);
            return saved;
        } catch (RuntimeException ex) {
            managedStore.setBillingLastError(truncate(errorMessage(ex)));
            managedStore.setBillingLastSyncedAt(Instant.now());
            storeRepository.save(managedStore);
            LOGGER.error("nuvemshop.billing.subscribe.error store_id={} plan={} message={}", managedStore.getStoreId(), targetPlan, ex.getMessage(), ex);
            throw ex;
        }
    }

    public void syncSubscription(Store store) {
        Store managedStore = storeRepository.findByStoreId(store.getStoreId())
                .orElseThrow(() -> new IllegalArgumentException("Loja nao encontrada."));
        if (!billingProperties.enabled() || billingProperties.conceptCode() == null || billingProperties.conceptCode().isBlank()) {
            LOGGER.info("nuvemshop.billing.sync.skip store_id={} reason=disabled_or_missing_concept", managedStore.getStoreId());
            return;
        }
        try {
            refreshStoreMarketIfMissing(managedStore);
            JsonNode subscription = getSubscription(managedStore);
            PlanType remotePlan = planFromSubscription(subscription, managedStore.getPlan());
            if (!remotePlan.isBillable()) {
                LOGGER.info("nuvemshop.billing.sync.skip store_id={} reason=non_billable_plan plan={}", managedStore.getStoreId(), remotePlan);
                managedStore.setBillingLastError(null);
                managedStore.setBillingLastSyncedAt(Instant.now());
                storeRepository.save(managedStore);
                return;
            }
            PlanDefinition plan = planDefinition(remotePlan, managedStore);
            applySubscription(managedStore, remotePlan, plan, subscription, managedStore.isBillingSuspended(), null);
            storeRepository.save(managedStore);
            LOGGER.info("nuvemshop.billing.sync.done store_id={} plan={}", managedStore.getStoreId(), remotePlan);
        } catch (RuntimeException ex) {
            managedStore.setBillingLastError(truncate(errorMessage(ex)));
            managedStore.setBillingLastSyncedAt(Instant.now());
            storeRepository.save(managedStore);
            LOGGER.error("nuvemshop.billing.sync.error store_id={} message={}", managedStore.getStoreId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    @Transactional
    public void markSuspended(Long storeId) {
        storeRepository.findByStoreId(storeId).ifPresent(store -> {
            store.setBillingSuspended(true);
            store.setBillingLastError(null);
            store.setBillingLastSyncedAt(Instant.now());
            storeRepository.save(store);
        });
    }

    @Transactional
    public void markResumed(Long storeId) {
        storeRepository.findByStoreId(storeId).ifPresent(store -> {
            store.setBillingSuspended(false);
            store.setBillingLastError(null);
            store.setBillingLastSyncedAt(Instant.now());
            storeRepository.save(store);
        });
    }

    private void validateTargetPlan(PlanType targetPlan) {
        if (!targetPlan.isBillable()) {
            throw new IllegalArgumentException("Selecione um plano pago valido.");
        }
    }

    private void ensureRemotePlan(PlanDefinition plan) {
        JsonNode existingPlans = restClient.get()
                .uri(billingProperties.apiBaseUrl() + "/apps/{appId}/plans", nuvemshopProperties.clientId())
                .header("Authorization", "Bearer " + nuvemshopProperties.clientSecret())
                .retrieve()
                .body(JsonNode.class);
        if (hasRemotePlan(existingPlans, plan.externalId())) {
            LOGGER.info("nuvemshop.billing.plan.exists external_id={}", plan.externalId());
            return;
        }

        Map<String, Object> payload = Map.of(
                "code", plan.currency(),
                "external_reference", plan.externalId(),
                "description", plan.description()
        );
        LOGGER.info(
                "nuvemshop.billing.plan.create.start app_id={} external_id={} currency={}",
                nuvemshopProperties.clientId(),
                plan.externalId(),
                plan.currency()
        );
        try {
            restClient.post()
                    .uri(billingProperties.apiBaseUrl() + "/apps/{appId}/plans", nuvemshopProperties.clientId())
                    .header("Authorization", "Bearer " + nuvemshopProperties.clientSecret())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            LOGGER.info("nuvemshop.billing.plan.create.done external_id={}", plan.externalId());
        } catch (RestClientResponseException ex) {
            if (!isDuplicatePlan(ex)) {
                throw ex;
            }
            LOGGER.info("nuvemshop.billing.plan.exists external_id={}", plan.externalId());
            patchRemotePlan(plan, payload);
        }
    }

    private void patchRemotePlan(PlanDefinition plan, Map<String, Object> payload) {
        LOGGER.info(
                "nuvemshop.billing.plan.patch.start app_id={} external_id={}",
                nuvemshopProperties.clientId(),
                plan.externalId()
        );
        restClient.patch()
                .uri(billingProperties.apiBaseUrl() + "/apps/{appId}/plans/{planId}", nuvemshopProperties.clientId(), plan.externalId())
                .header("Authorization", "Bearer " + nuvemshopProperties.clientSecret())
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        LOGGER.info("nuvemshop.billing.plan.patch.done external_id={}", plan.externalId());
    }

    private JsonNode updateSubscription(Store store, PlanDefinition plan) {
        LOGGER.info(
                "nuvemshop.billing.subscription.update.start store_id={} service_id={} plan_external_id={} amount={} currency={}",
                store.getStoreId(),
                nuvemshopProperties.clientId(),
                plan.externalId(),
                plan.amount(),
                plan.currency()
        );
        JsonNode response = restClient.patch()
                .uri(
                        billingProperties.apiBaseUrl() + "/{storeId}/concepts/{conceptCode}/services/{serviceId}/subscriptions",
                        store.getStoreId(),
                        billingProperties.conceptCode(),
                        nuvemshopProperties.clientId()
                )
                .header("Authorization", "Bearer " + store.getAccessToken())
                .body(Map.of(
                        "amount_currency", plan.currency(),
                        "amount_value", plan.amount(),
                        "plan_external_id", plan.externalId()
                ))
                .retrieve()
                .body(JsonNode.class);
        LOGGER.info(
                "nuvemshop.billing.subscription.update.done store_id={} response_present={}",
                store.getStoreId(),
                response != null
        );
        return response;
    }

    private JsonNode getSubscription(Store store) {
        return restClient.get()
                .uri(
                        billingProperties.apiBaseUrl() + "/{storeId}/concepts/{conceptCode}/services/{serviceId}/subscriptions",
                        store.getStoreId(),
                        billingProperties.conceptCode(),
                        nuvemshopProperties.clientId()
                )
                .header("Authorization", "Bearer " + store.getAccessToken())
                .retrieve()
                .body(JsonNode.class);
    }

    private void applySubscription(
            Store store,
            PlanType planType,
            PlanDefinition plan,
            JsonNode subscription,
            boolean suspended,
            String error
    ) {
        store.setPlan(planType);
        store.setSubscriptionId(subscriptionReference(subscription, store));
        store.setBillingPlanExternalId(plan.externalId());
        store.setBillingAmountCurrency(text(subscription, "amount_currency", plan.currency()));
        store.setBillingAmountValue(decimal(subscription, "amount_value", plan.amount()));
        store.setBillingNextExecution(date(subscription, "next_execution"));
        store.setBillingLastExecution(date(subscription, "last_execution"));
        store.setBillingSuspended(suspended);
        store.setBillingLastSyncedAt(Instant.now());
        store.setBillingLastError(error);
    }

    private void savePlanEvent(Long storeId, PlanType previousPlan, PlanType targetPlan, String source) {
        PlanEvent event = new PlanEvent();
        event.setStoreId(storeId);
        event.setFromPlan(previousPlan);
        event.setToPlan(targetPlan);
        event.setSource(source);
        planEventRepository.save(event);
    }

    private PlanType planFromSubscription(JsonNode subscription, PlanType fallbackPlan) {
        String externalId = subscription == null ? null : subscription.path("plan_external_id").asText(null);
        if (externalId == null || externalId.isBlank()) {
            externalId = subscription == null ? null : subscription.path("plan").path("external_reference").asText(null);
        }
        if (externalId == null || externalId.isBlank()) {
            externalId = subscription == null ? null : subscription.path("plan").path("code").asText(null);
        }
        if (externalId == null || externalId.isBlank()) {
            LOGGER.warn("nuvemshop.billing.sync.plan_missing fallback_plan={}", fallbackPlan);
            return fallbackPlan;
        }
        if (billingProperties.premiumExternalId().equals(externalId)) {
            return PlanType.PREMIUM;
        }
        if (billingProperties.premiumPlusExternalId().equals(externalId)) {
            return PlanType.PREMIUM_PLUS;
        }
        LOGGER.warn("nuvemshop.billing.sync.plan_unknown external_id={} fallback_plan={}", externalId, fallbackPlan);
        return fallbackPlan;
    }

    private PlanDefinition planDefinition(PlanType planType, Store store) {
        BillingPrice price = billingPrice(store);
        return switch (planType) {
            case PREMIUM -> new PlanDefinition(
                    billingProperties.premiumExternalId(),
                    price.currency(),
                    price.premiumAmount(),
                    "Campos Personalizados Premium"
            );
            case PREMIUM_PLUS -> new PlanDefinition(
                    billingProperties.premiumPlusExternalId(),
                    price.currency(),
                    price.premiumPlusAmount(),
                    "Campos Personalizados Premium Plus"
            );
            case FREE, FREE_GRATIS -> throw new IllegalArgumentException(planType.getDisplayName() + " nao usa billing automatico.");
        };
    }

    private void refreshStoreMarketIfMissing(Store store) {
        if (present(store.getStoreCountryCode()) && present(store.getStoreCurrency())) {
            return;
        }
        try {
            StoreProfile profile = apiClient.getStoreProfile(store);
            boolean changed = false;
            if (present(profile.countryCode())) {
                store.setStoreCountryCode(profile.countryCode());
                changed = true;
            }
            if (present(profile.currency())) {
                store.setStoreCurrency(profile.currency());
                changed = true;
            }
            if (present(profile.name()) && !present(store.getStoreName())) {
                store.setStoreName(profile.name());
                changed = true;
            }
            if (changed) {
                storeRepository.save(store);
            }
        } catch (RuntimeException ex) {
            LOGGER.warn("nuvemshop.billing.store_market.refresh_failed store_id={} message={}", store.getStoreId(), ex.getMessage());
        }
    }

    private BillingPrice billingPrice(Store store) {
        String countryCode = normalize(store.getStoreCountryCode());
        String currency = normalize(store.getStoreCurrency());
        BillingPrice price = priceForCountryOrNull(countryCode);
        if (price == null && present(currency)) {
            price = priceForCurrency(currency);
        }
        if (price == null && !present(countryCode) && !present(currency)) {
            throw rejected(
                    store.getStoreId(),
                    store.getPlan(),
                    "billing_market_missing",
                    "Nao foi possivel identificar o pais e a moeda da loja. Reinstale o app pela Nuvemshop e tente novamente."
            );
        }
        if (price == null) {
            throw rejected(
                    store.getStoreId(),
                    store.getPlan(),
                    "billing_market_unsupported",
                    "Pais/moeda da loja sem preco configurado para billing."
            );
        }
        if (present(currency) && !price.currency().equals(currency)) {
            throw rejected(
                    store.getStoreId(),
                    store.getPlan(),
                    "billing_currency_mismatch",
                    "Moeda da loja nao corresponde a tabela de preco configurada."
            );
        }
        return price;
    }

    private BillingPrice priceForCountry(String countryCode) {
        BillingPrice price = priceForCountryOrNull(countryCode);
        if (price == null) {
            throw new IllegalStateException("Preco de billing nao configurado para " + countryCode + ".");
        }
        return price;
    }

    private BillingPrice priceForCountryOrNull(String countryCode) {
        if (!present(countryCode)) {
            return null;
        }
        NuvemshopBillingProperties.CountryPrice configured = configuredPrices().get(countryCode);
        if (configured == null) {
            return null;
        }
        return new BillingPrice(normalize(configured.currency()), configured.premiumAmount(), configured.premiumPlusAmount());
    }

    private BillingPrice priceForCurrency(String currency) {
        return configuredPrices().values().stream()
                .filter(price -> currency.equals(normalize(price.currency())))
                .findFirst()
                .map(price -> new BillingPrice(normalize(price.currency()), price.premiumAmount(), price.premiumPlusAmount()))
                .orElse(null);
    }

    private Map<String, NuvemshopBillingProperties.CountryPrice> configuredPrices() {
        Map<String, NuvemshopBillingProperties.CountryPrice> prices = billingProperties.prices();
        if (prices == null || prices.isEmpty()) {
            return Map.of(
                    "BR", new NuvemshopBillingProperties.CountryPrice(billingProperties.currency(), billingProperties.premiumAmount(), billingProperties.premiumPlusAmount())
            );
        }
        return prices.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        entry -> normalize(entry.getKey()),
                        Map.Entry::getValue
                ));
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    private boolean isDuplicatePlan(RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String body = ex.getResponseBodyAsString();
        return status.is4xxClientError()
                && body != null
                && body.toLowerCase().contains("duplicated");
    }

    private boolean hasRemotePlan(JsonNode plans, String externalId) {
        if (plans == null || !plans.isArray()) {
            return false;
        }
        for (JsonNode plan : plans) {
            if (externalId.equals(plan.path("external_reference").asText())
                    || externalId.equals(plan.path("code").asText())) {
                return true;
            }
        }
        return false;
    }

    private IllegalStateException rejected(Long storeId, PlanType targetPlan, String reason, String message) {
        LOGGER.warn(
                "nuvemshop.billing.subscribe.rejected store_id={} target_plan={} reason={}",
                storeId,
                targetPlan,
                reason
        );
        return new IllegalStateException(message);
    }

    private String subscriptionReference(JsonNode subscription, Store store) {
        String externalReference = text(subscription, "external_reference", null);
        if (externalReference != null && !externalReference.isBlank()) {
            return externalReference;
        }
        return billingProperties.conceptCode() + ":" + nuvemshopProperties.clientId() + ":" + store.getStoreId();
    }

    private String text(JsonNode node, String field, String fallback) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return fallback;
        }
        String value = node.path(field).asText();
        return value == null || value.isBlank() ? fallback : value;
    }

    private BigDecimal decimal(JsonNode node, String field, BigDecimal fallback) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return fallback;
        }
        return node.path(field).decimalValue();
    }

    private LocalDate date(JsonNode node, String field) {
        String value = text(node, field, null);
        if (value == null) {
            return null;
        }
        return LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
    }

    private String errorMessage(RuntimeException ex) {
        if (ex instanceof RestClientResponseException responseException) {
            String body = responseException.getResponseBodyAsString();
            return responseException.getStatusCode() + (body == null || body.isBlank() ? "" : " " + body);
        }
        return ex.getMessage();
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private record PlanDefinition(String externalId, String currency, BigDecimal amount, String description) {
    }

    private record BillingPrice(String currency, BigDecimal premiumAmount, BigDecimal premiumPlusAmount) {
    }
}
