package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.CreemGateway;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class CreemCatalogService {
    private final CreemGateway gateway;
    private final CreemCatalogPublicationRepository publications;
    private final PaymentCatalogPriceRepository catalog;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;

    public CreemCatalogService(CreemGateway gateway, CreemCatalogPublicationRepository publications,
            PaymentCatalogPriceRepository catalog, ObjectMapper mapper, PlatformTransactionManager manager) {
        this.gateway = gateway; this.publications = publications; this.catalog = catalog; this.mapper = mapper;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public List<CreemCatalogPublication> publications() { return publications.findTop50ByOrderByCreatedAtDesc(); }
    public boolean apiConfigured() { return gateway.apiConfigured(); }
    public PaymentEnvironment environment() { return gateway.environment(); }
    public List<PaymentCatalogPrice> prices() {
        return catalog.findAllByOrderByCountryCodeAscPlanAsc().stream().filter(p -> p.getProvider() == PaymentProviderType.CREEM).toList();
    }

    public String create(PlanType plan, PaymentEnvironment environment, String country, String name,
                         String description, String currency, BigDecimal amount, String taxMode, String operator) {
        requireEnvironment(environment);
        String code = country(country);
        Payload payload = validate(plan, name, description, currency, amount, taxMode);
        String json = json(payload);
        String key = digest(environment + ":" + code + ":" + plan + ":" + json);
        CreemCatalogPublication operation;
        try {
            operation = transaction.execute(status -> publications.findByPublicationKey(key).orElseGet(() -> {
                CreemCatalogPublication p = new CreemCatalogPublication();
                p.setPublicationKey(key); p.setReservationKey(digest(environment + ":" + code + ":" + plan)); p.setPayloadJson(json); p.setCountryCode(code); p.setPlan(plan);
                p.setEnvironment(environment); p.setOperatorName(operator);
                return publications.saveAndFlush(p);
            }));
        } catch (DataIntegrityViolationException ex) {
            operation = publications.findByPublicationKey(key).orElseThrow(() -> new IllegalArgumentException("Este país e plano já têm uma publicação pendente. Retome a operação antes de mudar seus dados.", ex));
        }
        return resume(operation.getId());
    }

    public String resume(Long id) {
        CreemCatalogPublication operation = publications.findById(id).orElseThrow();
        requireEnvironment(operation.getEnvironment());
        if ("LINKED".equals(operation.getStatus())) return operation.getProductId();
        Instant now = Instant.now();
        if (publications.claim(id, now, now.plus(5, ChronoUnit.MINUTES)) != 1)
            throw new IllegalArgumentException("Esta publicação já está em processamento. Aguarde e reabra a página.");
        operation = publications.findById(id).orElseThrow();
        try {
            Payload payload = mapper.readValue(operation.getPayloadJson(), Payload.class);
            if (operation.getProductId() == null) {
                if (operation.getCreatedAt().isBefore(now.minus(20, ChronoUnit.HOURS)))
                    throw new IllegalArgumentException("Publicação antiga sem resposta confirmada. Consulte a Creem e vincule o produto existente para evitar duplicação.");
                var remote = gateway.createProduct(payload.name(), payload.description(), payload.currency(),
                        payload.amount(), payload.taxMode(), operation.getPublicationKey());
                operation.setProductId(CreemGateway.required(remote, "id"));
                operation.setStatus("CREATED");
                save(operation); // O ID sobrevive a falhas de validação/vínculo posteriores.
            }
            PaymentCatalogPrice price = price(operation.getPlan(), operation.getEnvironment(), operation.getCountryCode(), payload, operation.getProductId());
            gateway.validateCatalog(price);
            final PaymentCatalogPrice validPrice = price;
            transaction.executeWithoutResult(status -> link(validPrice));
            operation.setStatus("LINKED"); operation.setReservationKey(null); operation.setLastError(null);
            return operation.getProductId();
        } catch (Exception ex) {
            operation.setStatus(operation.getProductId() == null ? "UNKNOWN" : "CREATED");
            operation.setLastError(ex instanceof IllegalArgumentException || ex instanceof br.com.nuvemcustomfields.payment.PaymentGatewayException
                    ? limit(ex.getMessage()) : "Não foi possível salvar o vínculo. Retome esta publicação.");
            throw new IllegalArgumentException(operation.getLastError(), ex);
        } finally {
            operation.setLeaseUntil(null); save(operation);
        }
    }

    public void bind(PlanType plan, PaymentEnvironment environment, String country, String currency,
                     BigDecimal amount, String taxMode, String productId) {
        requireEnvironment(environment);
        Payload payload = validate(plan, "Produto existente", "Produto existente", currency, amount, taxMode);
        PaymentCatalogPrice price = price(plan, environment, country(country), payload, productId == null ? null : productId.trim());
        gateway.validateCatalog(price);
        transaction.executeWithoutResult(status -> {
            publications.findByReservationKey(digest(environment + ":" + price.getCountryCode() + ":" + plan)).ifPresent(operation -> {
                if (operation.getLeaseUntil() != null && operation.getLeaseUntil().isAfter(Instant.now()))
                    throw new IllegalArgumentException("A publicação ainda está em processamento. Aguarde antes de vincular outro produto.");
                operation.setProductId(price.getProviderPriceId()); operation.setStatus("LINKED");
                operation.setReservationKey(null); operation.setLastError(null); publications.saveAndFlush(operation);
            });
            link(price);
        });
    }
    private void link(PaymentCatalogPrice incoming) {
        PaymentCatalogPrice price = catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(
                PaymentProviderType.CREEM, incoming.getEnvironment(), incoming.getCountryCode(), incoming.getPlan()).orElse(incoming);
        price.setProviderPriceId(incoming.getProviderPriceId()); price.setCurrency(incoming.getCurrency());
        price.setAmountValue(incoming.getAmountValue()); price.setTaxMode(incoming.getTaxMode());
        price.setRecurring(true); price.setEnabled(false); price.setValidatedAt(Instant.now()); price.setValidationError(null);
        // Publicação não ativa novas vendas nem substitui o snapshot das assinaturas existentes.
        catalog.saveAndFlush(price);
    }
    private static PaymentCatalogPrice price(PlanType plan, PaymentEnvironment env, String country, Payload payload, String id) {
        PaymentCatalogPrice price = new PaymentCatalogPrice(); price.setProvider(PaymentProviderType.CREEM);
        price.setEnvironment(env); price.setCountryCode(country); price.setPlan(plan); price.setCurrency(payload.currency());
        price.setAmountValue(payload.amount()); price.setTaxMode(payload.taxMode()); price.setProviderPriceId(id);
        price.setRecurring(true); price.setEnabled(false); return price;
    }
    private void save(CreemCatalogPublication operation) { transaction.executeWithoutResult(status -> publications.saveAndFlush(operation)); }
    private void requireEnvironment(PaymentEnvironment environment) {
        if (environment != gateway.environment()) throw new IllegalArgumentException("Selecione o ambiente Creem configurado no servidor.");
        if (!gateway.apiConfigured()) throw new IllegalArgumentException("Configure CREEM_API_KEY no servidor antes de publicar.");
    }
    private static Payload validate(PlanType plan, String name, String description, String currency, BigDecimal amount, String taxMode) {
        if (plan == null || !plan.isBillable()) throw new IllegalArgumentException("Selecione um plano pago.");
        if (name == null || name.isBlank() || name.length() > 120 || description == null || description.isBlank() || description.length() > 500)
            throw new IllegalArgumentException("Informe nome e descrição do produto.");
        currency = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!List.of("USD", "EUR").contains(currency)) throw new IllegalArgumentException("A Creem aceita USD ou EUR.");
        if (!List.of("inclusive", "exclusive").contains(taxMode)) throw new IllegalArgumentException("Selecione o modo de impostos.");
        if (CreemGateway.minor(amount) > 999999999999L) throw new IllegalArgumentException("Preço fora do limite do catálogo.");
        return new Payload(name.trim(), description.trim(), currency, amount.setScale(2), taxMode);
    }
    private static String country(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{2}")) throw new IllegalArgumentException("Informe o país com duas letras."); return code;
    }
    private String json(Payload payload) { try { return mapper.writeValueAsString(payload); } catch (Exception ex) { throw new IllegalArgumentException("Dados inválidos.", ex); } }
    private static String digest(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private static String limit(String value) { return value == null ? "Não foi possível publicar na Creem." : value.substring(0, Math.min(500, value.length())); }
    public record Payload(String name, String description, String currency, BigDecimal amount, String taxMode) { }
}
