package br.com.nuvemcustomfields.controller;

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

    private final StoreRepository storeRepository;
    private final PersonalizationRuleRepository ruleRepository;
    private final PlanLimitService planLimitService;
    private final IntegrationLogService integrationLogService;

    public PublicPersonalizationController(
            StoreRepository storeRepository,
            PersonalizationRuleRepository ruleRepository,
            PlanLimitService planLimitService,
            IntegrationLogService integrationLogService
    ) {
        this.storeRepository = storeRepository;
        this.ruleRepository = ruleRepository;
        this.planLimitService = planLimitService;
        this.integrationLogService = integrationLogService;
    }

    @GetMapping("/public/stores/{storeId}/personalization")
    public PersonalizationResponse getFields(
            @PathVariable Long storeId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String path
    ) {
        LOGGER.info("public.personalization.open store_id={} product_id={} path={}", storeId, productId, path);
        if (productId == null) {
            LOGGER.warn("public.personalization.disabled store_id={} reason=missing_product_id path={}", storeId, path);
            return PersonalizationResponse.disabled();
        }

        Store store = storeRepository.findActiveByStoreId(storeId).orElse(null);
        if (store == null) {
            LOGGER.warn("public.personalization.disabled store_id={} product_id={} reason=store_not_active", storeId, productId);
            return PersonalizationResponse.disabled();
        }

        PersonalizationRule rule = ruleRepository.findWithFieldsByStoreIdAndProductId(storeId, productId)
                .filter(PersonalizationRule::isEnabled)
                .orElse(null);
        if (rule == null) {
            LOGGER.info("public.personalization.disabled store_id={} product_id={} reason=rule_not_found_or_disabled", storeId, productId);
            return PersonalizationResponse.disabled();
        }

        var fields = planLimitService.storefrontFields(store, rule.getFields()).stream()
                .map(FieldResponse::from)
                .toList();
        LOGGER.info(
                "public.personalization.enabled store_id={} product_id={} rule_id={} fields_count={} country={} locale={}",
                storeId,
                productId,
                rule.getId(),
                fields.size(),
                store.getStoreCountryCode(),
                StoreLocale.tagFor(store.getStoreCountryCode())
        );
        return new PersonalizationResponse(
                true,
                fields,
                PersonalizationStyleResponse.from(store),
                StoreLocale.tagFor(store.getStoreCountryCode())
        );
    }

    @GetMapping("/public/stores/{storeId}/style")
    public PersonalizationStyleResponse getStyle(@PathVariable Long storeId) {
        Store store = storeRepository.findActiveByStoreId(storeId).orElse(null);
        if (store == null) {
            LOGGER.warn("public.style.disabled store_id={} reason=store_not_active", storeId);
            return PersonalizationStyleResponse.empty();
        }
        LOGGER.info(
                "public.style.enabled store_id={} country={} locale={}",
                storeId,
                store.getStoreCountryCode(),
                StoreLocale.tagFor(store.getStoreCountryCode())
        );
        return PersonalizationStyleResponse.from(store);
    }

    @GetMapping("/public/script-events")
    public void scriptEvent(
            @RequestParam(required = false) String event,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String scriptSrc
    ) {
        LOGGER.info(
                "public.script.event event={} store_id={} product_id={} reason={} path={} script_src={}",
                event,
                storeId,
                productId,
                reason,
                path,
                scriptSrc
        );
        persistStorefrontSdkEvent(event, storeId, productId, reason, path);
    }

    /**
     * Espelha o beacon do script NubeSDK em integration_logs para aparecer no backoffice.
     * Restrito ao evento storefront_sdk de loja ativa: o endpoint e publico e sem auth,
     * entao gravar qualquer coisa aqui seria vetor de flood.
     */
    private void persistStorefrontSdkEvent(String event, Long storeId, Long productId, String reason, String path) {
        if (!"storefront_sdk".equals(event) || storeId == null) {
            return;
        }
        if (storeRepository.findActiveByStoreId(storeId).isEmpty()) {
            LOGGER.warn("public.script.event.not_persisted store_id={} reason=store_not_active", storeId);
            return;
        }
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
