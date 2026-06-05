package br.com.nuvemcustomfields.shopify;

import br.com.nuvemcustomfields.repository.ShopifyShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class ShopifyWebhookController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShopifyWebhookController.class);

    private final ShopifySecurityService securityService;
    private final ShopifyShopRepository shopRepository;

    public ShopifyWebhookController(ShopifySecurityService securityService, ShopifyShopRepository shopRepository) {
        this.securityService = securityService;
        this.shopRepository = shopRepository;
    }

    @PostMapping("/shopify/webhooks")
    @Transactional
    public ResponseEntity<Void> receive(
            @RequestBody String rawBody,
            @RequestHeader(name = "X-Shopify-Hmac-Sha256", required = false) String hmac,
            @RequestHeader(name = "X-Shopify-Topic", required = false) String topic,
            @RequestHeader(name = "X-Shopify-Shop-Domain", required = false) String shopDomain
    ) {
        if (!securityService.validWebhookHmac(rawBody, hmac)) {
            LOGGER.warn("shopify.webhook.invalid_signature topic={} shop={}", topic, shopDomain);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        LOGGER.info("shopify.webhook.receive topic={} shop={}", topic, shopDomain);
        if ("app/uninstalled".equals(topic) && shopDomain != null) {
            shopRepository.findByShopDomain(shopDomain).ifPresent(shop -> {
                shop.setUninstalledAt(Instant.now());
                shopRepository.save(shop);
            });
        }
        return ResponseEntity.ok().build();
    }
}
