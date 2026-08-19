package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSecurityServiceTest {

    private final NuvemshopProperties properties = new NuvemshopProperties(
            "client",
            "secret",
            "http://localhost/oauth/callback",
            "https://example.com/{clientId}/authorize",
            "https://example.com/token",
            "https://api.example.com",
            "http://localhost:8080",
            "read_products",
            "tests",
            "",
            "",
            ""
    );

    @Test
    void validatesLinkedStoreHmacHeader() throws Exception {
        String body = "{\"store_id\":123,\"event\":\"app/uninstalled\",\"id\":456}";
        String signature = hmac(body, "secret");

        assertThat(new WebhookSecurityService(properties).isValid(body, signature)).isTrue();
        assertThat(new WebhookSecurityService(properties).isValid(body, "invalid")).isFalse();
    }

    private String hmac(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
