package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.MercadoPagoProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MercadoPagoGateway implements PaymentGateway {
    private final MercadoPagoProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MercadoPagoGateway(MercadoPagoProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
    }

    @Override public PaymentProviderType provider() { return PaymentProviderType.MERCADO_PAGO; }
    @Override public boolean configured() { return properties.configured(); }
    @Override public boolean supports(Store store) {
        return store != null && "BR".equalsIgnoreCase(store.getStoreCountryCode())
                && (store.getStoreCurrency() == null || "BRL".equalsIgnoreCase(store.getStoreCurrency()));
    }
    @Override public BigDecimal amount(PlanType plan) { return properties.amount(plan); }

    @Override
    public GatewayCheckout createCheckout(Store store, PlanType plan, String externalReference, String returnUrl,
                                          String payerEmail) {
        requireConfigured();
        if (payerEmail == null || payerEmail.isBlank()) {
            throw new IllegalArgumentException("Informe o e-mail da conta Mercado Pago.");
        }
        Map<String, Object> payload = Map.of(
                "reason", "Campos Personalizados - " + plan.getDisplayName(),
                "external_reference", externalReference,
                "payer_email", payerEmail,
                "auto_recurring", Map.of(
                        "frequency", 1,
                        "frequency_type", "months",
                        "transaction_amount", amount(plan),
                        "currency_id", "BRL"
                ),
                "payment_methods_allowed", Map.of(
                        "payment_types", List.of(Map.of("id", "credit_card"))
                ),
                "back_url", returnUrl,
                "status", "pending"
        );
        JsonNode response = post("/preapproval", payload, externalReference);
        return new GatewayCheckout(requiredText(response, "id"), requiredText(response, "init_point"), text(response, "status"));
    }

    @Override
    public GatewaySubscription getSubscription(String subscriptionId) {
        JsonNode response = get("/preapproval/" + subscriptionId);
        JsonNode recurring = response.path("auto_recurring");
        return new GatewaySubscription(
                requiredText(response, "id"),
                text(response, "external_reference"),
                text(response, "status"),
                text(recurring, "currency_id"),
                recurring.path("transaction_amount").decimalValue(),
                instant(text(response, "next_payment_date"))
        );
    }

    @Override
    public Optional<GatewayInvoice> getLatestInvoice(String subscriptionId) {
        JsonNode response = get("/authorized_payments/search?preapproval_id=" + subscriptionId
                + "&sort=date_created&criteria=desc&limit=1");
        JsonNode results = response.path("results");
        return results.isArray() && !results.isEmpty() ? Optional.of(invoice(results.get(0))) : Optional.empty();
    }

    @Override
    public GatewayInvoice getInvoice(String invoiceId) {
        return invoice(get("/authorized_payments/" + invoiceId));
    }

    @Override
    public void cancel(String subscriptionId) {
        put("/preapproval/" + subscriptionId, Map.of("status", "cancelled"));
    }

    @Override
    public GatewayNotification verifyNotification(String body, String signature, String requestId, String dataId) {
        requireConfigured();
        try {
            JsonNode payload = objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
            String resourceId = hasText(dataId) ? dataId : text(payload.path("data"), "id");
            validateSignature(signature, requestId, resourceId);
            String type = requiredText(payload, "type");
            String notificationId = requiredText(payload, "id");
            String action = text(payload, "action");
            String eventKey = "MERCADO_PAGO:" + safe(type) + ":" + safe(notificationId) + ":" + safe(action);
            return new GatewayNotification(provider(), eventKey, type, resourceId);
        } catch (PaymentGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PaymentGatewayException("Notificacao Mercado Pago invalida.", ex);
        }
    }

    private GatewayInvoice invoice(JsonNode response) {
        JsonNode payment = response.path("payment");
        return new GatewayInvoice(
                requiredText(response, "id"),
                requiredText(response, "preapproval_id"),
                text(payment, "id"),
                text(payment, "status")
        );
    }

    private void validateSignature(String signature, String requestId, String dataId) throws Exception {
        if (!hasText(signature) || !hasText(requestId) || !hasText(dataId)) {
            throw new PaymentGatewayException("Assinatura Mercado Pago ausente.");
        }
        String timestamp = null;
        String received = null;
        for (String part : signature.split(",")) {
            String[] entry = part.trim().split("=", 2);
            if (entry.length == 2 && "ts".equals(entry[0])) timestamp = entry[1];
            if (entry.length == 2 && "v1".equals(entry[0])) received = entry[1];
        }
        if (!hasText(timestamp) || !hasText(received)) {
            throw new PaymentGatewayException("Assinatura Mercado Pago invalida.");
        }
        String manifest = "id:" + dataId.toLowerCase() + ";request-id:" + requestId + ";ts:" + timestamp + ";";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), received.getBytes(StandardCharsets.US_ASCII))) {
            throw new PaymentGatewayException("Assinatura Mercado Pago invalida.");
        }
    }

    private JsonNode get(String path) {
        requireConfigured();
        try {
            return restClient.get().uri(properties.apiBaseUrl() + path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException ex) {
            throw apiFailure(ex);
        }
    }

    private JsonNode post(String path, Object payload, String idempotencyKey) {
        try {
            return restClient.post().uri(properties.apiBaseUrl() + path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                    .header("X-Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON).body(payload)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException ex) {
            throw apiFailure(ex);
        }
    }

    private JsonNode put(String path, Object payload) {
        try {
            return restClient.put().uri(properties.apiBaseUrl() + path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                    .contentType(MediaType.APPLICATION_JSON).body(payload)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException ex) {
            throw apiFailure(ex);
        }
    }

    private PaymentGatewayException apiFailure(RestClientException ex) {
        String detail;
        String status = "erro de comunicacao";
        if (ex instanceof RestClientResponseException response) {
            String body = response.getResponseBodyAsString();
            detail = body == null || body.isBlank() ? response.getMessage() : body.replaceAll("\\s+", " ");
            status = response.getStatusCode().toString();
        } else {
            detail = ex.getMessage();
        }
        if (detail == null) detail = ex.getClass().getSimpleName();
        if (detail.length() > 300) detail = detail.substring(0, 300);
        return new PaymentGatewayException("Mercado Pago retornou " + status + ": " + detail, ex);
    }

    private void requireConfigured() {
        if (!configured()) throw new PaymentGatewayException("Mercado Pago nao configurado.");
    }
    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (!hasText(value)) throw new PaymentGatewayException("Resposta Mercado Pago sem " + field + ".");
        return value;
    }
    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value == null || value.isMissingNode() || value.isNull() ? null : value.asText();
    }
    private static Instant instant(String value) {
        if (!hasText(value)) return null;
        try { return OffsetDateTime.parse(value).toInstant(); }
        catch (RuntimeException ignored) { return Instant.parse(value); }
    }
    private static String safe(String value) { return value == null ? "-" : value; }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
}
