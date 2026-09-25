package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PaymentSubscription;
import br.com.nuvemcustomfields.entity.PersonalizationField;
import br.com.nuvemcustomfields.entity.PersonalizationRule;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PaymentSubscriptionRepository;
import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.NuvemshopBillingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PremiumBonusPageTest {
    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;
    @Autowired PaymentSubscriptionRepository paymentSubscriptions;
    @Autowired PersonalizationRuleRepository rules;
    @Autowired PersonalizationFieldRepository fields;
    @MockitoBean NuvemshopBillingService billing;

    @Test
    void guidesFirstProductUntilFirstFieldIsCreated() throws Exception {
        Store store = new Store();
        store.setStoreId(7654330L);
        store.setAccessToken("test");
        stores.save(store);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, store.getStoreId());

        mvc.perform(get("/admin").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Passo 1 de 2")))
                .andExpect(content().string(containsString("Configurar produto")));

        PersonalizationRule rule = new PersonalizationRule();
        rule.setStoreId(store.getStoreId());
        rule.setProductId(9300L);
        rule.setProductName("Caneca personalizada");
        rules.save(rule);
        mvc.perform(get("/admin").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Passo 2 de 2")))
                .andExpect(content().string(containsString("Criar primeiro campo")))
                .andExpect(content().string(containsString("/admin/products/9300/fields")));
        mvc.perform(get("/admin/products/9300/fields").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Dê um nome ao campo")));

        PersonalizationField field = new PersonalizationField();
        field.setRule(rule);
        field.setLabel("Nome");
        fields.save(field);
        mvc.perform(get("/admin").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Confira na loja")))
                .andExpect(content().string(not(containsString("Passo 2 de 2"))));
    }

    @Test
    void processingPagePollsPendingPaymentAndShowsConfirmedState() throws Exception {
        Store store = new Store();
        store.setStoreId(7654331L);
        store.setAccessToken("test");
        stores.save(store);
        PaymentSubscription subscription = new PaymentSubscription();
        subscription.setStoreId(store.getStoreId());
        subscription.setProvider(PaymentProviderType.MERCADO_PAGO);
        subscription.setExternalReference("store-7654331-premium");
        subscription.setPlan(PlanType.PREMIUM);
        subscription.setCurrency("BRL");
        subscription.setAmountValue(new BigDecimal("19.99"));
        paymentSubscriptions.save(subscription);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, store.getStoreId());

        mvc.perform(get("/admin/billing/processing").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Estamos confirmando seu pagamento")));
        mvc.perform(get("/admin/billing/status").session(session)).andExpect(status().isOk())
                .andExpect(content().json("{\"state\":\"pending\"}"));

        subscription.setStatus(br.com.nuvemcustomfields.entity.PaymentSubscriptionStatus.ACTIVE);
        subscription.setAccessActive(true);
        paymentSubscriptions.saveAndFlush(subscription);
        mvc.perform(get("/admin/billing/status").session(session)).andExpect(status().isOk())
                .andExpect(content().json("{\"state\":\"active\"}"));
    }

    @Test
    void pendingEfiDoesNotLookLikeAnActivePlanOrOfferAnotherPayment() throws Exception {
        Store store = new Store();
        store.setStoreId(7654332L);
        store.setAccessToken("test");
        stores.save(store);
        PaymentSubscription subscription = new PaymentSubscription();
        subscription.setStoreId(store.getStoreId());
        subscription.setProvider(PaymentProviderType.EFI);
        subscription.setExternalReference("store-7654332-premium");
        subscription.setPlan(PlanType.PREMIUM);
        subscription.setCurrency("BRL");
        subscription.setAmountValue(new BigDecimal("19.99"));
        subscription.setNextPaymentAt(Instant.parse("2026-10-24T03:00:00Z"));
        paymentSubscriptions.save(subscription);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, store.getStoreId());

        mvc.perform(get("/admin/billing").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("primeira cobrança ainda não foi confirmada")))
                .andExpect(content().string(containsString("Acompanhar confirmação")))
                .andExpect(content().string(not(containsString("Próxima cobrança:"))))
                .andExpect(content().string(not(containsString("Assinar Essencial"))));
    }

    @Test
    void freePlanStillAllowsEditingExistingFieldAtLimit() throws Exception {
        Store store = new Store();
        store.setStoreId(7654399L);
        store.setAccessToken("test");
        stores.save(store);
        PersonalizationRule rule = new PersonalizationRule();
        rule.setStoreId(store.getStoreId());
        rule.setProductId(9001L);
        rule.setProductName("Caneca");
        rules.save(rule);
        PersonalizationField field = new PersonalizationField();
        field.setRule(rule);
        field.setLabel("Nome");
        fields.save(field);
        rule.getFields().add(field);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, store.getStoreId());
        String path = "/admin/products/9001/fields";
        mvc.perform(get(path).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Editar campos existentes")))
                .andExpect(content().string(containsString("Você pode alterar e salvar os campos atuais normalmente.")))
                .andExpect(content().string(not(containsString("class=\"form preview-source\""))));

        mvc.perform(post(path + "/" + field.getId()).session(session)
                        .param("label", "Nome gravado")
                        .param("fieldType", "TEXT")
                        .param("maxLength", "100")
                        .param("sortOrder", "0"))
                .andExpect(status().isFound());
        assertThat(fields.findById(field.getId()).orElseThrow().getLabel()).isEqualTo("Nome gravado");
    }

    @Test
    void grantsPersistsAndRendersBonusWithoutBillingAndRejectsDuplicate() throws Exception {
        Store store = new Store();
        store.setStoreId(7654321L);
        store.setStoreName("Ateliê Aurora");
        store.setAccessToken("test");
        store.setStoreCountryCode("BR");
        stores.save(store);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
        String path = "/backoffice/stores/7654321";

        mvc.perform(get(path).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Conceder Essencial por 30 dias")))
                .andExpect(content().string(containsString("Conceder Pro por 30 dias")));
        mvc.perform(post(path + "/premium-bonus").param("plan", "PREMIUM").session(session))
                .andExpect(redirectedUrl(path));
        Store saved = stores.findByStoreId(7654321L).orElseThrow();
        assertThat(saved.getEffectivePlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(saved.getPremiumBonusExpiresAt()).isNotNull();
        var originalExpiration = saved.getPremiumBonusExpiresAt();
        mvc.perform(get(path).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Brasilia")))
                .andExpect(content().string(containsString("Trocar para Pro (manter prazo)")))
                .andExpect(content().string(not(containsString("Trocar para Essencial"))));

        mvc.perform(post(path + "/premium-bonus").param("plan", "PREMIUM_PLUS").session(session))
                .andExpect(redirectedUrl(path))
                .andExpect(flash().attribute("message", containsString("data de termino foi mantida")));
        saved = stores.findByStoreId(7654321L).orElseThrow();
        assertThat(saved.getEffectivePlan()).isEqualTo(PlanType.PREMIUM_PLUS);
        assertThat(saved.getPremiumBonusExpiresAt()).isEqualTo(originalExpiration);

        MockHttpSession merchantSession = new MockHttpSession();
        merchantSession.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, 7654321L);
        mvc.perform(get("/admin").session(merchantSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Ateliê Aurora</h1>")))
                .andExpect(content().string(containsString("Ver planos e limites")))
                .andExpect(content().string(containsString("Comece pelo primeiro produto")))
                .andExpect(content().string(containsString("Você está no plano Pro por 30 dias.")))
                .andExpect(content().string(containsString("Restam 30 dias.")));

        saved.setStoreCountryCode("AR");
        stores.saveAndFlush(saved);
        MockHttpSession spanishMerchantSession = new MockHttpSession();
        spanishMerchantSession.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, 7654321L);
        mvc.perform(get("/admin").session(spanishMerchantSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Estás en el plan Pro por 30 días.")))
                .andExpect(content().string(containsString("Quedan 30 días.")));
        verifyNoInteractions(billing);
    }

    @Test
    void rendersBillingWithPendingSubscriptionWithoutAnErrorMessage() throws Exception {
        Store store = new Store();
        store.setStoreId(7654322L);
        store.setAccessToken("test");
        stores.save(store);
        PaymentSubscription subscription = new PaymentSubscription();
        subscription.setStoreId(store.getStoreId());
        subscription.setProvider(PaymentProviderType.MERCADO_PAGO);
        subscription.setExternalReference("store-7654322-premium");
        subscription.setPlan(PlanType.PREMIUM);
        subscription.setCurrency("BRL");
        subscription.setAmountValue(new BigDecimal("19.99"));
        paymentSubscriptions.save(subscription);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, 7654322L);
        mvc.perform(get("/admin/billing").session(session)).andExpect(status().isOk());
        mvc.perform(post("/admin/billing/subscribe").param("plan", "PREMIUM")
                        .session(session))
                .andExpect(redirectedUrl("/admin/billing"));
        verifyNoInteractions(billing);
    }

    @Test
    void rendersCheckoutFailureOnBillingPage() throws Exception {
        Store store = new Store();
        store.setStoreId(7654323L);
        store.setAccessToken("test");
        store.setStoreCountryCode("BR");
        store.setStoreCurrency("BRL");
        store.setStoreEmail("merchant@example.com");
        stores.save(store);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, store.getStoreId());

        mvc.perform(get("/admin/billing").session(session)
                        .flashAttr("error", "Falha ao iniciar o checkout"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Falha ao iniciar o checkout")));
    }

    @Test
    void bonusRequiresBackofficeAuthentication() throws Exception {
        mvc.perform(post("/backoffice/stores/7654321/premium-bonus"))
                .andExpect(redirectedUrl("/backoffice/login"));
    }
}
