package br.com.nuvemcustomfields.shopify;

import br.com.nuvemcustomfields.dto.FieldResponse;
import br.com.nuvemcustomfields.dto.PersonalizationResponse;
import br.com.nuvemcustomfields.dto.PersonalizationStyleResponse;
import br.com.nuvemcustomfields.entity.CommercePlatform;
import br.com.nuvemcustomfields.entity.PersonalizationRule;
import br.com.nuvemcustomfields.entity.ShopifyShop;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.ShopifyShopRepository;
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
public class ShopifyPublicController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShopifyPublicController.class);

    private final ShopifyShopRepository shopRepository;
    private final PersonalizationRuleRepository ruleRepository;
    private final PlanLimitService planLimitService;

    public ShopifyPublicController(
            ShopifyShopRepository shopRepository,
            PersonalizationRuleRepository ruleRepository,
            PlanLimitService planLimitService
    ) {
        this.shopRepository = shopRepository;
        this.ruleRepository = ruleRepository;
        this.planLimitService = planLimitService;
    }

    @GetMapping("/shopify/public/shops/{shopDomain}/personalization")
    public PersonalizationResponse getFields(
            @PathVariable String shopDomain,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String path
    ) {
        LOGGER.info("shopify.public.personalization.open shop={} product_id={} path={}", shopDomain, productId, path);
        if (productId == null) {
            return PersonalizationResponse.disabled();
        }
        ShopifyShop shop = shopRepository.findActiveByShopDomain(shopDomain).orElse(null);
        if (shop == null) {
            LOGGER.warn("shopify.public.personalization.disabled shop={} reason=shop_not_active", shopDomain);
            return PersonalizationResponse.disabled();
        }
        PersonalizationRule rule = ruleRepository.findWithFieldsByPlatformAndStoreIdAndProductId(
                        CommercePlatform.SHOPIFY,
                        shop.getId(),
                        productId
                )
                .filter(PersonalizationRule::isEnabled)
                .orElse(null);
        if (rule == null) {
            return PersonalizationResponse.disabled();
        }
        var fields = planLimitService.storefrontFields(shop.getPlan(), rule.getFields()).stream()
                .map(FieldResponse::from)
                .toList();
        return new PersonalizationResponse(true, fields, PersonalizationStyleResponse.empty());
    }

    @GetMapping("/shopify/public/script-events")
    public void scriptEvent(
            @RequestParam(required = false) String event,
            @RequestParam(required = false) String shop,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String path
    ) {
        LOGGER.info("shopify.public.script.event event={} shop={} product_id={} reason={} path={}", event, shop, productId, reason, path);
    }
}
