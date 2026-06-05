package br.com.nuvemcustomfields.shopify;

import br.com.nuvemcustomfields.entity.ShopifyShop;
import br.com.nuvemcustomfields.properties.ShopifyProperties;
import br.com.nuvemcustomfields.repository.ShopifyShopRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@Service
public class ShopifyAuthService {

    private final ShopifyProperties properties;
    private final ShopifySecurityService securityService;
    private final ShopifyApiClient apiClient;
    private final ShopifyShopRepository shopRepository;

    public ShopifyAuthService(
            ShopifyProperties properties,
            ShopifySecurityService securityService,
            ShopifyApiClient apiClient,
            ShopifyShopRepository shopRepository
    ) {
        this.properties = properties;
        this.securityService = securityService;
        this.apiClient = apiClient;
        this.shopRepository = shopRepository;
    }

    public String authorizationUrl(String shop, String state) {
        if (!securityService.isValidShopDomain(shop)) {
            throw new IllegalArgumentException("Informe uma loja Shopify valida.");
        }
        return UriComponentsBuilder
                .fromUriString("https://" + shop + "/admin/oauth/authorize")
                .queryParam("client_id", properties.clientId())
                .queryParam("scope", properties.scopes())
                .queryParam("redirect_uri", properties.appBaseUrl() + "/shopify/oauth/callback")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    public String newState() {
        return UUID.randomUUID().toString();
    }

    @Transactional
    public ShopifyShop exchangeCodeAndUpsertShop(String shopDomain, String code) {
        JsonNode token = apiClient.exchangeCode(shopDomain, code);
        String accessToken = token == null ? null : token.path("access_token").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Token Shopify nao retornado.");
        }
        ShopifyShop shop = shopRepository.findByShopDomain(shopDomain).orElseGet(ShopifyShop::new);
        shop.setShopDomain(shopDomain);
        shop.setAccessToken(accessToken);
        shop.setScope(token.path("scope").asText(properties.scopes()));
        shop.setUninstalledAt(null);
        ShopifyShop saved = shopRepository.save(shop);
        enrichShopInfo(saved);
        return shopRepository.save(saved);
    }

    private void enrichShopInfo(ShopifyShop shop) {
        try {
            JsonNode response = apiClient.shopInfo(shop);
            JsonNode shopNode = response == null ? null : response.path("data").path("shop");
            if (shopNode == null || shopNode.isMissingNode()) {
                return;
            }
            shop.setShopName(shopNode.path("name").asText(null));
            shop.setShopifyShopId(numericId(shopNode.path("id").asText(null)));
        } catch (RuntimeException ignored) {
            // OAuth install should not fail if optional shop metadata cannot be loaded.
        }
    }

    private Long numericId(String gid) {
        if (gid == null || gid.isBlank()) {
            return null;
        }
        int index = gid.lastIndexOf('/');
        String value = index >= 0 ? gid.substring(index + 1) : gid;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
