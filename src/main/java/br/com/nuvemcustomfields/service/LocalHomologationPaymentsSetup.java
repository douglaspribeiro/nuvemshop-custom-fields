package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.config.LocalHomologationGuard;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.properties.EfiProperties;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import br.com.nuvemcustomfields.repository.PaymentRoutingRuleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Component
@Profile("local-homolog & !prod & !production")
@ConditionalOnProperty(name="local.homologation.enabled",havingValue="true")
public class LocalHomologationPaymentsSetup implements ApplicationRunner {
    private final PaymentCatalogPriceRepository catalog;
    private final PaymentRoutingRuleRepository rules;
    private final EfiProperties efi;
    public LocalHomologationPaymentsSetup(PaymentCatalogPriceRepository catalog,PaymentRoutingRuleRepository rules,EfiProperties efi,LocalHomologationGuard guard){this.catalog=catalog;this.rules=rules;this.efi=efi;}
    @Override @Transactional public void run(ApplicationArguments args){
        for(var plan:List.of(PlanType.PREMIUM,PlanType.PREMIUM_PLUS,PlanType.PREMIUM_ULTRA)){
            var price=catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(PaymentProviderType.EFI,PaymentEnvironment.SANDBOX,"BR",plan).orElse(null);
            if(price==null){
                price=new PaymentCatalogPrice();price.setProvider(PaymentProviderType.EFI);price.setEnvironment(PaymentEnvironment.SANDBOX);price.setCountryCode("BR");price.setPlan(plan);
                price.setCurrency("BRL");price.setAmountValue(efi.amount(plan));price.setTaxMode("internal");price.setRecurring(true);price.setEnabled(false);
            }
            String id=efi.planId(plan);
            if((price.getProviderPriceId()==null || price.getProviderPriceId().isBlank()) && id!=null && id.matches("\\d+")){
                price.setProviderPriceId(id);price.setValidatedAt(Instant.now());price.setEnabled(true);
            }
            catalog.save(price);
        }
        var route=rules.findByCountryCodeIgnoreCase("BR").orElseGet(PaymentRoutingRule::new);
        route.setCountryCode("BR");route.setProvider(PaymentProviderType.EFI);route.setEnvironment(PaymentEnvironment.SANDBOX);
        // O backoffice pode habilitar a rota depois de preencher e salvar os IDs.
        boolean ready=efi.credentialsConfigured() && List.of(PlanType.PREMIUM,PlanType.PREMIUM_PLUS).stream().allMatch(p->
            catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(PaymentProviderType.EFI,PaymentEnvironment.SANDBOX,"BR",p)
                .filter(PaymentCatalogPrice::isEnabled).filter(PaymentCatalogPrice::isValidated).isPresent());
        route.setEnabled(ready);route.setUpdatedBy("local-homologation");rules.save(route);
    }
}
