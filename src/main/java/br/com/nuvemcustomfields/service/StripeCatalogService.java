package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.StripeGateway;
import br.com.nuvemcustomfields.payment.PaymentGatewayException;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class StripeCatalogService {
    private final StripeGateway gateway;
    private final StripeCatalogPublicationRepository publications;
    private final PaymentCatalogPriceRepository catalog;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    public StripeCatalogService(StripeGateway gateway, StripeCatalogPublicationRepository publications,
            PaymentCatalogPriceRepository catalog, ObjectMapper mapper, PlatformTransactionManager manager) {
        this.gateway = gateway; this.publications = publications; this.catalog = catalog; this.mapper = mapper;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public boolean apiConfigured() { return gateway.apiConfigured(); }
    public PaymentEnvironment environment() { return gateway.environment(); }
    public List<StripeCatalogPublication> publications() { return publications.findTop50ByOrderByCreatedAtDesc(); }
    public List<PaymentCatalogPrice> prices() {
        return catalog.findAllByOrderByCountryCodeAscPlanAsc().stream().filter(p -> p.getProvider() == PaymentProviderType.STRIPE).toList();
    }
    public String create(PlanType plan, PaymentEnvironment environment, String name, String description,
                         String currency, BigDecimal amount, String operator) {
        requireEnvironment(environment);
        Payload payload = validate(plan, name, description, currency, amount);
        String json = json(payload);
        String key = digest(environment + ":AR:" + plan + ":" + json);
        StripeCatalogPublication operation;
        try {
            operation = transaction.execute(status -> publications.findByPublicationKey(key).orElseGet(() -> {
                var p = new StripeCatalogPublication(); p.setPublicationKey(key);
                p.setReservationKey(reservation(environment, plan)); p.setPayloadJson(json);
                p.setCountryCode("AR"); p.setPlan(plan); p.setEnvironment(environment); p.setOperatorName(operator);
                return publications.saveAndFlush(p);
            }));
        } catch (DataIntegrityViolationException ex) {
            operation = publications.findByPublicationKey(key).orElseThrow(() ->
                    new IllegalArgumentException("Este plano da Argentina já tem uma publicação pendente. Retome antes de alterar os dados.", ex));
        }
        return resume(operation.getId());
    }
    public String resume(Long id) {
        StripeCatalogPublication operation = publications.findById(id).orElseThrow();
        requireEnvironment(operation.getEnvironment());
        if ("LINKED".equals(operation.getStatus())) return operation.getPriceId();
        Instant now = Instant.now();
        if (publications.claim(id, now, now.plus(5, ChronoUnit.MINUTES)) != 1)
            throw new IllegalArgumentException("Esta publicação já está em processamento. Aguarde e reabra a página.");
        operation = publications.findById(id).orElseThrow();
        try {
            Payload payload = mapper.readValue(operation.getPayloadJson(), Payload.class);
            if (operation.getPriceId() == null) {
                // Stripe retains v1 idempotency keys for at least 24h; never replay beyond that window.
                if (operation.getCreatedAt().isBefore(now.minus(20, ChronoUnit.HOURS)))
                    throw new IllegalArgumentException("Publicação antiga sem resposta confirmada. Consulte a Stripe e vincule o preço existente para evitar duplicação.");
                var remote = gateway.createProductAndPrice(payload.name(), payload.description(), payload.currency(),
                        payload.amount(), operation.getPlan(), operation.getPublicationKey());
                operation.setProductId(StripeGateway.required(remote, "id"));
                String priceId = StripeGateway.resourceId(remote.path("default_price"));
                if (priceId == null || !priceId.matches("price_[A-Za-z0-9_]+"))
                    throw new PaymentGatewayException("A Stripe não confirmou o preço mensal. Retome esta publicação.");
                operation.setPriceId(priceId); operation.setStatus("CREATED");
                save(operation); // Both remote IDs survive a later validation or database failure.
            }
            PaymentCatalogPrice price = price(operation.getPlan(), operation.getEnvironment(), payload, operation.getPriceId());
            String productId = gateway.validatedProductId(price);
            if (!Objects.equals(operation.getProductId(), productId))
                throw new IllegalArgumentException("O preço Stripe pertence a outro produto.");
            StripeCatalogPublication completed = operation;
            transaction.executeWithoutResult(status -> {
                link(price); completed.setStatus("LINKED"); completed.setReservationKey(null);
                completed.setLastError(null); completed.setLeaseUntil(null); publications.saveAndFlush(completed);
            });
            return operation.getPriceId();
        } catch (Exception ex) {
            operation.setStatus(operation.getPriceId() == null ? "UNKNOWN" : "CREATED");
            operation.setLastError(ex instanceof IllegalArgumentException || ex instanceof PaymentGatewayException
                    ? limit(ex.getMessage()) : "Não foi possível salvar o vínculo Stripe. Retome esta publicação.");
            throw new IllegalArgumentException(operation.getLastError(), ex);
        } finally {
            operation.setLeaseUntil(null); save(operation);
        }
    }
    public void bind(PlanType plan, PaymentEnvironment environment, String currency, BigDecimal amount, String priceId) {
        requireEnvironment(environment);
        Payload payload = validate(plan, "Produto existente", "Produto existente", currency, amount);
        PaymentCatalogPrice price = price(plan, environment, payload, priceId == null ? null : priceId.trim());
        String productId = gateway.validatedProductId(price);
        transaction.executeWithoutResult(status -> {
            publications.findByReservationKey(reservation(environment, plan)).ifPresent(operation -> {
                if (operation.getLeaseUntil() != null && operation.getLeaseUntil().isAfter(Instant.now()))
                    throw new IllegalArgumentException("A publicação ainda está em processamento. Aguarde antes de vincular outro preço.");
                operation.setProductId(productId); operation.setPriceId(price.getProviderPriceId());
                operation.setStatus("LINKED"); operation.setReservationKey(null); operation.setLastError(null);
                publications.saveAndFlush(operation);
            });
            link(price);
        });
    }
    private void link(PaymentCatalogPrice incoming) {
        PaymentCatalogPrice local = catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(
                PaymentProviderType.STRIPE, incoming.getEnvironment(), "AR", incoming.getPlan()).orElse(incoming);
        local.setProviderPriceId(incoming.getProviderPriceId()); local.setCurrency(incoming.getCurrency());
        local.setAmountValue(incoming.getAmountValue()); local.setTaxMode("inclusive"); local.setRecurring(true);
        local.setEnabled(false); local.setValidatedAt(Instant.now()); local.setValidationError(null);
        catalog.saveAndFlush(local);
    }
    private static PaymentCatalogPrice price(PlanType plan, PaymentEnvironment environment, Payload payload, String priceId) {
        var price = new PaymentCatalogPrice(); price.setProvider(PaymentProviderType.STRIPE); price.setEnvironment(environment);
        price.setCountryCode("AR"); price.setPlan(plan); price.setCurrency(payload.currency()); price.setAmountValue(payload.amount());
        price.setTaxMode("inclusive"); price.setRecurring(true); price.setEnabled(false); price.setProviderPriceId(priceId); return price;
    }
    private void requireEnvironment(PaymentEnvironment environment) {
        if (environment != gateway.environment()) throw new IllegalArgumentException("Selecione o ambiente Stripe configurado no servidor.");
        if (!gateway.apiConfigured()) throw new IllegalArgumentException("Configure STRIPE_SECRET_KEY no servidor antes de criar produtos.");
    }
    private static Payload validate(PlanType plan, String name, String description, String currency, BigDecimal amount) {
        if (plan == null || !plan.isBillable()) throw new IllegalArgumentException("Selecione um plano pago.");
        if (name == null || name.isBlank() || name.length() > 120 || description == null || description.isBlank() || description.length() > 500)
            throw new IllegalArgumentException("Informe nome e descrição do produto.");
        currency = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ARS", "USD", "BRL").contains(currency)) throw new IllegalArgumentException("Selecione ARS, USD ou BRL.");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Informe um preço mensal positivo.");
        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (amount.movePointRight(2).longValueExact() > 99999999L) throw new ArithmeticException();
        } catch (ArithmeticException ex) { throw new IllegalArgumentException("Preço Stripe inválido: use até duas casas decimais e no máximo 999.999,99."); }
        return new Payload(name.trim(), description.trim(), currency, amount);
    }
    private void save(StripeCatalogPublication operation) { transaction.executeWithoutResult(status -> publications.saveAndFlush(operation)); }
    private static String reservation(PaymentEnvironment environment, PlanType plan) { return digest(environment + ":AR:" + plan); }
    private String json(Payload payload) { try { return mapper.writeValueAsString(payload); } catch (Exception ex) { throw new IllegalArgumentException("Dados inválidos.", ex); } }
    private static String digest(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private static String limit(String value) { return value == null ? "Não foi possível criar o produto Stripe." : value.substring(0, Math.min(500, value.length())); }
    public record Payload(String name, String description, String currency, BigDecimal amount) { }
}
