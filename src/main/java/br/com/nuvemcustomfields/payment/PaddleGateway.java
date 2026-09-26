package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.properties.PaddleProperties;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PaddleGateway implements PaymentGateway {
    private final PaddleProperties properties;
    private final PaymentCatalogPriceRepository catalog;
    private final RestClient client;
    private final ObjectMapper mapper;

    public PaddleGateway(PaddleProperties properties, PaymentCatalogPriceRepository catalog,
                         RestClient.Builder builder, ObjectMapper mapper) {
        this.properties = properties;
        this.catalog = catalog;
        this.client = builder.build();
        this.mapper = mapper;
    }

    @Override public PaymentProviderType provider() { return PaymentProviderType.PADDLE; }
    @Override public PaymentEnvironment environment() { return properties.environment(); }
    @Override public boolean configured() { return properties.configured(); }
    @Override public boolean supports(Store store) {
        if (store == null || !configured()) return false;
        String country = normalize(store.getStoreCountryCode());
        if (!List.of("AR", "MX", "CL").contains(country)) return false;
        return catalog(country, PlanType.PREMIUM).isPresent() && catalog(country, PlanType.PREMIUM_PLUS).isPresent();
    }
    @Override public BigDecimal amount(PlanType plan) {
        throw new IllegalArgumentException("O preço Paddle depende do mercado da loja.");
    }
    @Override public BigDecimal amount(Store store, PlanType plan) { return requireCatalog(store, plan).getAmountValue(); }
    @Override public String currency(Store store) { return requireCatalog(store, PlanType.PREMIUM).getCurrency(); }
    @Override public String priceId(Store store, PlanType plan) { return requireCatalog(store, plan).getProviderPriceId(); }
    public String clientSideToken() { return properties.clientSideToken(); }
    public boolean sandbox() { return properties.sandbox(); }
    public int graceDays() { return properties.safeGraceDays(); }
    public long checkoutTokenMinutes() { return properties.safeCheckoutTokenMinutes(); }

    @Override
    public GatewayCheckout createCheckout(Store store, PlanType plan, String externalReference, String returnUrl) {
        PaymentCatalogPrice price = requireCatalog(store, plan);
        Map<String, Object> body = Map.of(
                "items", List.of(Map.of("price_id", price.getProviderPriceId(), "quantity", 1)),
                "collection_mode", "automatic",
                "custom_data", Map.of(
                        "external_reference", externalReference,
                        "store_id", store.getStoreId().toString(),
                        "country_code", price.getCountryCode(),
                        "plan", plan.name())
        );
        JsonNode data = post("/transactions", body).path("data");
        String transactionId = required(data, "id");
        return new GatewayCheckout(null, transactionId, text(data.path("checkout"), "url"), text(data, "status"));
    }

    @Override
    public GatewaySubscription getSubscription(String subscriptionId) {
        JsonNode data = get("/subscriptions/" + subscriptionId).path("data");
        JsonNode item = data.path("items").isArray() && !data.path("items").isEmpty()
                ? data.path("items").get(0) : mapper.createObjectNode();
        JsonNode price = item.path("price");
        JsonNode unit = price.path("unit_price");
        String currency = text(data, "currency_code");
        if (!hasText(currency)) currency = text(unit, "currency_code");
        BigDecimal amount = fromMinor(required(unit, "amount"), currency);
        JsonNode period = data.path("current_billing_period");
        JsonNode scheduled = data.path("scheduled_change");
        return new GatewaySubscription(required(data, "id"), null,
                text(data.path("custom_data"), "external_reference"), required(data, "status"), currency, amount,
                instant(text(data, "next_billed_at")), text(data, "customer_id"), text(price, "id"),
                instant(text(period, "starts_at")), instant(text(period, "ends_at")),
                instant(text(scheduled, "effective_at")));
    }

    @Override
    public Optional<GatewayInvoice> getLatestInvoice(String subscriptionId) {
        JsonNode data = get("/transactions?subscription_id=" + subscriptionId + "&order_by=created_at%5BDESC%5D&per_page=1").path("data");
        return data.isArray() && !data.isEmpty() ? Optional.of(invoice(data.get(0))) : Optional.empty();
    }

    @Override public GatewayInvoice getInvoice(String invoiceId) { return invoice(get("/transactions/" + invoiceId).path("data")); }

    public PaddleTransaction getTransaction(String transactionId) {
        JsonNode data = get("/transactions/" + transactionId).path("data");
        String currency = required(data, "currency_code");
        String total = required(data.path("details").path("totals"), "total");
        return new PaddleTransaction(required(data, "id"), text(data, "subscription_id"),
                text(data.path("custom_data"), "external_reference"), required(data, "status"),
                currency, fromMinor(total, currency), text(data, "customer_id"));
    }

    @Override public void cancel(String subscriptionId) {
        post("/subscriptions/" + subscriptionId + "/cancel", Map.of("effective_from", "next_billing_period"));
    }
    @Override public void cancelImmediately(String subscriptionId) {
        post("/subscriptions/" + subscriptionId + "/cancel", Map.of("effective_from", "immediately"));
    }
    @Override public void cancelCheckout(String transactionId) {
        patch("/transactions/" + transactionId, Map.of("status", "canceled"));
    }

    @Override
    public GatewayNotification verifyNotification(String body, String signature, String requestId, String dataId) {
        requireConfigured();
        try {
            SignatureParts parts = signature(signature);
            long age = Math.abs(Duration.between(Instant.ofEpochSecond(parts.timestamp()), Instant.now()).toSeconds());
            if (age > properties.safeWebhookToleranceSeconds()) {
                throw new PaymentGatewayException("Assinatura Paddle expirada.");
            }
            String signed = parts.timestamp() + ":" + (body == null ? "" : body);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(signed.getBytes(StandardCharsets.UTF_8));
            boolean matches = parts.signatures().stream().map(PaddleGateway::decodeHex)
                    .anyMatch(candidate -> candidate != null && MessageDigest.isEqual(expected, candidate));
            if (!matches) throw new PaymentGatewayException("Assinatura Paddle inválida.");
            JsonNode payload = mapper.readTree(body);
            String eventId = required(payload, "event_id");
            String notificationId = text(payload, "notification_id");
            String type = required(payload, "event_type");
            String resourceId = required(payload.path("data"), "id");
            return new GatewayNotification(provider(), "PADDLE:" + eventId, type, resourceId,
                    notificationId, environment(), instant(text(payload, "occurred_at")), body);
        } catch (PaymentGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PaymentGatewayException("Notificação Paddle inválida.", ex);
        }
    }

    public void validateCatalog(PaymentCatalogPrice local) {
        if (local.getProvider() != PaymentProviderType.PADDLE || local.getEnvironment() != environment()) {
            throw new IllegalArgumentException("Catálogo não pertence ao ambiente Paddle configurado.");
        }
        if (!hasText(local.getProviderPriceId())) throw new IllegalArgumentException("Informe o ID de preço Paddle.");
        JsonNode remote = get("/prices/" + local.getProviderPriceId()).path("data");
        String remoteCurrency = text(remote.path("unit_price"), "currency_code");
        BigDecimal remoteAmount = fromMinor(required(remote.path("unit_price"), "amount"), remoteCurrency);
        JsonNode cycle = remote.path("billing_cycle");
        if (!local.getCurrency().equalsIgnoreCase(remoteCurrency) || local.getAmountValue().compareTo(remoteAmount) != 0
                || !"internal".equalsIgnoreCase(text(remote, "tax_mode"))
                || !"month".equalsIgnoreCase(text(cycle, "interval")) || cycle.path("frequency").asInt() != 1) {
            throw new IllegalArgumentException("Preço Paddle divergente: moeda, valor, imposto interno e recorrência mensal são obrigatórios.");
        }
    }

    private PaymentCatalogPrice requireCatalog(Store store, PlanType plan) {
        if (store == null) throw new IllegalArgumentException("Loja não informada.");
        return catalog(normalize(store.getStoreCountryCode()), plan)
                .orElseThrow(() -> new IllegalArgumentException("Catálogo Paddle indisponível para este país e plano."));
    }
    private Optional<PaymentCatalogPrice> catalog(String country, PlanType plan) {
        return catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(provider(), environment(), country, plan)
                .filter(PaymentCatalogPrice::isEnabled).filter(PaymentCatalogPrice::isRecurring)
                .filter(p -> "internal".equalsIgnoreCase(p.getTaxMode())).filter(p -> hasText(p.getProviderPriceId()));
    }
    private GatewayInvoice invoice(JsonNode data) {
        return new GatewayInvoice(required(data, "id"), required(data, "subscription_id"), required(data, "id"), required(data, "status"));
    }
    private JsonNode get(String path) { return request(client.get().uri(properties.apiBaseUrl() + path)); }
    private JsonNode post(String path, Object body) { return request(client.post().uri(properties.apiBaseUrl() + path).contentType(MediaType.APPLICATION_JSON).body(body)); }
    private JsonNode patch(String path, Object body) { return request(client.patch().uri(properties.apiBaseUrl() + path).contentType(MediaType.APPLICATION_JSON).body(body)); }
    private JsonNode request(RestClient.RequestHeadersSpec<?> request) {
        requireConfigured();
        try {
            return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .header("Paddle-Version", properties.safeApiVersion()).retrieve().body(JsonNode.class);
        } catch (RestClientException ex) {
            String detail = ex.getMessage();
            if (ex instanceof RestClientResponseException response && hasText(response.getResponseBodyAsString())) detail = response.getResponseBodyAsString();
            if (detail == null) detail = ex.getClass().getSimpleName();
            detail = detail.replaceAll("\\s+", " ");
            if (detail.length() > 300) detail = detail.substring(0, 300);
            throw new PaymentGatewayException("Paddle retornou erro: " + detail, ex);
        }
    }
    private void requireConfigured() { if (!configured()) throw new PaymentGatewayException("Paddle não configurada."); }
    private static SignatureParts signature(String header) {
        if (!hasText(header)) throw new PaymentGatewayException("Assinatura Paddle ausente.");
        Long timestamp = null; List<String> hashes = new ArrayList<>();
        for (String part : header.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) continue;
            if ("ts".equals(pair[0])) timestamp = Long.parseLong(pair[1]);
            if ("h1".equals(pair[0])) hashes.add(pair[1]);
        }
        if (timestamp == null || hashes.isEmpty()) throw new PaymentGatewayException("Assinatura Paddle inválida.");
        return new SignatureParts(timestamp, hashes);
    }
    private static byte[] decodeHex(String value) { try { return HexFormat.of().parseHex(value); } catch (RuntimeException ex) { return null; } }
    private static String required(JsonNode node, String field) {
        String value = text(node, field); if (!hasText(value)) throw new PaymentGatewayException("Resposta Paddle sem " + field + "."); return value;
    }
    private static String text(JsonNode node, String field) { JsonNode v=node==null?null:node.path(field); return v==null||v.isMissingNode()||v.isNull()?null:v.asText(); }
    private static Instant instant(String value) { return hasText(value) ? Instant.parse(value) : null; }
    private static BigDecimal fromMinor(String value, String currency) { return new BigDecimal(value).movePointLeft("CLP".equalsIgnoreCase(currency) ? 0 : 2); }
    private static String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(); }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private record SignatureParts(long timestamp, List<String> signatures) {}
    public record PaddleTransaction(String id, String subscriptionId, String externalReference, String status,
                                    String currency, BigDecimal amount, String customerId) {}
}
