package br.com.nuvemcustomfields.service;

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
import java.util.Map;

@Service
public class NuvemshopBillingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NuvemshopBillingService.class);

    private final NuvemshopBillingProperties billingProperties;
    private final NuvemshopProperties nuvemshopProperties;
    private final StoreRepository storeRepository;
    private final PlanEventRepository planEventRepository;
    private final PlanCatalogService planCatalogService;
    private final RestClient restClient;

    public NuvemshopBillingService(
            NuvemshopBillingProperties billingProperties,
            NuvemshopProperties nuvemshopProperties,
            StoreRepository storeRepository,
            PlanEventRepository planEventRepository,
            PlanCatalogService planCatalogService,
            RestClient.Builder builder
    ) {
        this.billingProperties = billingProperties;
        this.nuvemshopProperties = nuvemshopProperties;
        this.storeRepository = storeRepository;
        this.planEventRepository = planEventRepository;
        this.planCatalogService = planCatalogService;
        this.restClient = builder.defaultHeader("User-Agent", nuvemshopProperties.userAgent()).build();
    }

    public boolean isEnabled() {
        return billingProperties.enabled();
    }

    public BigDecimal amountFor(PlanType plan) {
        return planCatalogService.activePlan(plan).getAmount();
    }

    public String currency() {
        return planCatalogService.activePlan(PlanType.PREMIUM).getCurrency();
    }

    public Store subscribe(Store store, PlanType targetPlan) {
        validateTargetPlan(targetPlan);
        Store managedStore = storeRepository.findActiveByStoreId(store.getStoreId())
                .orElseThrow(() -> new IllegalArgumentException("Loja ativa nao encontrada."));
        if (!billingProperties.enabled()) {
            throw new IllegalStateException("Billing automatico ainda nao esta ativo.");
        }
        if (managedStore.isCourtesyPremium()) {
            throw new IllegalStateException("Premium Cortesia nao pode gerar cobranca automatica.");
        }
        if (billingProperties.conceptCode() == null || billingProperties.conceptCode().isBlank()) {
            throw new IllegalStateException("NUVEMSHOP_BILLING_CONCEPT_CODE nao configurado.");
        }

        PlanType previousPlan = managedStore.getPlan();
        PlanDefinition plan = planDefinition(targetPlan);
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
            JsonNode subscription = getSubscription(managedStore);
            PlanType remotePlan = planFromSubscription(subscription, managedStore.getPlan());
            PlanDefinition plan = planDefinition(remotePlan);
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
        if (targetPlan != PlanType.PREMIUM && targetPlan != PlanType.PREMIUM_PLUS) {
            throw new IllegalArgumentException("Selecione um plano pago valido.");
        }
    }

    private void ensureRemotePlan(PlanDefinition plan) {
        Map<String, Object> payload = Map.of(
                "code", plan.currency(),
                "external_reference", plan.externalId(),
                "description", plan.description()
        );
        try {
            restClient.post()
                    .uri(billingProperties.apiBaseUrl() + "/apps/{appId}/plans", nuvemshopProperties.clientId())
                    .header("Authentication", "bearer " + nuvemshopProperties.clientSecret())
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
        restClient.patch()
                .uri(billingProperties.apiBaseUrl() + "/apps/{appId}/plans/{planId}", nuvemshopProperties.clientId(), plan.externalId())
                .header("Authentication", "bearer " + nuvemshopProperties.clientSecret())
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        LOGGER.info("nuvemshop.billing.plan.patch.done external_id={}", plan.externalId());
    }

    private JsonNode updateSubscription(Store store, PlanDefinition plan) {
        return restClient.patch()
                .uri(
                        billingProperties.apiBaseUrl() + "/concepts/{conceptCode}/services/{serviceId}/subscriptions",
                        billingProperties.conceptCode(),
                        nuvemshopProperties.clientId()
                )
                .header("Authentication", "bearer " + store.getAccessToken())
                .body(Map.of(
                        "amount_currency", plan.currency(),
                        "amount_value", plan.amount(),
                        "plan_external_id", plan.externalId()
                ))
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode getSubscription(Store store) {
        return restClient.get()
                .uri(
                        billingProperties.apiBaseUrl() + "/concepts/{conceptCode}/services/{serviceId}/subscriptions",
                        billingProperties.conceptCode(),
                        nuvemshopProperties.clientId()
                )
                .header("Authentication", "bearer " + store.getAccessToken())
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
        var planType = planCatalogService.planTypeForBillingExternalId(externalId);
        if (planType.isPresent()) {
            return planType.get();
        }
        LOGGER.warn("nuvemshop.billing.sync.plan_unknown external_id={} fallback_plan={}", externalId, fallbackPlan);
        return fallbackPlan;
    }

    private PlanDefinition planDefinition(PlanType planType) {
        if (planType == PlanType.FREE) {
            throw new IllegalArgumentException("FREE nao usa billing automatico.");
        }
        var asset = planCatalogService.activePlan(planType);
        return new PlanDefinition(
                asset.getBillingExternalId(),
                asset.getCurrency(),
                asset.getAmount(),
                asset.getDescription() == null ? asset.getDisplayName() : asset.getDescription()
        );
    }

    private boolean isDuplicatePlan(RestClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String body = ex.getResponseBodyAsString();
        return status.is4xxClientError()
                && body != null
                && body.toLowerCase().contains("duplicated");
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
        return value == null ? null : LocalDate.parse(value);
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
}
