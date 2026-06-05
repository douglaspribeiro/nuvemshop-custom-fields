package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@Service
public class NexoSessionTokenService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NuvemshopProperties properties;
    private final ObjectMapper objectMapper;

    public NexoSessionTokenService(NuvemshopProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Long requireStoreId(String token) {
        Map<String, Object> claims = verifiedClaims(token);
        validateTimeClaim(claims, "exp", true);
        validateTimeClaim(claims, "nbf", false);
        return extractStoreId(claims)
                .orElseThrow(() -> new IllegalArgumentException("Token Nexo sem identificador da loja."));
    }

    private Map<String, Object> verifiedClaims(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token Nexo ausente.");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Token Nexo invalido.");
        }
        try {
            Map<String, Object> header = objectMapper.readValue(base64UrlDecode(parts[0]), MAP_TYPE);
            String algorithm = String.valueOf(header.get("alg"));
            byte[] expectedSignature = sign(parts[0] + "." + parts[1], algorithm);
            byte[] actualSignature = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                throw new IllegalArgumentException("Assinatura do token Nexo invalida.");
            }
            return objectMapper.readValue(base64UrlDecode(parts[1]), MAP_TYPE);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Token Nexo invalido.", ex);
        }
    }

    private byte[] sign(String content, String algorithm) throws Exception {
        String macAlgorithm = switch (algorithm) {
            case "HS256" -> "HmacSHA256";
            case "HS384" -> "HmacSHA384";
            case "HS512" -> "HmacSHA512";
            default -> throw new IllegalArgumentException("Algoritmo de token Nexo nao suportado.");
        };
        Mac mac = Mac.getInstance(macAlgorithm);
        mac.init(new SecretKeySpec(properties.clientSecret().getBytes(StandardCharsets.UTF_8), macAlgorithm));
        return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private void validateTimeClaim(Map<String, Object> claims, String claimName, boolean mustBeFuture) {
        Object value = claims.get(claimName);
        if (!(value instanceof Number number)) {
            return;
        }
        long timestamp = number.longValue();
        long now = Instant.now().getEpochSecond();
        if (mustBeFuture && timestamp < now) {
            throw new IllegalArgumentException("Token Nexo expirado.");
        }
        if (!mustBeFuture && timestamp > now) {
            throw new IllegalArgumentException("Token Nexo ainda nao esta valido.");
        }
    }

    private Optional<Long> extractStoreId(Map<String, Object> claims) {
        return firstLong(claims, "store_id")
                .or(() -> firstLong(claims, "storeId"))
                .or(() -> firstLong(claims, "user_id"))
                .or(() -> firstLong(claims, "userId"))
                .or(() -> firstLong(claims, "id"))
                .or(() -> nestedStoreId(claims));
    }

    private Optional<Long> nestedStoreId(Map<String, Object> claims) {
        Object store = claims.get("store");
        if (store instanceof Map<?, ?> values) {
            return firstLong(values, "id")
                    .or(() -> firstLong(values, "store_id"))
                    .or(() -> firstLong(values, "storeId"));
        }
        return Optional.empty();
    }

    private Optional<Long> firstLong(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return Optional.of(number.longValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Optional.of(Long.parseLong(text));
            } catch (NumberFormatException ex) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
