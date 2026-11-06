package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConfigurationPlanPageTest {
    @Autowired StoreRepository stores;
    @Autowired PersonalizationRuleRepository rules;
    @Autowired PersonalizationFieldRepository fields;
    @Autowired MockMvc mvc;
    @Autowired PaymentSubscriptionRepository subscriptions;
    @Autowired jakarta.persistence.EntityManager em;

    @Test void displaysActualPauseBeforeReconcilerAndClearsWarningAfterPaidRecovery() throws Exception {
        var store=new Store(); store.setStoreId(9900401L); store.setStoreCountryCode("BR"); stores.saveAndFlush(store);
        for(long id=10; id<=20; id+=10) {
            var rule=new PersonalizationRule(); rule.setStoreId(store.getStoreId()); rule.setProductId(id); rules.saveAndFlush(rule);
            var field=new PersonalizationField(); field.setRule(rule); field.setLabel("Nome"); fields.saveAndFlush(field);
        }
        em.clear(); // New HTTP request observes collections loaded from the committed-shaped fixture.
        var session=new MockHttpSession(); session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,store.getStoreId());
        String html=mvc.perform(get("/admin/products/20/fields").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Personalização pausada pelo plano", "Seus campos e pedidos continuam salvos", "Reativar assinatura");
        store.setPlan(PlanType.PREMIUM); stores.saveAndFlush(store);
        em.clear();
        String recovered=mvc.perform(get("/admin/products/20/fields").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(recovered).doesNotContain("Personalização pausada pelo plano");
    }

    private Store paymentStore(PaymentSubscriptionStatus status, boolean renewal) {
        var store=new Store(); store.setStoreId(9900402L); store.setStoreCountryCode("BR"); store.setPlan(PlanType.PREMIUM);
        stores.saveAndFlush(store);
        var sub=new PaymentSubscription(); sub.setStoreId(store.getStoreId()); sub.setProvider(PaymentProviderType.EFI);
        sub.setExternalReference("home-alert-fixture"); sub.setPlan(PlanType.PREMIUM); sub.setCurrency("BRL");
        sub.setAmountValue(new java.math.BigDecimal("19.99")); sub.setStatus(status);
        if (renewal) sub.startRenewalFailure(java.time.Instant.now(), "failed-renewal");
        subscriptions.saveAndFlush(sub); return store;
    }
    private String home(Store store) throws Exception {
        var session=new MockHttpSession(); session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,store.getStoreId());
        return mvc.perform(get("/admin").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    @Test void homeWarnsAboutFailedRenewalWhilePaidGraceIsStillValid() throws Exception {
        var store=paymentStore(PaymentSubscriptionStatus.PAST_DUE,true);
        assertThat(home(store)).contains("A renovação da sua assinatura falhou", "Prazo para regularizar", "durante a carência", "Regularizar assinatura")
                .doesNotContain("Sua assinatura tem uma pendência de pagamento");
    }
    @Test void homeDistinguishesCheckoutFailureFromOverdueRenewal() throws Exception {
        var store=paymentStore(PaymentSubscriptionStatus.ERROR,false);
        assertThat(home(store)).contains("Não foi possível concluir o pagamento")
                .doesNotContain("Sua assinatura tem uma pendência de pagamento", "durante a carência");
        var sub=subscriptions.findByStoreId(store.getStoreId()).orElseThrow();
        sub.startRenewalFailure(java.time.Instant.now().minusSeconds(172800), "failed-renewal");
        subscriptions.saveAndFlush(sub); store.setPlan(PlanType.FREE); stores.saveAndFlush(store);
        assertThat(home(store)).contains("Sua assinatura tem uma pendência de pagamento")
                .doesNotContain("Prazo para regularizar", "durante a carência");
    }
    @Test void homeShowsPausedCountAndContactInstructionsWithoutInventingBillingDebt() throws Exception {
        var store=new Store(); store.setStoreId(9900403L); store.setStoreCountryCode("BR"); stores.saveAndFlush(store);
        var rule=new PersonalizationRule(); rule.setStoreId(store.getStoreId()); rule.setProductId(10L); rules.saveAndFlush(rule);
        for(int i=0;i<2;i++) {
            var field=new PersonalizationField(); field.setRule(rule); field.setLabel("Nome "+i); fields.saveAndFlush(field);
        }
        em.clear();
        assertThat(home(store)).contains("1 configuração", "diretamente à loja após a compra", "Ver configurações")
                .doesNotContain("Sua assinatura tem uma pendência de pagamento");
    }
}
