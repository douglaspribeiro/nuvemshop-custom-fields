package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.properties.StripeProperties;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
public class StripeGateway implements PaymentGateway {
    // A fixed version keeps invoice.parent and subscription item periods stable.
    public static final String API_VERSION = "2025-06-30.basil";
    public static final String WEBHOOK_API_VERSION = "2026-03-25.dahlia";
    private static final Set<String> WEBHOOK_API_VERSIONS = Set.of(API_VERSION, WEBHOOK_API_VERSION);
    private static final String BASE = "https://api.stripe.com/v1";
    private final StripeProperties properties;
    private final PaymentCatalogPriceRepository catalog;
    private final RestClient client;
    private final ObjectMapper mapper;

    @org.springframework.beans.factory.annotation.Autowired
    public StripeGateway(StripeProperties properties, PaymentCatalogPriceRepository catalog,
                         RestClient.Builder builder, ObjectMapper mapper) {
        this(properties, catalog, timedClient(builder, properties), mapper);
    }
    StripeGateway(StripeProperties properties, PaymentCatalogPriceRepository catalog, RestClient client, ObjectMapper mapper) {
        this.properties = properties; this.catalog = catalog; this.client = client; this.mapper = mapper;
    }
    private static RestClient timedClient(RestClient.Builder builder, StripeProperties p) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(p.safeTimeoutSeconds())).build());
        factory.setReadTimeout(Duration.ofSeconds(p.safeTimeoutSeconds()));
        return builder.requestFactory(factory).build();
    }
    @Override public PaymentProviderType provider() { return PaymentProviderType.STRIPE; }
    @Override public PaymentEnvironment environment() { return properties.environment(); }
    @Override public boolean configured() { return properties.configured(); }
    @Override public boolean operational() { return properties.operational(); }
    public boolean apiConfigured() { return properties.apiConfigured(); }
    public int graceDays() { return properties.safeGraceDays(); }
    @Override public boolean supports(Store store) {
        return configured() && allowed(store) && price(store, PlanType.PREMIUM).isPresent()
                && price(store, PlanType.PREMIUM_PLUS).filter(p -> p.getCurrency().equals(price(store, PlanType.PREMIUM).orElseThrow().getCurrency())).isPresent();
    }
    private boolean allowed(Store store) {
        return store != null && "AR".equalsIgnoreCase(store.getStoreCountryCode()) && properties.allows(store.getStoreId());
    }
    @Override public BigDecimal amount(PlanType plan) { throw new IllegalArgumentException("O preço Stripe depende do catálogo da Argentina."); }
    @Override public BigDecimal amount(Store store, PlanType plan) { return requirePrice(store, plan).getAmountValue(); }
    @Override public String currency(Store store) { return requirePrice(store, PlanType.PREMIUM).getCurrency(); }
    @Override public String priceId(Store store, PlanType plan) { return requirePrice(store, plan).getProviderPriceId(); }
    @Override public boolean planAvailable(Store store, PlanType plan) {
        return supports(store) && plan != null && plan.isBillable()
                && price(store, plan).filter(p -> p.getCurrency().equals(currency(store))).isPresent();
    }
    @Override public void validateCatalog(PaymentCatalogPrice local) { validatedProductId(local); }
    public String validatedProductId(PaymentCatalogPrice local) {
        if (local.getProvider() != provider() || local.getEnvironment() != environment()
                || !"AR".equalsIgnoreCase(local.getCountryCode()) || !local.isRecurring())
            throw new IllegalArgumentException("Catálogo Stripe exige Argentina, assinatura mensal e o ambiente configurado.");
        JsonNode remote = get("/prices/" + id(local.getProviderPriceId(), "price_") + "?expand[]=product");
        if (!remote.path("active").asBoolean() || !remote.path("product").path("active").asBoolean()
                || !Objects.equals(local.getProviderPriceId(), required(remote, "id"))
                || !local.getCurrency().equalsIgnoreCase(required(remote, "currency"))
                || local.getAmountValue().compareTo(money(remote, "unit_amount")) != 0
                || !"recurring".equals(text(remote, "type")) || !"per_unit".equals(text(remote, "billing_scheme"))
                || !"month".equals(text(remote.path("recurring"), "interval"))
                || remote.path("recurring").path("interval_count").asInt() != 1
                || !"licensed".equals(text(remote.path("recurring"), "usage_type"))
                || remote.path("recurring").path("trial_period_days").asInt(0) != 0
                || !Objects.equals(local.getTaxMode(), text(remote, "tax_behavior"))
                || !Set.of("ARS", "USD", "BRL").contains(local.getCurrency()))
            throw new IllegalArgumentException("Preço Stripe divergente: confira moeda, valor, imposto, produto ativo e recorrência mensal fixa sem trial.");
        minor(local.getAmountValue());
        return id(required(remote.path("product"), "id"), "prod_");
    }
    public JsonNode createProductAndPrice(String name, String description, String currency, BigDecimal amount,
                                          PlanType plan, String publicationKey) {
        // The product and its monthly default price share a single idempotent API operation.
        return post("/products", form("name", name, "description", description,
                "metadata[ncf_publication]", publicationKey, "metadata[country_code]", "AR", "metadata[plan]", plan.name(),
                "default_price_data[currency]", currency.toLowerCase(Locale.ROOT),
                "default_price_data[unit_amount]", Long.toString(minor(amount)),
                "default_price_data[recurring][interval]", "month",
                "default_price_data[recurring][interval_count]", "1",
                "default_price_data[tax_behavior]", "inclusive"), "ncf-catalog-" + publicationKey);
    }
    @Override public GatewayCheckout createCheckout(Store store, PlanType plan, String reference, String returnUrl) {
        if (!configured() || !allowed(store)) throw new IllegalArgumentException("Checkout Stripe indisponível para esta loja ou ambiente de teste.");
        if (!planAvailable(store, plan)) throw new IllegalArgumentException("Plano Stripe indisponível para esta loja.");
        PaymentCatalogPrice price = requirePrice(store, plan); validateCatalog(price);
        MultiValueMap<String, String> form = form("mode", "subscription", "line_items[0][price]", price.getProviderPriceId(),
                "line_items[0][quantity]", "1", "payment_method_types[0]", "card", "locale", "es",
                "success_url", returnUrl, "cancel_url", returnUrl, "client_reference_id", reference,
                "expires_at", Long.toString(Instant.now().plusSeconds(1800).getEpochSecond()));
        for (String scope : List.of("metadata", "subscription_data[metadata]")) {
            form.add(scope + "[external_reference]", reference); form.add(scope + "[store_id]", store.getStoreId().toString());
            form.add(scope + "[country_code]", "AR"); form.add(scope + "[plan]", plan.name());
            form.add(scope + "[price_id]", price.getProviderPriceId());
        }
        JsonNode session = post("/checkout/sessions", form, "ncf-checkout-" + reference);
        URI url = URI.create(required(session, "url"));
        if (!"https".equals(url.getScheme()) || !"checkout.stripe.com".equals(url.getHost()))
            throw new PaymentGatewayException("A Stripe retornou um endereço de checkout inválido.");
        return new GatewayCheckout(null, required(session, "id"), url.toString(), required(session, "status"));
    }
    public JsonNode getCheckout(String checkoutId) { return get("/checkout/sessions/" + id(checkoutId, "cs_")); }
    @Override public GatewaySubscription getSubscription(String subscriptionId) {
        JsonNode remote = get("/subscriptions/" + id(subscriptionId, "sub_"));
        JsonNode items = remote.path("items").path("data");
        if (!items.isArray() || items.size() != 1 || items.get(0).path("quantity").asInt() != 1)
            throw new PaymentGatewayException("Assinatura Stripe deve conter um único plano com quantidade 1.");
        JsonNode item = items.get(0), price = item.path("price");
        if (!"month".equals(text(price.path("recurring"), "interval")) || price.path("recurring").path("interval_count").asInt() != 1
                || !"licensed".equals(text(price.path("recurring"), "usage_type")))
            throw new PaymentGatewayException("Recorrência Stripe divergente do plano mensal.");
        Instant start = timestamp(item, "current_period_start"), end = timestamp(item, "current_period_end");
        if (start == null || end == null) throw new PaymentGatewayException("Assinatura Stripe sem período de cobrança.");
        Instant cancellation = remote.path("cancel_at_period_end").asBoolean() ? end : timestamp(remote, "cancel_at");
        if ("canceled".equals(text(remote, "status"))) cancellation = null;
        return new GatewaySubscription(required(remote, "id"), null, required(remote.path("metadata"), "external_reference"),
                required(remote, "status"), required(price, "currency").toUpperCase(Locale.ROOT), money(price, "unit_amount"), end,
                resourceId(remote.path("customer")), required(price, "id"), start, end, cancellation);
    }
    @Override public Optional<GatewayInvoice> getLatestInvoice(String subscriptionId) {
        JsonNode remote = get("/subscriptions/" + id(subscriptionId, "sub_"));
        String invoiceId = resourceId(remote.path("latest_invoice"));
        if (invoiceId == null) return Optional.empty();
        GatewayInvoice invoice = getInvoice(invoiceId);
        if (!subscriptionId.equals(invoice.subscriptionId())) throw new PaymentGatewayException("Fatura Stripe pertence a outra assinatura.");
        return Optional.of(invoice);
    }
    @Override public GatewayInvoice getInvoice(String invoiceId) {
        JsonNode invoice = get("/invoices/" + id(invoiceId, "in_"));
        String subscriptionId = resourceId(invoice.path("parent").path("subscription_details").path("subscription"));
        if (subscriptionId == null) throw new PaymentGatewayException("Fatura Stripe sem assinatura.");
        String status = required(invoice, "status");
        // A paid invoice is authoritative, including zero-amount invoices settled using customer credit.
        if ("paid".equals(status) && invoice.path("amount_remaining").asLong(-1) != 0)
            throw new PaymentGatewayException("Fatura Stripe paga sem confirmação de quitação.");
        if ("paid".equals(status) && invoice.path("amount_paid").asLong() > 0) status = paymentStatus(invoice);
        return new GatewayInvoice(required(invoice, "id"), subscriptionId, required(invoice, "id"), status,
                required(invoice, "currency").toUpperCase(Locale.ROOT), money(invoice, "amount_paid"));
    }
    private String paymentStatus(JsonNode invoice) {
        JsonNode payments = get("/invoice_payments?invoice=" + id(required(invoice, "id"), "in_")
                + "&status=paid&limit=100&expand[]=data.payment.payment_intent.latest_charge");
        if (payments.path("has_more").asBoolean() || !payments.path("data").isArray())
            throw new PaymentGatewayException("Pagamentos Stripe exigem conciliação adicional.");
        long paid = 0, refunded = 0;
        for (JsonNode payment : payments.path("data")) {
            checkMode(payment);
            if (!required(invoice, "id").equals(resourceId(payment.path("invoice"))))
                throw new PaymentGatewayException("Pagamento Stripe de outra fatura.");
            paid = Math.addExact(paid, payment.path("amount_paid").longValue());
            JsonNode charge = payment.path("payment").path("payment_intent").path("latest_charge");
            if (!charge.isObject()) throw new PaymentGatewayException("Pagamento Stripe sem cobrança verificável.");
            checkMode(charge);
            if (charge.path("disputed").asBoolean()) {
                JsonNode disputes = get("/disputes?charge=" + id(required(charge, "id"), "ch_") + "&limit=100");
                if (disputes.path("has_more").asBoolean() || disputes.path("data").isEmpty())
                    throw new PaymentGatewayException("Disputa Stripe exige conciliação adicional.");
                for (JsonNode dispute : disputes.path("data")) {
                    checkMode(dispute);
                    if (!Set.of("won", "warning_closed").contains(required(dispute, "status"))) return "chargedBack";
                }
            }
            refunded = Math.addExact(refunded, charge.path("amount_refunded").asLong());
        }
        if (paid < invoice.path("amount_paid").asLong()) throw new PaymentGatewayException("Fatura Stripe sem pagamentos confirmados suficientes.");
        return refunded >= paid && paid > 0 ? "refunded" : "paid";
    }
    public List<String> invoiceSubscriptionsForCharge(String chargeId) {
        JsonNode charge = get("/charges/" + id(chargeId, "ch_"));
        String intent = resourceId(charge.path("payment_intent"));
        if (intent == null) return List.of();
        JsonNode payments = get("/invoice_payments?payment[type]=payment_intent&payment[payment_intent]="
                + id(intent, "pi_") + "&limit=100");
        if (payments.path("has_more").asBoolean()) throw new PaymentGatewayException("Cobrança Stripe exige conciliação adicional.");
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode payment : payments.path("data")) {
            checkMode(payment); ids.add(getInvoice(required(payment, "invoice")).subscriptionId());
        }
        return List.copyOf(ids);
    }
    public String disputeCharge(String disputeId) {
        return required(get("/disputes/" + id(disputeId, "dp_")), "charge");
    }
    @Override public void cancel(String subscriptionId) {
        post("/subscriptions/" + id(subscriptionId, "sub_"), form("cancel_at_period_end", "true"), "ncf-cancel-" + subscriptionId);
    }
    @Override public void cancelImmediately(String subscriptionId) {
        request(client.delete().uri(BASE + "/subscriptions/" + id(subscriptionId, "sub_")));
    }
    @Override public void cancelCheckout(String checkoutId) {
        JsonNode session = getCheckout(checkoutId);
        if ("expired".equals(text(session, "status"))) return;
        if (!"open".equals(text(session, "status"))) throw new PaymentGatewayException("Checkout Stripe concluído; concilie antes de iniciar outra compra.");
        JsonNode expired = post("/checkout/sessions/" + id(checkoutId, "cs_") + "/expire", form(), "ncf-expire-" + checkoutId);
        if (!"expired".equals(text(expired, "status"))) throw new PaymentGatewayException("Expiração do checkout Stripe ainda não confirmada.");
    }
    @Override public void changeSubscriptionPlan(String subscriptionId, Store store, PlanType plan) {
        // A separate, durable proration flow is required before exposing Stripe upgrades.
        throw new IllegalArgumentException("Para alterar o plano Stripe, entre em contato com o suporte.");
    }
    @Override public GatewayNotification verifyNotification(String body, String signature, String requestId, String dataId) {
        if (!operational()) throw new PaymentGatewayException("Stripe sem credenciais completas.");
        try {
            if (body == null || signature == null || signature.length() > 4096) throw new IllegalArgumentException();
            String timestamp = null; List<String> signatures = new ArrayList<>();
            for (String part : signature.split(",")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length == 2 && "t".equals(pair[0])) { if (timestamp != null) throw new IllegalArgumentException(); timestamp = pair[1]; }
                if (pair.length == 2 && "v1".equals(pair[0])) signatures.add(pair[1]);
            }
            long sentAt = Long.parseLong(Objects.requireNonNull(timestamp));
            long now = Instant.now().getEpochSecond();
            if (sentAt < now - 300 || sentAt > now + 300) throw new IllegalArgumentException();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            boolean valid = false;
            for (String candidate : signatures) if (candidate.matches("[a-fA-F0-9]{64}"))
                valid |= MessageDigest.isEqual(expected, HexFormat.of().parseHex(candidate));
            if (!valid) throw new IllegalArgumentException();
            JsonNode event = mapper.readTree(body); checkMode(event);
            // Snapshot payloads are used only for envelope fields and data.object.id.
            // Financial state is always fetched separately using the pinned REST version.
            if (!WEBHOOK_API_VERSIONS.contains(required(event, "api_version"))) throw new IllegalArgumentException();
            JsonNode object = event.path("data").path("object");
            return new GatewayNotification(provider(), "STRIPE:" + environment() + ":" + required(event, "id"),
                    required(event, "type"), required(object, "id"), null, environment(),
                    Instant.ofEpochSecond(event.path("created").longValue()), body);
        } catch (Exception ex) { throw new PaymentGatewayException("Assinatura ou evento Stripe inválido."); }
    }
    private Optional<PaymentCatalogPrice> price(Store store, PlanType plan) {
        if (store == null || !"AR".equalsIgnoreCase(store.getStoreCountryCode()) || plan == null) return Optional.empty();
        return catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(provider(), environment(), "AR", plan)
                .filter(PaymentCatalogPrice::isEnabled).filter(PaymentCatalogPrice::isValidated).filter(PaymentCatalogPrice::isRecurring);
    }
    private PaymentCatalogPrice requirePrice(Store store, PlanType plan) {
        return price(store, plan).orElseThrow(() -> new IllegalArgumentException("Preço Stripe não validado para este plano da Argentina."));
    }
    private JsonNode get(String path) { return request(client.get().uri(BASE + path)); }
    private JsonNode post(String path, MultiValueMap<String, String> form, String key) {
        var request = client.post().uri(BASE + path).contentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (key != null) request.header("Idempotency-Key", key);
        return request(request.body(form));
    }
    private JsonNode request(RestClient.RequestHeadersSpec<?> request) {
        if (!apiConfigured()) throw new PaymentGatewayException("Configure STRIPE_SECRET_KEY no servidor.");
        try {
            JsonNode response = request.header("Authorization", "Bearer " + properties.secretKey()).header("Stripe-Version", API_VERSION)
                    .retrieve().body(JsonNode.class);
            if (response == null) throw new PaymentGatewayException("A Stripe retornou uma resposta vazia.");
            checkMode(response); return response;
        } catch (RestClientResponseException ex) {
            throw new PaymentGatewayException("A API Stripe retornou HTTP " + ex.getStatusCode().value() + ". Confira a configuração.");
        } catch (RestClientException ex) { throw new PaymentGatewayException("Resposta Stripe não confirmada. Concilie a tentativa antes de iniciar outra."); }
    }
    private void checkMode(JsonNode object) {
        // List envelopes have no livemode; each contained financial resource is checked by its caller.
        if ("list".equals(text(object, "object"))) return;
        if (!object.path("livemode").isBoolean() || object.path("livemode").asBoolean() == properties.sandbox())
            throw new PaymentGatewayException("Resposta Stripe de outro ambiente.");
    }
    public static String text(JsonNode object, String field) {
        JsonNode value = object.path(field); return value.isNull() || value.isMissingNode() ? null : value.asText();
    }
    public static String required(JsonNode object, String field) {
        String value = text(object, field);
        if (value == null || value.isBlank()) throw new PaymentGatewayException("Resposta Stripe sem " + field + ".");
        return value;
    }
    public static String resourceId(JsonNode object) { return object.isTextual() ? object.asText() : text(object, "id"); }
    private static String id(String value, String prefix) {
        if (value == null || !value.startsWith(prefix) || !value.matches("[A-Za-z0-9_]{4,200}")) throw new IllegalArgumentException("ID Stripe inválido.");
        return value;
    }
    private static MultiValueMap<String, String> form(String... values) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        for (int i = 0; i < values.length; i += 2) form.add(values[i], values[i + 1]);
        return form;
    }
    private static BigDecimal money(JsonNode object, String field) { return new BigDecimal(required(object, field)).movePointLeft(2); }
    private static long minor(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Preço Stripe deve ser positivo.");
        return amount.movePointRight(2).longValueExact();
    }
    private static Instant timestamp(JsonNode object, String field) {
        return object.path(field).isIntegralNumber() ? Instant.ofEpochSecond(object.path(field).longValue()) : null;
    }
}
