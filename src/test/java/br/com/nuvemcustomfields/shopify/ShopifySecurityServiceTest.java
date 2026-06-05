package br.com.nuvemcustomfields.shopify;

import br.com.nuvemcustomfields.properties.ShopifyProperties;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ShopifySecurityServiceTest {

    private final ShopifySecurityService service = new ShopifySecurityService(new ShopifyProperties(
            "client",
            "secret",
            "read_products",
            "https://app.example.com",
            "2026-04"
    ));

    @Test
    void validatesShopDomain() {
        assertThat(service.isValidShopDomain("minha-loja.myshopify.com")).isTrue();
        assertThat(service.isValidShopDomain("https://minha-loja.myshopify.com")).isFalse();
        assertThat(service.isValidShopDomain("minha-loja.example.com")).isFalse();
    }

    @Test
    void validatesShopifyHmac() {
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("shop", new String[]{"minha-loja.myshopify.com"});
        params.put("timestamp", new String[]{"1710000000"});
        params.put("code", new String[]{"abc"});
        String message = "code=abc&shop=minha-loja.myshopify.com&timestamp=1710000000";
        params.put("hmac", new String[]{service.hmacHex(message)});

        assertThat(service.validHmac(params)).isTrue();

        params.put("hmac", new String[]{"invalid"});
        assertThat(service.validHmac(params)).isFalse();
    }

    @Test
    void validatesWebhookHmac() {
        String body = "{\"id\":123}";
        String hmac = "OeBtbEIAz+EnjWhU3WkE+GpE4e/Cu0Fl+JGceISoR9I=";

        assertThat(service.validWebhookHmac(body, hmac)).isTrue();
        assertThat(service.validWebhookHmac(body, "invalid")).isFalse();
    }
}
