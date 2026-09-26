package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.PaddleGateway;
import br.com.nuvemcustomfields.payment.PaymentGateway;
import br.com.nuvemcustomfields.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class PaymentConfigurationService {
    private final PaymentRoutingRuleRepository rules;
    private final PaymentRoutingHistoryRepository history;
    private final PaymentCatalogPriceRepository catalog;
    private final PaymentGatewayRouter router;
    private final PaddleGateway paddle;

    public PaymentConfigurationService(PaymentRoutingRuleRepository rules, PaymentRoutingHistoryRepository history,
            PaymentCatalogPriceRepository catalog, PaymentGatewayRouter router, PaddleGateway paddle) {
        this.rules=rules; this.history=history; this.catalog=catalog; this.router=router; this.paddle=paddle;
    }

    public List<PaymentRoutingRule> rules() { return rules.findAll().stream().sorted((a,b)->a.getCountryCode().compareTo(b.getCountryCode())).toList(); }
    public List<PaymentCatalogPrice> catalog() { return catalog.findAllByOrderByCountryCodeAscPlanAsc(); }
    public List<PaymentRoutingHistory> history() { return history.findTop50ByOrderByChangedAtDesc(); }
    public boolean configured(PaymentProviderType provider) { return router.configured(provider); }
    public PaymentEnvironment runningEnvironment(PaymentProviderType provider) { return router.environment(provider); }

    @Transactional
    public void saveRoute(String country, PaymentProviderType provider, PaymentEnvironment environment,
                          boolean enabled, String operator) {
        String code=country(country);
        if (enabled) validateMarket(code, provider, environment);
        PaymentRoutingRule rule=rules.findByCountryCodeIgnoreCase(code).orElseGet(PaymentRoutingRule::new);
        PaymentRoutingHistory change=new PaymentRoutingHistory();
        change.setCountryCode(code); change.setOldProvider(rule.getProvider()); change.setOldEnvironment(rule.getEnvironment());
        change.setOldEnabled(rule.getId()==null?null:rule.isEnabled()); change.setNewProvider(provider);
        change.setNewEnvironment(environment); change.setNewEnabled(enabled); change.setOperatorName(operator);
        rule.setCountryCode(code); rule.setProvider(provider); rule.setEnvironment(environment);
        rule.setEnabled(enabled); rule.setUpdatedBy(operator);
        rules.save(rule); history.save(change);
    }

    @Transactional
    public void savePrice(Long id, String currency, BigDecimal amount, String providerPriceId,
                          boolean enabled) {
        PaymentCatalogPrice price=catalog.findById(id).orElseThrow(() -> new IllegalArgumentException("Preço não encontrado."));
        if (amount==null || amount.signum()<=0) throw new IllegalArgumentException("O valor deve ser positivo.");
        String normalizedCurrency=currency==null?"":currency.trim().toUpperCase(Locale.ROOT);
        if (!normalizedCurrency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Moeda inválida.");
        price.setCurrency(normalizedCurrency); price.setAmountValue(amount);
        price.setProviderPriceId(blank(providerPriceId)?null:providerPriceId.trim()); price.setEnabled(enabled);
        if (enabled && price.getProvider()==PaymentProviderType.PADDLE) paddle.validateCatalog(price);
        catalog.save(price);
    }

    private void validateMarket(String country, PaymentProviderType provider, PaymentEnvironment environment) {
        if (!router.configured(provider)) throw new IllegalArgumentException("O gateway não possui credenciais completas.");
        if (router.environment(provider)!=environment) throw new IllegalArgumentException("O ambiente da regra difere do gateway configurado.");
        List<PaymentCatalogPrice> prices=catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseOrderByPlan(provider,environment,country);
        for (PlanType plan : List.of(PlanType.PREMIUM,PlanType.PREMIUM_PLUS)) {
            PaymentCatalogPrice price=prices.stream().filter(p->p.getPlan()==plan && p.isEnabled() && p.isRecurring()).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("Catálogo incompleto para "+plan.getDisplayName()+"."));
            if (provider==PaymentProviderType.PADDLE) paddle.validateCatalog(price);
        }
    }
    private static String country(String value) {
        String code=value==null?"":value.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{2}")) throw new IllegalArgumentException("País inválido."); return code;
    }
    private static boolean blank(String value){return value==null||value.isBlank();}
}
