package br.com.nuvemcustomfields.payment;

import br.com.efi.efisdk.EfiPay;
import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.EfiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class EfiGateway implements PaymentGateway {
    private final EfiProperties properties;
    private final ObjectMapper mapper;

    public EfiGateway(EfiProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override public PaymentProviderType provider() { return PaymentProviderType.EFI; }
    @Override public boolean configured() { return properties.configured(); }
    @Override public boolean supports(Store store) {
        return store != null && "BR".equalsIgnoreCase(store.getStoreCountryCode())
                && (store.getStoreCurrency() == null || "BRL".equalsIgnoreCase(store.getStoreCurrency()));
    }
    @Override public BigDecimal amount(PlanType plan) { return properties.amount(plan); }
    public String payeeCode() { return properties.payeeCode(); }
    public boolean sandbox() { return properties.sandbox(); }
    public String planId(PlanType plan) { return properties.planId(plan); }

    // A Efí usa checkout transparente; esta operação é feita pelo formulário do app.
    @Override public GatewayCheckout createCheckout(Store store, PlanType plan, String reference, String returnUrl) {
        throw new UnsupportedOperationException("Use o formulário de pagamento Efí.");
    }

    public String createSubscription(PlanType plan, String reference, String notificationUrl) {
        long cents = amount(plan).movePointRight(2).longValueExact();
        Map<String, Object> body = Map.of(
                "items", List.of(Map.of("name", "Campos Personalizados - " + plan.getDisplayName(),
                        "value", cents, "amount", 1)),
                "metadata", Map.of("custom_id", reference, "notification_url", notificationUrl));
        JsonNode data = call("createSubscription", Map.of("id", properties.planId(plan)), body).path("data");
        return required(data, "subscription_id");
    }

    public JsonNode pay(String subscriptionId, EfiPayer payer, String paymentToken) {
        Map<String, Object> customer = new HashMap<>();
        customer.put("name", payer.name());
        customer.put("cpf", payer.cpf());
        customer.put("email", payer.email());
        customer.put("phone_number", payer.phone());
        customer.put("birth", payer.birth());
        Map<String, Object> card = Map.of("customer", customer, "payment_token", paymentToken);
        return call("defineSubscriptionPayMethod", Map.of("id", subscriptionId),
                Map.of("payment", Map.of("credit_card", card))).path("data");
    }

    @Override public GatewaySubscription getSubscription(String id) {
        JsonNode data = detail(id);
        String date = data.path("next_execution").asText("");
        return new GatewaySubscription(required(data, "subscription_id"),
                data.path("plan").path("plan_id").asText(), data.path("custom_id").asText(null),
                data.path("status").asText(), "BRL",
                BigDecimal.valueOf(data.path("value").asLong(), 2),
                date.isBlank() || "null".equals(date) ? null
                        : LocalDate.parse(date.substring(0, 10)).atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant());
    }

    @Override public Optional<GatewayInvoice> getLatestInvoice(String subscriptionId) {
        JsonNode history = detail(subscriptionId).path("history");
        if (!history.isArray() || history.isEmpty()) return Optional.empty();
        for (int index = history.size() - 1; index >= 0; index--) {
            JsonNode entry = history.get(index);
            String chargeId = entry.path("charge_id").asText("");
            if (!chargeId.isBlank() && !"null".equals(chargeId)) {
                return Optional.of(getCharge(subscriptionId, chargeId));
            }
        }
        return Optional.empty();
    }

    public GatewayInvoice getCharge(String subscriptionId, String chargeId) {
        JsonNode charge = call("detailCharge", Map.of("id", chargeId), Map.of()).path("data");
        return new GatewayInvoice(chargeId, subscriptionId, chargeId, required(charge, "status"));
    }

    public void cancelCharge(String chargeId) {
        call("cancelCharge", Map.of("id", chargeId), Map.of());
    }

    @Override public GatewayInvoice getInvoice(String invoiceId) {
        throw new UnsupportedOperationException("Concilie a assinatura Efí pelo ID da assinatura.");
    }

    @Override public void cancel(String subscriptionId) {
        call("cancelSubscription", Map.of("id", subscriptionId), Map.of());
    }

    @Override public void cancelCheckout(String checkoutResourceId) {
        throw new UnsupportedOperationException("Plano Efí compartilhado não pode ser cancelado individualmente.");
    }

    @Override public GatewayNotification verifyNotification(String body, String signature, String requestId, String dataId) {
        throw new UnsupportedOperationException("A notificação Efí usa token consultado na API.");
    }

    public JsonNode notification(String token) {
        return call("getNotification", Map.of("token", token), Map.of()).path("data");
    }

    private JsonNode detail(String id) {
        return call("detailSubscription", Map.of("id", id), Map.of()).path("data");
    }

    private JsonNode call(String method, Map<String, String> params, Map<String, Object> body) {
        if (!configured()) throw new PaymentGatewayException("Efí não configurada.");
        try {
            Map<String, Object> options = Map.of("client_id", properties.clientId(),
                    "client_secret", properties.clientSecret(), "sandbox", properties.sandbox());
            EfiPay api = new EfiPay(options);
            Map<String, Object> result = api.call(method, new HashMap<>(params), body);
            return mapper.valueToTree(result);
        } catch (Exception ex) {
            // A resposta do provedor pode conter dados sensíveis. Não a exiba ao lojista nem em logs.
            throw new PaymentGatewayException("Não foi possível concluir a operação na Efí. Tente novamente ou contate o suporte.", ex);
        }
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) throw new PaymentGatewayException("Resposta da Efí sem " + field + ".");
        return value;
    }

    public record EfiPayer(String name, String cpf, String email, String phone, String birth) {}
}
