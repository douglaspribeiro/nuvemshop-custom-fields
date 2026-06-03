package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class WebhookSecurityService {

    private final NuvemshopProperties properties;

    public WebhookSecurityService(NuvemshopProperties properties) {
        this.properties = properties;
    }

    public boolean isValid(String rawBody, String hmacHeader) {
        if (hmacHeader == null || hmacHeader.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.clientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), hmacHeader.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            return false;
        }
    }
}
