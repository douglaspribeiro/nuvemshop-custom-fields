package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import com.dodopayments.api.client.DodoPaymentsClient;
import com.dodopayments.api.client.okhttp.DodoPaymentsOkHttpClient;
import com.dodopayments.api.core.UnwrapWebhookParams;
import com.dodopayments.api.core.http.Headers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;

/** Same pricing and payment validation in both environments. */
public abstract class DodoGateway implements PaymentGateway {
    private final String baseUrl;
    private final DodoConnection properties;
    private final PaymentCatalogPriceRepository catalog;
    private final RestClient client;
    private final ObjectMapper mapper;
    private final DodoPaymentsClient verifier;

    protected DodoGateway(DodoConnection properties, PaymentCatalogPriceRepository catalog, RestClient client, ObjectMapper mapper) {
        this.properties = properties; this.catalog = catalog; this.client = client; this.mapper = mapper;
        baseUrl = properties.environment() == PaymentEnvironment.SANDBOX ? "https://test.dodopayments.com" : "https://live.dodopayments.com";
        verifier = DodoPaymentsOkHttpClient.builder().bearerToken("webhook_verifier").baseUrl(baseUrl).maxRetries(0).build();
    }
    @jakarta.annotation.PreDestroy public void close() { verifier.close(); }
    protected static RestClient timedClient(RestClient.Builder builder, DodoConnection properties) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.safeTimeoutSeconds())).build());
        factory.setReadTimeout(Duration.ofSeconds(properties.safeTimeoutSeconds()));
        return builder.requestFactory(factory).build();
    }
    @Override public PaymentProviderType provider() { return properties.provider(); }
    @Override public PaymentEnvironment environment() { return properties.environment(); }
    @Override public boolean configured() { return properties.configured(); }
    @Override public boolean operational() { return properties.operational(); }
    public boolean allowed(Store store) {
        return store != null && store.isActive() && properties.allows(store.getStoreId())
                && store.getStoreCountryCode() != null && !"BR".equalsIgnoreCase(store.getStoreCountryCode());
    }
    @Override public boolean supports(Store store) {
        return configured() && allowed(store) && Arrays.stream(PlanType.values()).anyMatch(plan -> price(store, plan).isPresent());
    }
    @Override public boolean planAvailable(Store store, PlanType plan) {
        return configured() && allowed(store) && plan != null && plan.isBillable() && price(store, plan).filter(p -> p.getCurrency().equals(currency(store))).isPresent();
    }
    @Override public BigDecimal amount(PlanType plan) { throw new IllegalArgumentException("O produto Dodo depende do país e do plano."); }
    @Override public BigDecimal amount(Store store, PlanType plan) { return requirePrice(store, plan).getAmountValue(); }
    @Override public String priceId(Store store, PlanType plan) { return requirePrice(store, plan).getProviderPriceId(); }
    @Override public String currency(Store store) {
        return Arrays.stream(PlanType.values()).map(plan -> price(store, plan)).flatMap(Optional::stream)
                .map(PaymentCatalogPrice::getCurrency).findFirst().orElseThrow(() -> new IllegalArgumentException("Produto Dodo não configurado."));
    }
    public JsonNode getProduct(String id) { return get("/products/" + safeId(id)); }
    public JsonNode getCheckout(String id) { return get("/checkouts/" + safeId(id)); }
    @Override public void validateCatalog(PaymentCatalogPrice local) {
        if (local.getProvider() != provider() || local.getEnvironment() != environment())
            throw new IllegalArgumentException("O ambiente do produto difere do gateway Dodo.");
        DodoMarketPricing.validate(local.getCountryCode(), local.getCurrency(), local.getAmountValue());
        JsonNode product = getProduct(local.getProviderPriceId());
        JsonNode price = product.path("price");
        if (!Objects.equals(local.getProviderPriceId(), text(product, "product_id"))
                || !product.path("is_recurring").asBoolean() || !"saas".equals(text(product, "tax_category"))
                || !"recurring_price".equals(text(price, "type")) || !local.isRecurring()
                || !"Month".equals(text(price, "payment_frequency_interval")) || price.path("payment_frequency_count").asInt() != 1
                || price.path("trial_period_days").asInt() != 0 || price.path("discount").asInt() != 0 || price.path("discount_bps").asInt() != 0
                || price.path("purchasing_power_parity").asBoolean() || price.path("tax_inclusive").asBoolean())
            throw new IllegalArgumentException("Use um produto Dodo SaaS mensal, sem trial, desconto, impostos inclusos ou preço variável.");
        JsonNode resolved = price;
        String mode = text(product, "pricing_mode");
        if (mode != null) {
            if (!List.of("by_country", "by_currency").contains(mode))
                throw new IllegalArgumentException("Modo de preço Dodo não suportado.");
            JsonNode overrides = get("/products/" + safeId(local.getProviderPriceId()) + "/localized-prices").path("items");
            if (!overrides.isArray()) throw new PaymentGatewayException("Lista de preços regionais Dodo inválida.");
            for (JsonNode override : overrides) {
                boolean match = "by_country".equals(mode) ? local.getCountryCode().equals(text(override, "country_code"))
                        : local.getCurrency().equals(text(override, "currency"));
                if (match) { resolved = override; break; }
            }
        }
        String amountField = resolved == price ? "price" : "amount";
        if (!local.getCurrency().equals(text(resolved, "currency")) || local.getAmountValue().compareTo(money(resolved, amountField)) != 0)
            throw new IllegalArgumentException("O preço regional fixo do Dodo difere do catálogo. Confira país, moeda e valor; conversão automática não substitui um preço fixo.");
    }
    public Optional<JsonNode> discountByCode(String code) {
        if (code == null || !code.matches("[A-Z0-9_-]{3,36}")) throw new IllegalArgumentException("Código de cupom Dodo inválido.");
        try { return Optional.of(get("/discounts/code/" + code)); }
        catch (PaymentGatewayException ex) {
            if (ex.getCause() instanceof RestClientResponseException http && http.getStatusCode().value() == 404)
                return Optional.empty();
            throw ex;
        }
    }

    /** Called only by an authenticated backoffice save. Metadata prevents taking over another discount. */
    public JsonNode synchronizeDiscount(UpgradeCoupon coupon, List<String> productIds) {
        if (!operational() || productIds.isEmpty() || coupon.getEnvironment()!=environment()
                || coupon.getProvider()!=PaymentProviderType.DODO || coupon.getPurpose()!=UpgradeCoupon.Purpose.FIRST_PAYMENT)
            throw new IllegalArgumentException("Configure o Dodo, o ambiente e os produtos do plano antes de ativar o cupom.");
        return synchronizeManagedDiscount(coupon.getCode(),coupon.getDiscountPercent().movePointRight(2).intValueExact(),
                coupon.getStartsAt(),coupon.getEndsAt(),coupon.getMaxUses(),Map.of("ncf_coupon_id",coupon.getId().toString()),productIds);
    }

    public JsonNode synchronizeWinbackDiscount(WinbackCoupon coupon,List<String> productIds) {
        if (!operational() || productIds.isEmpty() || coupon.getProvider()!=provider() || coupon.getProviderEnvironment()!=environment())
            throw new IllegalArgumentException("Reconquista Dodo indisponível neste ambiente.");
        return synchronizeManagedDiscount(coupon.getCode(),5000,null,coupon.getExpiresAt(),1,
                Map.of("ncf_winback_coupon",coupon.getCode(),"ncf_winback_campaign",coupon.getCampaignId()),productIds);
    }

    private JsonNode synchronizeManagedDiscount(String code,int amount,Instant starts,Instant expires,Integer limit,
            Map<String,String> owner,List<String> productIds) {
        var body = new LinkedHashMap<String, Object>();
        body.put("amount",amount); body.put("code",code); body.put("subscription_cycles",1);
        body.put("per_customer_usage_limit",null); body.put("customer_eligibility","any");
        body.put("preserve_on_plan_change",false); body.put("restricted_to",productIds); body.put("metadata",owner);
        body.put("starts_at",starts==null?null:starts.toString()); body.put("expires_at",expires==null?null:expires.toString());
        body.put("usage_limit",limit);
        var existing=discountByCode(code);
        if (existing.isPresent()) {
            var remote=existing.get();
            if (!"percentage".equals(text(remote,"type")) || owner.entrySet().stream()
                    .anyMatch(entry->!entry.getValue().equals(text(remote.path("metadata"),entry.getKey()))))
                throw new IllegalArgumentException("Este código já existe no Dodo e não pertence a este cupom do aplicativo.");
            return request(client.patch().uri(baseUrl+"/discounts/"+safeId(required(remote,"discount_id")))
                    .contentType(MediaType.APPLICATION_JSON).body(body));
        }
        body.put("type","percentage");
        return request(client.post().uri(baseUrl+"/discounts").contentType(MediaType.APPLICATION_JSON).body(body));
    }

    private Map<String, Object> checkoutBody(Store store, PlanType plan, String couponCode) {
        var price = requirePrice(store, plan);
        var body = new LinkedHashMap<String, Object>();
        body.put("product_cart", List.of(Map.of("product_id", price.getProviderPriceId(), "quantity", 1)));
        body.put("billing_currency", price.getCurrency());
        body.put("billing_address", Map.of("country", store.getStoreCountryCode()));
        body.put("feature_flags", Map.of("allow_currency_selection", false, "allow_discount_code", false,
                "allow_customer_editing_country", false));
        if (couponCode != null) body.put("discount_codes", List.of(couponCode));
        return body;
    }

    public BigDecimal previewFirstAmount(Store store, PlanType plan, String code) {
        if (!planAvailable(store, plan)) throw new IllegalArgumentException("Plano Dodo indisponível.");
        // Use the gateway's own regional-price calculation, including zero-decimal currencies.
        BigDecimal baseline = previewAmount(store, plan, null);
        if (baseline.compareTo(amount(store, plan)) != 0)
            throw new PaymentGatewayException("O preço do Dodo mudou. Confira o catálogo antes de aplicar o cupom.");
        BigDecimal first = previewAmount(store, plan, code);
        if (first.signum() <= 0 || first.compareTo(baseline) >= 0)
            throw new IllegalArgumentException("O Dodo não confirmou um desconto válido para a primeira mensalidade.");
        return first;
    }

    private BigDecimal previewAmount(Store store, PlanType plan, String code) {
        JsonNode data = request(client.post().uri(baseUrl + "/checkouts/preview").contentType(MediaType.APPLICATION_JSON)
                .body(checkoutBody(store, plan, code)));
        JsonNode cart = data.path("product_cart");
        if (!cart.isArray() || cart.size() != 1 || !priceId(store, plan).equals(text(cart.get(0), "product_id"))
                || !currency(store).equals(text(cart.get(0), "currency")) || cart.get(0).path("quantity").asInt() != 1
                || !cart.get(0).path("is_subscription").asBoolean())
            throw new PaymentGatewayException("Prévia Dodo divergente do produto ou da moeda escolhida.");
        return money(cart.get(0), "discounted_price");
    }

    @Override public GatewayCheckout createCheckout(Store store, PlanType plan, String reference, String returnUrl) {
        return createCheckout(store, plan, reference, returnUrl, null);
    }
    public GatewayCheckout createCheckout(Store store, PlanType plan, String reference, String returnUrl, String couponCode) {
        if (!planAvailable(store, plan)) throw new IllegalArgumentException("Loja ou plano não autorizado para Dodo.");
        var body = checkoutBody(store, plan, couponCode);
        body.put("return_url", returnUrl);
        body.put("metadata", Map.of("external_reference", reference, "store_id", store.getStoreId().toString(), "plan", plan.name()));
        var data = request(client.post().uri(baseUrl + "/checkouts").contentType(MediaType.APPLICATION_JSON).body(body));
        var uri = URI.create(required(data, "checkout_url"));
        if (!"https".equals(uri.getScheme()) || !(environment() == PaymentEnvironment.SANDBOX ? "test.checkout.dodopayments.com" : "checkout.dodopayments.com").equals(uri.getHost()) || uri.getUserInfo() != null)
            throw new PaymentGatewayException("Endereço de checkout Dodo inválido.");
        return new GatewayCheckout(null, required(data, "session_id"), uri.toString(), "pending");
    }
    @Override public GatewaySubscription getSubscription(String id) {
        var data = get("/subscriptions/" + safeId(id));
        Instant end = instant(text(data, "next_billing_date"));
        String status = required(data, "status");
        if ("on_hold".equals(status)) status = "past_due";
        // Preserve terminal states: a failed creation/expired term requires a new subscription.
        boolean scheduled = data.path("cancel_at_next_billing_date").asBoolean();
        if (scheduled && end == null) throw new PaymentGatewayException("Assinatura Dodo sem fim do período pago.");
        return new GatewaySubscription(required(data, "subscription_id"), null,
                text(data.path("metadata"), "external_reference"), status, required(data, "currency"), money(data, "recurring_pre_tax_amount"),
                end, text(data.path("customer"), "customer_id"), required(data, "product_id"),
                instant(text(data, "previous_billing_date")), end, scheduled ? end : null)
                .withPayerPhone(text(data.path("customer"), "phone_number"));
    }
    @Override public Optional<GatewayInvoice> getLatestInvoice(String id) {
        var items = get("/payments?subscription_id=" + safeId(id) + "&page_size=100").path("items");
        if (!items.isArray()) throw new PaymentGatewayException("Lista de pagamentos Dodo inválida.");
        JsonNode latest = null;
        for (var item : items) if (latest == null || Instant.parse(required(item, "created_at")).isAfter(Instant.parse(required(latest, "created_at")))) latest = item;
        if (latest == null) return Optional.empty();
        var invoice = getInvoice(required(latest, "payment_id"));
        if (!id.equals(invoice.subscriptionId())) throw new PaymentGatewayException("Pagamento pertence a outra assinatura.");
        return Optional.of(invoice);
    }
    @Override public GatewayInvoice getInvoice(String id) {
        var data = get("/payments/" + safeId(id));
        String status = required(data, "status");
        if ("succeeded".equals(status)) status = "paid";
        if ("full".equals(text(data, "refund_status"))) status = "refunded";
        return new GatewayInvoice(required(data, "payment_id"), required(data, "subscription_id"), required(data, "payment_id"), status,
                required(data, "currency"), money(data, "total_amount"));
    }
    @Override public void cancel(String id) { patchSubscription(id, Map.of("cancel_at_next_billing_date", true)); }
    @Override public void cancelImmediately(String id) { patchSubscription(id, Map.of("status", "cancelled")); }
    private void patchSubscription(String id, Object body) {
        request(client.patch().uri(baseUrl + "/subscriptions/" + safeId(id)).contentType(MediaType.APPLICATION_JSON).body(body));
    }
    @Override public void cancelCheckout(String id) {
        // No remote cancellation endpoint: never replace a link that may still accept payment.
        throw new PaymentGatewayException("O checkout Dodo ainda precisa ser conciliado antes de iniciar outra contratação.");
    }
    @Override public GatewayNotification verifyNotification(String body, String signature, String requestId, String timestamp) {
        if (!operational() || body == null || signature == null || requestId == null || timestamp == null)
            throw new PaymentGatewayException("Webhook Dodo incompleto ou não configurado.");
        try {
            verifier.webhooks().unwrap(UnwrapWebhookParams.builder().body(body).secret(properties.webhookKey())
                    .headers(Headers.builder().put("webhook-id", requestId).put("webhook-signature", signature)
                            .put("webhook-timestamp", timestamp).build()).build());
            var payload = mapper.readTree(body); var data = payload.path("data");
            String type = required(payload, "type");
            String resource = type.startsWith("subscription.") ? required(data, "subscription_id")
                    : type.startsWith("payment.") ? required(data, "payment_id") : requestId;
            return new GatewayNotification(provider(), provider() + ":" + environment() + ":" + requestId, type, resource,
                    requestId, environment(), Instant.parse(required(payload, "timestamp")), body);
        } catch (Exception ex) { throw new PaymentGatewayException("Assinatura ou payload do webhook Dodo inválido.", ex); }
    }
    private Optional<PaymentCatalogPrice> price(Store store, PlanType plan) {
        if (store == null || store.getStoreCountryCode() == null || plan == null || !plan.isBillable()) return Optional.empty();
        return catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(provider(), environment(), store.getStoreCountryCode(), plan)
                .filter(PaymentCatalogPrice::isEnabled).filter(PaymentCatalogPrice::isRecurring).filter(PaymentCatalogPrice::isValidated);
    }
    private PaymentCatalogPrice requirePrice(Store store, PlanType plan) {
        return price(store, plan).orElseThrow(() -> new IllegalArgumentException("Produto Dodo indisponível para este plano."));
    }
    private JsonNode get(String path) { return request(client.get().uri(baseUrl + path)); }
    private JsonNode request(RestClient.RequestHeadersSpec<?> request) {
        if (!properties.apiConfigured()) throw new PaymentGatewayException("Configure uma chave Dodo no servidor.");
        try {
            var data = request.header("Authorization", "Bearer " + properties.apiKey()).retrieve().body(JsonNode.class);
            if (data == null) throw new PaymentGatewayException("Resposta Dodo vazia.");
            return data;
        } catch (RestClientResponseException ex) {
            throw new PaymentGatewayException("A API Dodo retornou HTTP " + ex.getStatusCode().value() + ".", ex);
        } catch (RestClientException ex) { throw new PaymentGatewayException("Resposta Dodo indeterminada; concilie antes de tentar novamente.", ex); }
    }
    public static String text(JsonNode node, String key) { var value = node.path(key); return value.isMissingNode() || value.isNull() ? null : value.asText(); }
    public static String required(JsonNode node, String key) { var value = text(node, key); if (value == null || value.isBlank()) throw new PaymentGatewayException("Resposta Dodo sem " + key + "."); return value; }
    private static String safeId(String id) { if (id == null || !id.matches("[A-Za-z0-9_-]{3,120}")) throw new IllegalArgumentException("Código Dodo inválido."); return id; }
    private static BigDecimal money(JsonNode node, String key) { return DodoMarketPricing.fromMinor(new BigDecimal(required(node, key)), required(node, "currency")); }
    private static Instant instant(String value) { return value == null ? null : Instant.parse(value); }
}
