package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.StoreAdminEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BackofficeStoreEmailPageTest {
    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;
    @Autowired br.com.nuvemcustomfields.repository.PaymentSubscriptionRepository subscriptions;
    @Autowired br.com.nuvemcustomfields.repository.PlanEventRepository planEvents;
    @Autowired br.com.nuvemcustomfields.service.RenewalFailureEmailService renewalEmails;
    @MockitoBean StoreAdminEmailService emails;

    private void assertNoEmailSent() {
        verify(emails,never()).send(any(),any(),any());
        verify(emails,never()).sendAction(any(),any(),any(),any(),any());
        verify(emails,never()).sendActivation(any(),any(),any(),any(),any(),any(),any());
    }

    @Test void rendersRenewalPreviewAndOnlySendsAfterAnAuthenticatedPost() throws Exception {
        var store = new Store(); store.setStoreId(987654322L); store.setStoreName("Loja Dark Hunter");
        store.setStoreEmail("owner@example.com"); store.setStoreCountryCode("BR");
        stores.saveAndFlush(store);
        var sub = new br.com.nuvemcustomfields.entity.PaymentSubscription();
        sub.setStoreId(store.getStoreId()); sub.setProvider(br.com.nuvemcustomfields.entity.PaymentProviderType.EFI);
        sub.setPlan(br.com.nuvemcustomfields.entity.PlanType.PREMIUM_ULTRA); sub.setCountryCode("BR");
        sub.setCurrency("BRL"); sub.setAmountValue(new java.math.BigDecimal("59.90"));
        sub.setExternalReference("renewal-test"); sub.setProviderSubscriptionId("renewal-contract");
        sub.setLastPaymentId("renewal-charge"); sub.setLastPaymentStatus("unpaid");
        sub.setStatus(br.com.nuvemcustomfields.entity.PaymentSubscriptionStatus.ERROR);
        subscriptions.saveAndFlush(sub);
        var event = new br.com.nuvemcustomfields.entity.PlanEvent(); event.setStoreId(store.getStoreId());
        event.setFromPlan(br.com.nuvemcustomfields.entity.PlanType.FREE);
        event.setToPlan(br.com.nuvemcustomfields.entity.PlanType.PREMIUM_PLUS); event.setSource("EFI_CHECKOUT");
        planEvents.saveAndFlush(event);
        String path = "/backoffice/stores/987654322";
        mvc.perform(post(path + "/payment/renewal-failure-email").param("actionToken", "bad").param("fingerprint", "bad"))
            .andExpect(redirectedUrl("/backoffice/login"));
        var session = new MockHttpSession(); session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
        mvc.perform(get(path).session(session)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Enviar aviso de renovação falhou")))
            .andExpect(content().string(containsString("Reativar meu plano Ultra")));
        assertNoEmailSent();
        var notice = renewalEmails.preview(store.getStoreId());
        String token = BackofficeRenewalFailureEmailController.token(session, store.getStoreId(), notice.fingerprint());
        mvc.perform(post(path + "/payment/renewal-failure-email").session(session)
            .param("actionToken", token).param("fingerprint", notice.fingerprint()).param("recipient", "other@example.com"))
            .andExpect(redirectedUrl(path + "#renewal-failure-email")).andExpect(flash().attributeExists("message"));
        verify(emails).sendAction(store.getStoreId(), notice.subject(), notice.body(), notice.actionLabel(), notice.actionUrl());
        mvc.perform(post(path + "/payment/renewal-failure-email").session(session)
            .param("actionToken", token).param("fingerprint", notice.fingerprint())).andExpect(status().isForbidden());
    }

    @Test void rendersFormAndProtectsSendingWithAuthenticationAndToken() throws Exception {
        var store = new Store();
        store.setStoreId(987654321L);
        store.setStoreName("Loja de teste");
        store.setStoreEmail("owner@example.com");
        stores.saveAndFlush(store);
        String path = "/backoffice/stores/987654321";
        mvc.perform(post(path + "/email").param("actionToken", "bad")
                .param("subject", "Aviso").param("body", "Olá"))
                .andExpect(redirectedUrl("/backoffice/login"));
        var session = new MockHttpSession();
        session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
        mvc.perform(get(path).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("owner@example.com")))
                .andExpect(content().string(containsString("name=\"subject\"")))
                .andExpect(model().attributeExists("storeEmailToken"));
        mvc.perform(post(path + "/email").session(session).param("actionToken", "bad")
                .param("subject", "Aviso").param("body", "Olá")).andExpect(status().isForbidden());
        assertNoEmailSent();
        mvc.perform(post(path + "/email").session(session)
                .param("actionToken", BackofficeStoreEmailController.token(session))
                .param("subject", "Aviso").param("body", "Olá")
                .param("recipient", "other@example.com"))
                .andExpect(redirectedUrl(path + "#store-email")).andExpect(flash().attributeExists("message"));
        verify(emails).send(987654321L, "Aviso", "Olá");
    }
}
