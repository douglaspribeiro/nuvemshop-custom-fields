package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NexoSessionTokenServiceTest {

    private final NuvemshopProperties properties = new NuvemshopProperties(
            "33395",
            "test-secret",
            "https://app.example.com/oauth/callback",
            "https://www.tiendanube.com/apps/{clientId}/authorize",
            "https://www.tiendanube.com/apps/authorize/token",
            "https://api.tiendanube.com",
            "https://app.example.com",
            "read_products",
            "Campos Personalizados suporte@example.com",
            "7100",
            "7200"
    );

    private final NexoSessionTokenService service = new NexoSessionTokenService(properties, new ObjectMapper());

    @Test
    void extractsStoreIdFromValidToken() {
        String token = token("{\"store_id\":5538394,\"exp\":" + (Instant.now().getEpochSecond() + 60) + "}");

        Long storeId = service.requireStoreId(token);

        assertThat(storeId).isEqualTo(5538394L);
    }

    @Test
    void rejectsInvalidSignature() {
        String token = token("{\"store_id\":5538394}") + "x";

        assertThatThrownBy(() -> service.requireStoreId(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Assinatura");
    }

    private String token(String payloadJson) {
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url(payloadJson);
        String content = header + "." + payload;
        return content + "." + base64Url(sign(content));
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value);
    }

    private byte[] sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.clientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
