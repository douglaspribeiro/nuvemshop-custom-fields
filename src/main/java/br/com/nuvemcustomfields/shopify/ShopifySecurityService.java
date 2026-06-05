package br.com.nuvemcustomfields.shopify;

import br.com.nuvemcustomfields.properties.ShopifyProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ShopifySecurityService {

    private final ShopifyProperties properties;

    public ShopifySecurityService(ShopifyProperties properties) {
        this.properties = properties;
    }

    public boolean isValidShopDomain(String shop) {
        return shop != null && shop.matches("[a-zA-Z0-9][a-zA-Z0-9-]*\\.myshopify\\.com");
    }

    public boolean validHmac(Map<String, String[]> parameters) {
        String[] hmacValues = parameters.get("hmac");
        if (hmacValues == null || hmacValues.length == 0 || hmacValues[0].isBlank()) {
            return false;
        }
        String message = parameters.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("hmac") && !entry.getKey().equals("signature"))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + String.join(",", entry.getValue()))
                .collect(Collectors.joining("&"));
        String expected = hmacHex(message);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                hmacValues[0].getBytes(StandardCharsets.UTF_8)
        );
    }

    public String hmacHex(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.clientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Nao foi possivel validar assinatura Shopify.", ex);
        }
    }

    public boolean validWebhookHmac(String rawBody, String hmacHeader) {
        if (rawBody == null || hmacHeader == null || hmacHeader.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.clientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] expected = Base64.getEncoder().encode(digest);
            return MessageDigest.isEqual(expected, hmacHeader.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Nao foi possivel validar webhook Shopify.", ex);
        }
    }
}
