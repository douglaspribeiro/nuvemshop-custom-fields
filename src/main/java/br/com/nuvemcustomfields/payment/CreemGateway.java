package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.properties.CreemProperties;
import br.com.nuvemcustomfields.properties.WinbackProperties;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class CreemGateway implements PaymentGateway {
    private final CreemProperties properties;
    private final PaymentCatalogPriceRepository catalog;
    private final RestClient client;
    private final ObjectMapper mapper;
    private final WinbackProperties winback;

    @org.springframework.beans.factory.annotation.Autowired
    public CreemGateway(CreemProperties properties, PaymentCatalogPriceRepository catalog,
                        RestClient.Builder builder, ObjectMapper mapper, WinbackProperties winback) {
        this(properties, catalog, timedClient(builder, properties), mapper, winback);
    }

    CreemGateway(CreemProperties properties, PaymentCatalogPriceRepository catalog, RestClient client, ObjectMapper mapper,
                 WinbackProperties winback) {
        this.properties = properties; this.catalog = catalog; this.client = client; this.mapper = mapper;
        this.winback = winback;
    }
    private static RestClient timedClient(RestClient.Builder builder, CreemProperties properties) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.safeTimeoutSeconds())).build());
        factory.setReadTimeout(Duration.ofSeconds(properties.safeTimeoutSeconds()));
        return builder.requestFactory(factory).build();
    }

    @Override public PaymentProviderType provider() { return PaymentProviderType.CREEM; }
    @Override public PaymentEnvironment environment() { return properties.environment(); }
    @Override public boolean configured() { return properties.configured(); }
    @Override public boolean operational() { return properties.operational(); }
    public boolean apiConfigured() { return properties.apiConfigured(); }
    public int graceDays() { return properties.safeGraceDays(); }
    @Override public boolean supports(Store store) {
        if (store == null || !configured() || !purchaseAllowed(store)) return false;
        var premium = price(store, PlanType.PREMIUM);
        var plus = price(store, PlanType.PREMIUM_PLUS);
        return premium.isPresent() && plus.isPresent() && premium.get().getCurrency().equals(plus.get().getCurrency());
    }
    @Override public BigDecimal amount(PlanType plan) { throw new IllegalArgumentException("O preço Creem depende do país da loja."); }
    @Override public BigDecimal amount(Store store, PlanType plan) { return requirePrice(store, plan).getAmountValue(); }
    @Override public String currency(Store store) { return requirePrice(store, PlanType.PREMIUM).getCurrency(); }
    @Override public String priceId(Store store, PlanType plan) { return requirePrice(store, plan).getProviderPriceId(); }
    @Override public boolean planAvailable(Store store, PlanType plan) {
        return configured() && supports(store) && plan != null && plan.isBillable()
                && price(store, plan).filter(p -> p.getCurrency().equals(requirePrice(store, PlanType.PREMIUM).getCurrency())).isPresent();
    }

    public JsonNode createProduct(String name, String description, String currency, BigDecimal amount,
                                  String taxMode, String idempotencyKey) {
        return post("/products", Map.of("name", name, "description", description,
                "currency", currency, "price", minor(amount), "billing_type", "recurring",
                "billing_period", "every-month", "tax_mode", taxMode, "tax_category", "saas"), idempotencyKey);
    }

    public JsonNode getProduct(String id) { return get("/products/" + safeId(id)); }
    public JsonNode getCheckout(String id) { return get("/checkouts?checkout_id=" + safeId(id)); }
    public JsonNode getTransaction(String id) { return get("/transactions?transaction_id=" + safeId(id)); }

    @Override public void validateCatalog(PaymentCatalogPrice local) {
        if (local.getProvider() != provider() || local.getEnvironment() != environment())
            throw new IllegalArgumentException("O ambiente do produto difere da configuração Creem.");
        JsonNode remote = getProduct(local.getProviderPriceId());
        validateProduct(local, remote);
    }
    public void validateProduct(PaymentCatalogPrice local, JsonNode remote) {
        checkMode(remote);
        if (!Objects.equals(local.getProviderPriceId(), required(remote, "id"))
                || !Objects.equals(local.getCurrency(), required(remote, "currency"))
                || local.getAmountValue().compareTo(money(remote, "price")) != 0
                || !local.isRecurring() || !"recurring".equals(text(remote, "billing_type"))
                || !"every-month".equals(text(remote, "billing_period"))
                || !"active".equals(text(remote, "status"))
                || !Objects.equals(local.getTaxMode(), text(remote, "tax_mode"))
                || !"saas".equals(text(remote, "tax_category"))
                || remote.path("trial_period_days").asInt(0) > 0
                || remote.path("pay_what_you_want").asBoolean(false)
                || (remote.path("usage_prices").isArray() && !remote.path("usage_prices").isEmpty()))
            throw new IllegalArgumentException("Produto Creem divergente: confira valor, moeda, imposto, SaaS e assinatura mensal sem trial ou cobrança por uso.");
        minor(local.getAmountValue());
        if (!List.of("USD", "EUR").contains(local.getCurrency())) throw new IllegalArgumentException("Use USD ou EUR na Creem.");
    }

    @Override public GatewayCheckout createCheckout(Store store, PlanType plan, String reference, String returnUrl) {
        requirePurchaseAllowed(store);
        PaymentCatalogPrice price = requirePrice(store, plan);
        validateCatalog(price);
        JsonNode data = post("/checkouts", Map.of("product_id", price.getProviderPriceId(), "units", 1,
                "request_id", reference, "success_url", returnUrl,
                "metadata", Map.of("external_reference", reference, "store_id", store.getStoreId().toString(),
                        "country_code", price.getCountryCode(), "plan", plan.name())), null);
        String url = required(data, "checkout_url");
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                || !(uri.getHost().equals("creem.io") || uri.getHost().endsWith(".creem.io")))
            throw new PaymentGatewayException("A Creem retornou um endereço de checkout inválido.");
        return new GatewayCheckout(null, required(data, "id"), url, required(data, "status"));
    }

    @Override public GatewaySubscription getSubscription(String id) {
        JsonNode data = get("/subscriptions?subscription_id=" + safeId(id));
        JsonNode product = data.path("product");
        if (product.isTextual()) product = getProduct(product.asText());
        checkMode(product);
        String status = required(data, "status");
        Instant periodEnd = instant(text(data, "current_period_end_date"));
        if ("scheduled_cancel".equals(status) && periodEnd == null)
            throw new PaymentGatewayException("Assinatura Creem sem fim do período pago.");
        return new GatewaySubscription(required(data, "id"), null,
                text(data.path("metadata"), "external_reference"), status,
                required(product, "currency"), money(product, "price"),
                Optional.ofNullable(instant(text(data, "next_transaction_date"))).orElse(periodEnd),
                resourceId(data.path("customer")), required(product, "id"),
                instant(text(data, "current_period_start_date")), periodEnd,
                "scheduled_cancel".equals(status) ? periodEnd : null);
    }
    @Override public Optional<GatewayInvoice> getLatestInvoice(String subscriptionId) {
        JsonNode data = get("/subscriptions?subscription_id=" + safeId(subscriptionId));
        String id = text(data, "last_transaction_id");
        if (id == null) id = resourceId(data.path("last_transaction"));
        if (id == null) return Optional.empty();
        GatewayInvoice invoice = getInvoice(id);
        if (!subscriptionId.equals(invoice.subscriptionId())) throw new PaymentGatewayException("Transação Creem pertence a outra assinatura.");
        return Optional.of(invoice);
    }
    @Override public GatewayInvoice getInvoice(String id) {
        JsonNode data = getTransaction(id);
        String status = required(data, "status");
        return new GatewayInvoice(required(data, "id"), required(data, "subscription"), required(data, "id"), status,
                required(data, "currency"), data.path("amount_paid").isNumber() ? money(data, "amount_paid") : money(data, "amount"));
    }
    @Override public void cancel(String id) { post("/subscriptions/" + safeId(id) + "/cancel", Map.of("mode", "scheduled", "onExecute", "cancel"), null); }
    @Override public void cancelImmediately(String id) { post("/subscriptions/" + safeId(id) + "/cancel", Map.of("mode", "immediate"), null); }
    @Override public void cancelCheckout(String id) {
        JsonNode checkout = getCheckout(id);
        if (!"expired".equals(text(checkout, "status")))
            throw new PaymentGatewayException("O checkout Creem anterior ainda pode receber pagamento. Aguarde a expiração ou contate o suporte.");
    }
    // Confirmar a cobrança proporcional no serviço antes de liberar o novo plano.
    @Override public void changeSubscriptionPlan(String id, Store store, PlanType plan) {
        requirePurchaseAllowed(store);
        post("/subscriptions/" + safeId(id) + "/upgrade", Map.of("product_id", requirePrice(store, plan).getProviderPriceId(),
                "update_behavior", "proration-charge-immediately"), null);
    }

    @Override public GatewayNotification verifyNotification(String body, String signature, String requestId, String dataId) {
        if (!operational()) throw new PaymentGatewayException("Creem sem credenciais de webhook.");
        try {
            if (body == null || signature == null || !signature.matches("[a-fA-F0-9]{64}"))
                throw new PaymentGatewayException("Assinatura Creem inválida.");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            if (!MessageDigest.isEqual(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)), HexFormat.of().parseHex(signature)))
                throw new PaymentGatewayException("Assinatura Creem inválida.");
            JsonNode payload = mapper.readTree(body);
            checkMode(payload.path("object"));
            String eventId = required(payload, "id");
            return new GatewayNotification(provider(), "CREEM:" + environment() + ":" + eventId,
                    required(payload, "eventType"), required(payload.path("object"), "id"), null, environment(),
                    Instant.ofEpochMilli(payload.path("created_at").longValue()), body);
        } catch (PaymentGatewayException ex) { throw ex; }
        catch (Exception ex) { throw new PaymentGatewayException("Notificação Creem inválida.", ex); }
    }

    private boolean purchaseAllowed(Store store) {
        if (!properties.sandbox()) return true;
        // Sandbox requires explicit membership; winback's "0 means all" convention does not apply here.
        if (store == null || store.getStoreId() == null || winback.allowedStoreIds() == null) return false;
        return Arrays.stream(winback.allowedStoreIds().split(","))
                .map(String::trim).anyMatch(id -> id.equals(store.getStoreId().toString()));
    }
    private void requirePurchaseAllowed(Store store) {
        if (!purchaseAllowed(store))
            throw new IllegalArgumentException("Compras Creem em sandbox estão disponíveis apenas para as lojas autorizadas.");
    }

    private Optional<PaymentCatalogPrice> price(Store store, PlanType plan) {
        if (store == null || store.getStoreCountryCode() == null || plan == null) return Optional.empty();
        return catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(provider(), environment(), store.getStoreCountryCode(), plan)
                .filter(PaymentCatalogPrice::isEnabled).filter(PaymentCatalogPrice::isRecurring).filter(PaymentCatalogPrice::isValidated);
    }
    private PaymentCatalogPrice requirePrice(Store store, PlanType plan) {
        PaymentCatalogPrice selected = price(store, plan).orElseThrow(() -> new IllegalArgumentException("Catálogo Creem indisponível para este país e plano."));
        if (plan != PlanType.PREMIUM && !selected.getCurrency().equals(requirePrice(store, PlanType.PREMIUM).getCurrency()))
            throw new IllegalArgumentException("Os planos Creem do país devem usar a mesma moeda.");
        return selected;
    }
    private JsonNode get(String path) { return request(client.get().uri(properties.baseUrl() + path)); }
    private JsonNode post(String path, Object body, String key) {
        var request = client.post().uri(properties.baseUrl() + path).contentType(MediaType.APPLICATION_JSON);
        if (key != null) request.header("Idempotency-Key", key);
        return request(request.body(body));
    }
    private JsonNode request(RestClient.RequestHeadersSpec<?> request) {
        if (!apiConfigured()) throw new PaymentGatewayException("Configure a chave de API Creem no servidor.");
        try {
            JsonNode data = request.header("x-api-key", properties.apiKey()).retrieve().body(JsonNode.class);
            if (data == null) throw new PaymentGatewayException("A Creem retornou uma resposta vazia.");
            checkMode(data);
            return data;
        } catch (RestClientResponseException ex) {
            throw new PaymentGatewayException("A API Creem retornou HTTP " + ex.getStatusCode().value() + ". Confira a configuração e tente novamente.", ex);
        } catch (RestClientException ex) {
            throw new PaymentGatewayException("Não foi possível confirmar a resposta da Creem. Retome a operação antes de iniciar outra.", ex);
        }
    }
    public void checkMode(JsonNode data) {
        String mode = required(data, "mode");
        if (!(properties.sandbox() ? List.of("test", "sandbox").contains(mode) : "prod".equals(mode)))
            throw new PaymentGatewayException("A resposta Creem pertence a outro ambiente.");
    }
    public static long minor(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ONE) < 0) throw new IllegalArgumentException("O preço Creem deve ser de pelo menos 1,00.");
        try { return amount.movePointRight(2).longValueExact(); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("Preço Creem inválido: use no máximo duas casas decimais."); }
    }
    public static String resourceId(JsonNode node) { return node.isTextual() ? node.asText() : text(node, "id"); }
    public static String text(JsonNode node, String field) {
        JsonNode value = node.path(field); return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
    public static String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) throw new PaymentGatewayException("Resposta Creem sem " + field + ".");
        return value;
    }
    private static String safeId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{3,120}")) throw new IllegalArgumentException("Código Creem inválido.");
        return id;
    }
    private static BigDecimal money(JsonNode node, String field) { return new BigDecimal(required(node, field)).movePointLeft(2); }
    private static Instant instant(String value) { return value == null || value.isBlank() ? null : Instant.parse(value); }
}
