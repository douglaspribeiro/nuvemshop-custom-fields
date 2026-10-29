package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.StorefrontCache;
import br.com.nuvemcustomfields.config.StorefrontTrafficMetricsFilter;
import br.com.nuvemcustomfields.dto.FieldResponse;
import br.com.nuvemcustomfields.dto.PersonalizationResponse;
import br.com.nuvemcustomfields.i18n.StoreLocale;
import br.com.nuvemcustomfields.dto.PersonalizationStyleResponse;
import br.com.nuvemcustomfields.entity.PersonalizationRule;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.IntegrationLogService;
import br.com.nuvemcustomfields.service.PlanLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class PublicPersonalizationController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicPersonalizationController.class);

    private final com.github.benmanes.caffeine.cache.Cache<Long, Boolean> eventSamples =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .expireAfterWrite(java.time.Duration.ofMinutes(1)).maximumSize(10000).build();
    private final java.util.concurrent.atomic.LongAdder sampledEvents = new java.util.concurrent.atomic.LongAdder();
    private final java.util.concurrent.atomic.LongAdder suppressedEvents = new java.util.concurrent.atomic.LongAdder();
    private final StorefrontCache cache;
    private final StoreRepository storeRepository;
    private final PersonalizationRuleRepository ruleRepository;
    private final PlanLimitService planLimitService;
    private final IntegrationLogService integrationLogService;

    public PublicPersonalizationController(
            StoreRepository storeRepository,
            PersonalizationRuleRepository ruleRepository,
            PlanLimitService planLimitService,
            IntegrationLogService integrationLogService,
            StorefrontCache cache
    ) {
        this.cache = cache;
        this.storeRepository = storeRepository;
        this.ruleRepository = ruleRepository;
        this.planLimitService = planLimitService;
        this.integrationLogService = integrationLogService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void registerEventMetrics(io.micrometer.core.instrument.MeterRegistry registry) {
        io.micrometer.core.instrument.FunctionCounter.builder("ncf.storefront.beacon.sampled", sampledEvents,
                java.util.concurrent.atomic.LongAdder::doubleValue).register(registry);
        io.micrometer.core.instrument.FunctionCounter.builder("ncf.storefront.beacon.suppressed", suppressedEvents,
                java.util.concurrent.atomic.LongAdder::doubleValue).register(registry);
    }

    @GetMapping("/public/stores/{storeId}/personalization")
    public PersonalizationResponse getFields(
            @PathVariable Long storeId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String path,
            HttpServletRequest request
    ) {
        StoreState state = storeState(storeId);
        if (state.active()) request.setAttribute(StorefrontTrafficMetricsFilter.ACTIVE_STORE_ID_ATTRIBUTE, storeId);
        if (!state.hasFields()) return state.disabled();
        CachedPersonalization cached = cache.get("personalization", storeId, productId,
                () -> loadPersonalization(storeId, productId, path));
        if (cached.active()) request.setAttribute(StorefrontTrafficMetricsFilter.ACTIVE_STORE_ID_ATTRIBUTE, storeId);
        if (cached.response().enabled() && !cached.response().fields().isEmpty())
            request.setAttribute(StorefrontTrafficMetricsFilter.PERSONALIZED_PRODUCT_ID_ATTRIBUTE, productId);
        return cached.response();
    }

    private record StoreState(boolean active, boolean hasFields, PersonalizationResponse disabled) {
        StoreState(boolean active, boolean hasFields) { this(active, hasFields, PersonalizationResponse.disabled()); }
    }

    private StoreState storeState(Long storeId) {
        return cache.get("store-state", storeId, null, () -> {
            Store store = storeRepository.findActiveByStoreId(storeId).orElse(null);
            if (store == null) return new StoreState(false, false);
            boolean visible = ruleRepository.findByStoreIdAndEnabledTrue(storeId).stream()
                    .anyMatch(rule -> !planLimitService.storefrontFields(store, rule.getFields()).isEmpty());
            return new StoreState(true, visible);
        });
    }

    private record CachedPersonalization(boolean active, PersonalizationResponse response) {}

    private CachedPersonalization loadPersonalization(Long storeId, Long productId, String path) {
        LOGGER.debug("public.personalization.open store_id={} product_id={} path={}", storeId, productId, path);
        Store store = storeRepository.findActiveByStoreId(storeId).orElse(null);
        if (productId == null) {
            LOGGER.warn("public.personalization.disabled store_id={} reason=missing_product_id path={}", storeId, path);
            return new CachedPersonalization(store != null, PersonalizationResponse.disabled());
        }

        if (store == null) {
            LOGGER.warn("public.personalization.disabled store_id={} product_id={} reason=store_not_active", storeId, productId);
            return new CachedPersonalization(store != null, PersonalizationResponse.disabled());
        }

        PersonalizationRule rule = ruleRepository.findWithFieldsByStoreIdAndProductId(storeId, productId)
                .filter(PersonalizationRule::isEnabled)
                .orElse(null);
        if (rule == null) {
            LOGGER.debug("public.personalization.disabled store_id={} product_id={} reason=rule_not_found_or_disabled", storeId, productId);
            return new CachedPersonalization(store != null, PersonalizationResponse.disabled());
        }

        int imageOptionLimit = planLimitService.imageOptionLimit(store.getEffectivePlan());
        var fields = planLimitService.storefrontFields(store, rule.getFields()).stream()
                .map(field -> FieldResponse.from(field, imageOptionLimit))
                .toList();
        LOGGER.debug(
                "public.personalization.enabled store_id={} product_id={} rule_id={} fields_count={} country={} locale={}",
                storeId,
                productId,
                rule.getId(),
                fields.size(),
                store.getStoreCountryCode(),
                StoreLocale.tagFor(store.getStoreCountryCode())
        );
        if (fields.isEmpty()) return new CachedPersonalization(true, PersonalizationResponse.disabled());
        return new CachedPersonalization(true, new PersonalizationResponse(
                true,
                fields,
                PersonalizationStyleResponse.from(store),
                StoreLocale.tagFor(store.getStoreCountryCode()),
                rule.isVariantRestricted(),
                rule.getVariantIds()
        ));
    }

    @GetMapping("/public/stores/{storeId}/style")
    public PersonalizationStyleResponse getStyle(@PathVariable Long storeId, HttpServletRequest request) {
        CachedStyle cached = cache.get("style", storeId, null, () -> {
            Store store = storeRepository.findActiveByStoreId(storeId).orElse(null);
            return new CachedStyle(store != null,
                    store == null ? PersonalizationStyleResponse.empty() : PersonalizationStyleResponse.from(store));
        });
        if (cached.active()) request.setAttribute(StorefrontTrafficMetricsFilter.ACTIVE_STORE_ID_ATTRIBUTE, storeId);
        return cached.response();
    }

    private record CachedStyle(boolean active, PersonalizationStyleResponse response) {}

    @GetMapping("/public/script-events")
    public void scriptEvent(
            @RequestParam(required = false) String event,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String scriptSrc,
            HttpServletRequest request
    ) {
        LOGGER.debug(
                "public.script.event event={} store_id={} product_id={} reason={} path={} script_src={}",
                event,
                storeId,
                productId,
                reason,
                path,
                scriptSrc
        );
        boolean activeStore = storeId != null && cache.get("active", storeId, null, () -> storeRepository.findActiveByStoreId(storeId).isPresent());
        if (activeStore) {
            request.setAttribute(StorefrontTrafficMetricsFilter.ACTIVE_STORE_ID_ATTRIBUTE, storeId);
        }
        persistStorefrontSdkEvent(event, storeId, productId, reason, path, activeStore);
    }

    /**
     * Espelha o beacon do script NubeSDK em integration_logs para aparecer no backoffice.
     * Restrito ao evento storefront_sdk de loja ativa: o endpoint e publico e sem auth,
     * entao gravar qualquer coisa aqui seria vetor de flood.
     */
    private void persistStorefrontSdkEvent(String event, Long storeId, Long productId, String reason, String path,
                                          boolean activeStore) {
        if (!"storefront_sdk".equals(event) || storeId == null) {
            return;
        }
        if (!activeStore) {
            LOGGER.warn("public.script.event.not_persisted store_id={} reason=store_not_active", storeId);
            return;
        }
        // A public beacon must not write once for every page view. One sample per store/minute,
        // independent of arbitrary product IDs or reasons supplied by the caller.
        if (eventSamples.asMap().putIfAbsent(storeId, Boolean.TRUE) != null) {
            suppressedEvents.increment();
            return;
        }
        sampledEvents.increment();
        String message = "produto=" + (productId == null ? "-" : productId)
                + " eventos=" + sanitizeDetail(path);
        integrationLogService.info(storeId, "storefront.sdk." + sanitizeReason(reason), message);
    }

    /** Entrada publica: limita tamanho e charset antes de persistir. */
    private String sanitizeReason(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String safe = value.strip().replaceAll("[^A-Za-z0-9_.-]", "_");
        return safe.length() > 60 ? safe.substring(0, 60) : safe;
    }

    private String sanitizeDetail(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String safe = value.strip().replaceAll("[^A-Za-z0-9_.:|-]", "_");
        return safe.length() > 400 ? safe.substring(0, 400) : safe;
    }

}
