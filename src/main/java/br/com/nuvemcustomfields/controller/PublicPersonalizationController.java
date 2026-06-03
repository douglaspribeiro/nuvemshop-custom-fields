package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.dto.FieldResponse;
import br.com.nuvemcustomfields.dto.PersonalizationResponse;
import br.com.nuvemcustomfields.entity.PersonalizationRule;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.PlanLimitService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicPersonalizationController {

    private final StoreRepository storeRepository;
    private final PersonalizationRuleRepository ruleRepository;
    private final PlanLimitService planLimitService;

    public PublicPersonalizationController(
            StoreRepository storeRepository,
            PersonalizationRuleRepository ruleRepository,
            PlanLimitService planLimitService
    ) {
        this.storeRepository = storeRepository;
        this.ruleRepository = ruleRepository;
        this.planLimitService = planLimitService;
    }

    @GetMapping("/public/stores/{storeId}/personalization")
    public PersonalizationResponse getFields(
            @PathVariable Long storeId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String path
    ) {
        if (productId == null) {
            return PersonalizationResponse.disabled();
        }

        Store store = storeRepository.findActiveByStoreId(storeId).orElse(null);
        if (store == null) {
            return PersonalizationResponse.disabled();
        }

        PersonalizationRule rule = ruleRepository.findWithFieldsByStoreIdAndProductId(storeId, productId)
                .filter(PersonalizationRule::isEnabled)
                .orElse(null);
        if (rule == null) {
            return PersonalizationResponse.disabled();
        }

        return new PersonalizationResponse(
                true,
                planLimitService.storefrontFields(store, rule.getFields()).stream()
                        .map(FieldResponse::from)
                        .toList()
        );
    }
}
