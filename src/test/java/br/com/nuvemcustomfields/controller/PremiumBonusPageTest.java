package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PaymentSubscription;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PaymentSubscriptionRepository;
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
    @MockitoBean NuvemshopBillingService billing;

    @Test
    void grantsPersistsAndRendersBonusWithoutBillingAndRejectsDuplicate() throws Exception {
        Store store = new Store();
        store.setStoreId(7654321L);
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
    void bonusRequiresBackofficeAuthentication() throws Exception {
        mvc.perform(post("/backoffice/stores/7654321/premium-bonus"))
                .andExpect(redirectedUrl("/backoffice/login"));
    }
}
