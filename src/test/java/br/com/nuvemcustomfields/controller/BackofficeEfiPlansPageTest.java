package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.PaymentEnvironment;
import br.com.nuvemcustomfields.payment.EfiGateway;
import br.com.nuvemcustomfields.payment.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BackofficeEfiPlansPageTest {
    @Autowired MockMvc mvc;
    @MockitoSpyBean EfiGateway efi;

    private MockHttpSession session() {
        var session = new MockHttpSession();
        session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
        return session;
    }

    @Test void requiresBackofficeAuthentication() throws Exception {
        mvc.perform(get("/backoffice/payments/efi/plans")).andExpect(redirectedUrl("/backoffice/login"));
        verify(efi, never()).listPlans(anyInt());
    }

    @Test void rendersRemoteIdEnvironmentAndPagination() throws Exception {
        doReturn(PaymentEnvironment.PRODUCTION).when(efi).environment();
        doReturn(Collections.nCopies(50, new EfiGateway.EfiPlan("72053", "Ultra", 1, null, "2026-10-02"))).when(efi).listPlans(50);
        String html = mvc.perform(get("/backoffice/payments/efi/plans").param("offset", "50").session(session()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("72053", "Ultra", "PRODUCTION", "Ilimitadas", "offset=100", "offset=0");
    }

    @Test void rendersEmptyResultsAndSafeProviderErrors() throws Exception {
        doReturn(PaymentEnvironment.SANDBOX).when(efi).environment();
        doReturn(List.of()).when(efi).listPlans(0);
        String empty = mvc.perform(get("/backoffice/payments/efi/plans").session(session()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(empty).contains("Nenhum plano retornado", "SANDBOX");
        doThrow(new PaymentGatewayException("sensitive-provider-response")).when(efi).listPlans(0);
        String error = mvc.perform(get("/backoffice/payments/efi/plans").session(session()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(error).contains("Não foi possível consultar os planos").doesNotContain("sensitive-provider-response");
    }
}
