package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
public class StoreConfigurationHistoryService {
    private final StoreRepository stores;
    private final PersonalizationRuleRepository rules;
    private final IntegrationLogRepository logs;
    private final StoreConfigurationSnapshotRepository snapshots;
    private final WinbackCampaignRepository campaigns;
    private final ObjectMapper json;

    public StoreConfigurationHistoryService(StoreRepository stores, PersonalizationRuleRepository rules,
            IntegrationLogRepository logs, StoreConfigurationSnapshotRepository snapshots,
            WinbackCampaignRepository campaigns, ObjectMapper json) {
        this.stores = stores; this.rules = rules; this.logs = logs;
        this.snapshots = snapshots; this.campaigns = campaigns; this.json = json;
    }

    /** Called before plan/scope reset, under the same store lock as uninstall and erasure. */
    @Transactional
    public void captureDeparture(Long storeId, Instant receivedAt) {
        stores.findByStoreIdForUpdate(storeId).ifPresent(store -> {
            // Never present today's configuration as an older departure's configuration.
            if (store.getUninstalledAt() != null) return;
            Instant at = receivedAt.truncatedTo(ChronoUnit.MICROS);
            try {
                snapshots.save(new StoreConfigurationSnapshot(storeId, at, json.writeValueAsString(configuration(store))));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Não foi possível preservar a configuração da loja.", exception);
            }
            store.setUninstalledAt(at);
            stores.save(store);
        });
    }

    @Transactional
    public void recordReason(Store store) {
        if (store.getUninstalledAt() == null) return;
        snapshots.findByStoreIdAndUninstalledAt(store.getStoreId(), store.getUninstalledAt()).ifPresent(snapshot -> {
            snapshot.recordReason(store.getDepartureReason(), store.getDepartureJustification());
            snapshots.save(snapshot);
        });
    }

    @Transactional(readOnly = true)
    public History inspect(Long storeId, Long snapshotId, Instant requestedDeparture) {
        Store store = stores.findByStoreId(storeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var history = snapshots.findByStoreIdOrderByUninstalledAtDesc(storeId);
        StoreConfigurationSnapshot selected;
        if (snapshotId != null) {
            selected = snapshots.findByIdAndStoreId(snapshotId, storeId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        } else {
            Instant target = requestedDeparture == null ? store.getUninstalledAt() : requestedDeparture;
            selected = target == null ? null : history.stream()
                    .filter(snapshot -> snapshot.getUninstalledAt().equals(target)).findFirst().orElse(null);
        }
        Instant departure = selected == null ? (requestedDeparture == null ? store.getUninstalledAt() : requestedDeparture) : selected.getUninstalledAt();
        var campaign = departure == null ? null : campaigns.findByStoreIdAndUninstalledAt(storeId, departure).orElse(null);
        boolean currentDeparture = departure != null && departure.equals(store.getUninstalledAt());
        String manualReason = selected == null ? (currentDeparture ? store.getDepartureReason() : null) : selected.getDepartureReason();
        String manualDetail = selected == null ? (currentDeparture ? store.getDepartureJustification() : null) : selected.getDepartureJustification();
        String merchantReason = campaign == null ? "Ainda não informado" : campaign.getReasonLabel();
        String merchantDetail = campaign == null ? null : campaign.getResponse();
        Configuration config;
        try {
            config = selected == null ? configuration(store) : json.readValue(selected.getConfigurationJson(), Configuration.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível ler o histórico da loja.", exception);
        }
        return new History(store, selected, history, config, departure, manualReason, manualDetail, merchantReason, merchantDetail);
    }

    private Configuration configuration(Store store) {
        var products = rules.findByStoreIdOrderByProductNameAsc(store.getStoreId()).stream().map(rule ->
                new Product(rule.getProductId(), rule.getProductName(), rule.isEnabled(), rule.getCreatedAt(),
                        rule.getFields().stream().sorted(Comparator.comparing(PersonalizationField::getSortOrder)
                                .thenComparing(PersonalizationField::getId)).map(field ->
                                new Field(field.getId(), field.getLabel(), field.getFieldType(), field.isRequired(),
                                        field.getMaxLength(), field.getPlaceholder(), field.getValidationPattern(),
                                        field.getOptionsText(), field.getImageOptionsJson(), field.getSortOrder())).toList())).toList();
        var recentLogs = logs.findTop20ByStoreIdOrderByCreatedAtDesc(store.getStoreId()).stream()
                .map(log -> new Log(log.getCreatedAt(), log.getLevel(), log.getEventType(), log.getMessage())).toList();
        return new Configuration(store.getEffectivePlan(), store.isBillingSuspended(), store.getScope(),
                store.getProductTextColor(), store.getCartTextColor(), store.getCheckoutTextColor(), products, recentLogs);
    }

    public record Configuration(PlanType plan, boolean billingSuspended, String scope, String productTextColor,
            String cartTextColor, String checkoutTextColor, List<Product> products, List<Log> logs) {
        public int fieldCount() { return products.stream().mapToInt(product -> product.fields().size()).sum(); }
    }
    public record Product(Long productId, String productName, boolean enabled, Instant createdAt, List<Field> fields) { }
    public record Field(Long id, String label, FieldType fieldType, boolean required, Integer maxLength,
            String placeholder, String validationPattern, String optionsText, String imageOptionsJson, Integer sortOrder) {
        public List<String> imageLabels() {
            return ImageOptions.parse(imageOptionsJson).stream().map(br.com.nuvemcustomfields.dto.ImageOption::label).toList();
        }
    }
    public record Log(Instant createdAt, String level, String eventType, String message) { }
    public record History(Store store, StoreConfigurationSnapshot selected, List<StoreConfigurationSnapshot> snapshots,
            Configuration configuration, Instant departure, String manualReason, String manualDetail,
            String merchantReason, String merchantDetail) { }
}
