package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.dto.FieldResponse;
import br.com.nuvemcustomfields.dto.PersonalizationResponse;
import br.com.nuvemcustomfields.entity.PersonalizationRule;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.PlanLimitService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    public PublicPersonalizationController(
            StoreRepository storeRepository,
            PersonalizationRuleRepository ruleRepository,
            PlanLimitService planLimitService,
            ObjectMapper objectMapper
    ) {
        this.storeRepository = storeRepository;
        this.ruleRepository = ruleRepository;
        this.planLimitService = planLimitService;
        this.objectMapper = objectMapper;
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
                "public.personalization.enabled store_id={} product_id={} rule_id={} fields_count={}",
                storeId,
                productId,
                rule.getId(),
                fields.size()
        );
        return new PersonalizationResponse(
                true,
                fields
        );
    }

    @GetMapping(value = "/public/stores/{storeId}/personalization.js", produces = "application/javascript")
    public String getFieldsJsonp(
            @PathVariable Long storeId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String path,
            @RequestParam(defaultValue = "ncfPersonalizationCallback") String callback
    ) throws JsonProcessingException {
        String callbackName = safeCallback(callback);
        PersonalizationResponse response = getFields(storeId, productId, path);
        return callbackName + "(" + objectMapper.writeValueAsString(response) + ");";
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
    }

    private String safeCallback(String callback) {
        if (callback != null && callback.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")) {
            return callback;
        }
        return "ncfPersonalizationCallback";
    }
}
