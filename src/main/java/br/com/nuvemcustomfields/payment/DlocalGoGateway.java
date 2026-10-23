package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.properties.DlocalGoProperties;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Uses one remote plan per checkout: external_id is only documented on the browser redirect. */
@Component
public class DlocalGoGateway implements PaymentGateway {
    private final DlocalGoProperties properties;
    private final PaymentCatalogPriceRepository catalog;
    private final RestClient client;
    private final ObjectMapper mapper;
    private org.springframework.beans.factory.ObjectProvider<br.com.nuvemcustomfields.service.DlocalGoFinancialService> finances;
    @Autowired
    public void setFinances(org.springframework.beans.factory.ObjectProvider<br.com.nuvemcustomfields.service.DlocalGoFinancialService> value) { finances=value; }

    @Autowired
    public DlocalGoGateway(DlocalGoProperties p, PaymentCatalogPriceRepository catalog, RestClient.Builder builder, ObjectMapper mapper) {
        this(p, catalog, timedClient(builder, p), mapper);
    }
    DlocalGoGateway(DlocalGoProperties p, PaymentCatalogPriceRepository catalog, RestClient client, ObjectMapper mapper) {
        this.properties=p; this.catalog=catalog; this.client=client; this.mapper=mapper;
    }
    private static RestClient timedClient(RestClient.Builder builder, DlocalGoProperties p) {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(p.safeTimeoutSeconds())).build());
        factory.setReadTimeout(Duration.ofSeconds(p.safeTimeoutSeconds()));
        return builder.requestFactory(factory).build();
    }
    public PaymentProviderType provider() { return PaymentProviderType.DLOCAL_GO; }
    public PaymentEnvironment environment() { return properties.environment(); }
    public boolean configured() { return properties.configured(); }
    public boolean operational() { return properties.operational(); }
    public boolean supports(Store store) {
        return configured() && store != null && "AR".equalsIgnoreCase(store.getStoreCountryCode()) && properties.allows(store.getStoreId());
    }
    public BigDecimal amount(PlanType plan) { throw new IllegalArgumentException("O preço dLocal Go depende do catálogo AR."); }
    public BigDecimal amount(Store store, PlanType plan) { return price(store,plan).getAmountValue(); }
    public String currency(Store store) { return "ARS"; }
    public String priceId(Store store, PlanType plan) { return price(store,plan).getProviderPriceId(); }
    public boolean planAvailable(Store store, PlanType plan) {
        return supports(store) && plan != null && plan.isBillable() && catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(
                provider(),environment(),"AR",plan).filter(p -> p.isEnabled() && p.isValidated() && p.isRecurring()
                && "ARS".equals(p.getCurrency()) && p.getAmountValue().signum()>0 && p.getProviderPriceId()!=null).isPresent();
    }
    private PaymentCatalogPrice price(Store store, PlanType plan) {
        if (!planAvailable(store,plan)) throw new IllegalArgumentException("Plano dLocal Go indisponível para esta loja.");
        return catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(provider(),environment(),"AR",plan).orElseThrow();
    }
    public void validateCatalog(PaymentCatalogPrice local) {
        if (local.getProvider()!=provider() || local.getEnvironment()!=environment() || !"AR".equals(local.getCountryCode())
                || !"ARS".equals(local.getCurrency()) || !local.isRecurring()) throw new IllegalArgumentException("dLocal Go exige catálogo mensal AR/ARS no ambiente configurado.");
        JsonNode remote=getPlan(local.getProviderPriceId());
        validatePlan(remote,local.getCurrency(),local.getAmountValue());
        if (!id(local.getProviderPriceId()).equals(required(remote,"id"))) throw new IllegalArgumentException("ID do plano dLocal Go divergente.");
    }
    private void validatePlan(JsonNode remote, String currency, BigDecimal amount) {
        if (!remote.path("active").asBoolean() || !"AR".equals(text(remote,"country"))
                || !currency.equals(text(remote,"currency")) || amount.compareTo(money(remote,"amount"))!=0
                || !"MONTHLY".equals(text(remote,"frequency_type")) || remote.path("frequency_value").asInt(1)!=1
                || remote.path("free_trial_days").asInt(0)!=0 || remote.path("max_periods").asInt(0)>0
                || remote.path("day_of_month").asInt(0)>0)
            throw new IllegalArgumentException("Confira plano dLocal Go ativo, ARS, valor e recorrência mensal sem trial, limite ou dia fixo.");
    }
    public GatewayCheckout createCheckout(Store store, PlanType plan, String reference, String returnUrl) {
        PaymentCatalogPrice price=price(store,plan); validateCatalog(price);
        if (!reference.matches("ncf_[0-9]+_[a-f0-9]{32}")) throw new IllegalArgumentException("Referência inválida.");
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("name",reference); body.put("description","Nuvem Custom Fields - "+plan.getDisplayName());
        body.put("country","AR"); body.put("currency","ARS"); body.put("amount",price.getAmountValue());
        body.put("frequency_type","MONTHLY"); body.put("frequency_value",1);
        String appBase=returnUrl.substring(0,returnUrl.length()-"/admin/billing/return".length());
        body.put("notification_url",appBase+"/webhooks/dlocal-go?reference="+reference);
        body.put("success_url",returnUrl); body.put("back_url",returnUrl); body.put("error_url",returnUrl);
        // No automatic retries: the API does not document an idempotency key for plan creation.
        JsonNode remote=request(HttpMethod.POST,"/v1/subscription/plan",body);
        validatePlan(remote,"ARS",price.getAmountValue());
        if (!reference.equals(required(remote,"name"))) throw new PaymentGatewayException("Referência do plano dLocal Go divergente.");
        URI url=URI.create(required(remote,"subscribe_url"));
        if (!"https".equals(url.getScheme()) || !properties.checkoutHost().equals(url.getHost()) || url.getUserInfo()!=null
                || (url.getPort()!=-1 && url.getPort()!=443) || !url.getPath().startsWith("/validate/subscription/"))
            throw new PaymentGatewayException("Endereço de checkout dLocal Go inválido.");
        return new GatewayCheckout(null,id(required(remote,"id")),url.toString(),"pending");
    }
    public JsonNode getPlan(String plan) { return request(HttpMethod.GET,"/v1/subscription/plan/"+id(plan),null); }
    public Optional<GatewaySubscription> checkoutSubscription(String planId) {
        JsonNode plan=getPlan(planId);
        List<JsonNode> rows=pages("/v1/subscription/plan/"+id(planId)+"/subscription/all");
        if (rows.size()>1) throw new PaymentGatewayException("Mais de um assinante no checkout dLocal Go. Conciliação manual necessária.");
        return rows.stream().findFirst().map(row -> subscription(plan,row));
    }
    public GatewaySubscription getSubscription(String key) {
        String[] ids=parts(key,2);
        return checkoutSubscription(ids[0]).filter(s -> s.id().equals(key))
                .orElseThrow(() -> new PaymentGatewayException("Assinatura dLocal Go não encontrada no plano esperado."));
    }
    private GatewaySubscription subscription(JsonNode plan,JsonNode row) {
        String planId=id(required(plan,"id"));
        if (!planId.equals(required(row.path("plan"),"id")) || !"AR".equals(required(row,"country")))
            throw new PaymentGatewayException("Assinatura dLocal Go divergente do plano ou país.");
        String key=planId+":"+id(required(row,"id"));
        List<JsonNode> executions=executions(key);
        JsonNode latest=executions.stream().max(Comparator.comparing(e -> date(e,"created_at"))).orElse(null);
        JsonNode paid=executions.stream().filter(e -> "COMPLETED".equals(text(e,"status")))
                .max(Comparator.comparing(e -> date(e,"created_at"))).orElse(null);
        Instant start=paid==null?null:date(paid,"created_at");
        if (!plan.path("active").isBoolean() || !row.path("active").isBoolean()
                || !"AR".equals(text(plan,"country")) || !"ARS".equals(text(plan,"currency"))
                || !"MONTHLY".equals(text(plan,"frequency_type")) || plan.path("frequency_value").asInt(1)!=1)
            throw new PaymentGatewayException("Estado ou recorrência dLocal Go inválido.");
        // The paid interval comes from the execution, never from a future retry schedule.
        Instant end=start==null?null:start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        boolean active=plan.path("active").asBoolean() && row.path("active").asBoolean();
        String status=!active?"canceled":("CONFIRMED".equals(text(row,"status"))?"active":"pending");
        if (active && latest!=null && "DECLINED".equals(text(latest,"status"))) status="past_due";
        return new GatewaySubscription(key,planId,required(plan,"name"),status,required(plan,"currency"),money(plan,"amount"),
                end,text(row,"client_id"),null,start,end,!active?end:null);
    }
    public Optional<GatewayInvoice> getLatestInvoice(String key) { return latestExecution(key).map(e -> invoice(key,e)); }
    private Optional<JsonNode> latestExecution(String key) {
        return executions(key).stream().max(Comparator.comparing(e -> date(e,"created_at")));
    }
    private List<JsonNode> executions(String key) {
        String[] ids=parts(key,2);
        return pages("/v1/subscription/plan/"+ids[0]+"/subscription/"+ids[1]+"/execution/all");
    }
    private GatewayInvoice invoice(String key,JsonNode e) {
        String[] ids=parts(key,2);
        if (!ids[1].equals(required(e.path("subscription"),"id"))
                || !ids[0].equals(required(e.path("subscription").path("plan"),"id")))
            throw new PaymentGatewayException("Execução dLocal Go pertence a outra assinatura.");
        String status=switch(required(e,"status")) { case "COMPLETED" -> "paid"; case "DECLINED" -> "failed"; case "PENDING" -> "pending"; default -> "unpaid"; };
        if ("paid".equals(status) && finances!=null)
            status=finances.getObject().paymentStatus(required(e,"order_id"),required(e,"currency"),money(e,"amount_paid"));
        return new GatewayInvoice(key+":"+id(required(e,"id")),key,required(e,"order_id"),status,
                required(e,"currency"),money(e,"amount_paid"));
    }
    public GatewayInvoice getInvoice(String key) {
        String[] ids=parts(key,3);
        String subscriptionKey=ids[0]+":"+ids[1];
        return executions(subscriptionKey).stream().filter(e -> ids[2].equals(text(e,"id")))
                .findFirst().map(e -> invoice(subscriptionKey,e))
                .orElseThrow(() -> new PaymentGatewayException("Cobrança dLocal Go não encontrada."));
    }
    public JsonNode payment(String paymentId) { return request(HttpMethod.GET,"/v1/payments/"+safePaymentId(paymentId),null); }
    public JsonNode paymentForOrder(String order) {
        List<JsonNode> matches=financialPages("/v1/payments?country=AR&size=20").stream()
                .filter(p -> order.equals(text(p,"order_id"))).toList();
        if (matches.size()!=1) throw new PaymentGatewayException("Não foi possível identificar uma única cobrança dLocal Go para este pedido.");
        JsonNode detail=payment(required(matches.getFirst(),"id"));
        if (!order.equals(required(detail,"order_id")) || !"AR".equals(required(detail,"country")))
            throw new PaymentGatewayException("Pagamento dLocal Go divergente do pedido.");
        return detail;
    }
    public JsonNode createAdjustment(String order,BigDecimal amount,String returnUrl,String reference) {
        Map<String,Object> body=new LinkedHashMap<>(); body.put("country","AR");body.put("currency","ARS");
        body.put("amount",amount);body.put("order_id",order);body.put("description","Ajuste proporcional de upgrade");
        body.put("success_url",returnUrl);body.put("back_url",returnUrl);
        body.put("notification_url",returnUrl.substring(0,returnUrl.indexOf("/admin/"))+"/webhooks/dlocal-go?reference="+reference);
        body.put("expiration_type","MINUTES");body.put("expiration_value",15);body.put("payment_type","CREDIT_CARD");
        JsonNode remote=request(HttpMethod.POST,"/v1/payments",body);
        safePaymentId(required(remote,"id"));
        URI url=URI.create(required(remote,"redirect_url"));
        if (!"https".equals(url.getScheme()) || !properties.checkoutHost().equals(url.getHost()) || url.getUserInfo()!=null
                || (url.getPort()!=-1 && url.getPort()!=443)) throw new PaymentGatewayException("Checkout de ajuste inválido.");
        return remote;
    }
    public void updateExclusivePlan(String planId,String reference,BigDecimal before,BigDecimal target) {
        JsonNode plan=getPlan(planId);
        if (!reference.equals(required(plan,"name"))) throw new PaymentGatewayException("Plano exclusivo divergente.");
        if (money(plan,"amount").compareTo(target)==0) { validatePlan(plan,"ARS",target); return; }
        validatePlan(plan,"ARS",before);
        if (pages("/v1/subscription/plan/"+id(planId)+"/subscription/all").size()!=1)
            throw new PaymentGatewayException("O plano de upgrade deve possuir apenas um assinante.");
        request(HttpMethod.PATCH,"/v1/subscription/plan/"+id(planId),Map.of("amount",target));
        JsonNode updated=getPlan(planId); validatePlan(updated,"ARS",target);
        if (!reference.equals(required(updated,"name"))) throw new PaymentGatewayException("Referência do plano alterada.");
    }
    public JsonNode refund(String paymentId,BigDecimal amount,String notificationUrl) {
        return request(HttpMethod.POST,"/v1/refunds",Map.of("payment_id",safePaymentId(paymentId),"amount",amount,"notification_url",notificationUrl));
    }
    public JsonNode refundDetail(String refundId) { return request(HttpMethod.GET,"/v1/refunds/"+safePaymentId(refundId),null); }
    public List<JsonNode> financialPages(String path) {
        List<JsonNode> result=new ArrayList<>();
        for(int page=0;page<100;page++) {
            JsonNode response=request(HttpMethod.GET,path+(path.contains("?")?"&":"?")+"page="+page,null);
            if (!response.path("data").isArray() || !response.path("totalPages").isIntegralNumber()
                    || response.path("totalPages").asInt()<0) throw new PaymentGatewayException("Paginação financeira dLocal Go inválida.");
            response.path("data").forEach(result::add);
            if(page+1>=response.path("totalPages").asInt()) return result;
        }
        throw new PaymentGatewayException("Limite de consulta financeira dLocal Go excedido.");
    }
    private static String safePaymentId(String value) {
        if(value==null || !value.matches("[A-Za-z0-9-]{1,120}")) throw new IllegalArgumentException("Identificador financeiro inválido.");
        return value;
    }
    public void cancel(String key) {
        String[] ids=parts(key,2);
        request(HttpMethod.PATCH,"/v1/subscription/plan/"+ids[0]+"/subscription/"+ids[1]+"/deactivate",null);
        // Dedicated plan must also stop accepting new subscribers through its old checkout link.
        cancelCheckout(ids[0]);
    }
    public void cancelCheckout(String planId) { request(HttpMethod.PATCH,"/v1/subscription/plan/"+id(planId)+"/deactivate",null); }
    public GatewayNotification verifyNotification(String body,String signature,String requestId,String reference) {
        if (!operational() || body==null || body.length()>65536 || signature==null
                || !signature.matches("V2-HMAC-SHA256, Signature: [a-fA-F0-9]{64}")
                || reference==null || !reference.matches("ncf_[0-9]+_[a-f0-9]{32}"))
            throw new IllegalArgumentException("Notificação dLocal Go inválida.");
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.secretKey().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            byte[] expected=mac.doFinal((properties.apiKey()+body).getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expected,HexFormat.of().parseHex(signature.substring(signature.length()-64))))
                throw new IllegalArgumentException("Assinatura dLocal Go inválida.");
            mapper.readTree(body);
            // Notifications have no event ID; do not deduplicate only by payment_id (its status can change).
            return new GatewayNotification(provider(),"dlocal-go:"+environment()+":"+UUID.randomUUID(),"payment.updated",reference,
                    null,environment(),Instant.now(),null);
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalArgumentException("Notificação dLocal Go inválida."); }
    }
    private List<JsonNode> pages(String path) {
        List<JsonNode> result=new ArrayList<>();
        for (int page=1;page<=100;page++) {
            JsonNode response=request(HttpMethod.GET,path+"?page="+page+"&page_size=100",null);
            if (!response.path("data").isArray() || !response.has("total_pages")) throw new PaymentGatewayException("Paginação dLocal Go inválida.");
            response.path("data").forEach(result::add);
            if (page>=response.path("total_pages").asInt()) return result;
        }
        throw new PaymentGatewayException("Limite de páginas dLocal Go excedido; conciliação manual necessária.");
    }
    private JsonNode request(HttpMethod method,String path,Object body) {
        if (!operational()) throw new PaymentGatewayException("Credenciais dLocal Go ausentes.");
        try {
            var request=client.method(method).uri(properties.baseUrl()+path)
                    .header(HttpHeaders.AUTHORIZATION,"Bearer "+properties.apiKey()+":"+properties.secretKey())
                    .contentType(MediaType.APPLICATION_JSON);
            if (body!=null) request.body(body);
            JsonNode response=request.retrieve().body(JsonNode.class);
            if (response==null || response.isNull()) throw new PaymentGatewayException("Resposta dLocal Go vazia.");
            return response;
        } catch (RuntimeException ex) {
            // Provider bodies and URLs can contain credentials or payer details.
            throw new PaymentGatewayException("Não foi possível confirmar a resposta dLocal Go.");
        }
    }
    private static String id(String value) { if (value==null || !value.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("ID dLocal Go inválido."); return value; }
    private static String[] parts(String key,int count) {
        String[] ids=key==null?new String[0]:key.split(":");
        if (ids.length!=count) throw new IllegalArgumentException("Identificador dLocal Go inválido.");
        for (String value:ids) id(value); return ids;
    }
    private static String text(JsonNode n,String field) { return n.path(field).isMissingNode() || n.path(field).isNull()?null:n.path(field).asText(); }
    private static String required(JsonNode n,String field) { String value=text(n,field); if(value==null||value.isBlank()) throw new PaymentGatewayException("Campo dLocal Go ausente: "+field); return value; }
    private static BigDecimal money(JsonNode n,String field) { return new BigDecimal(required(n,field)); }
    private static Instant date(JsonNode n,String field) {
        String value=required(n,field);
        try { return Instant.parse(value); } catch (java.time.format.DateTimeParseException ex) { return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC); }
    }
}
