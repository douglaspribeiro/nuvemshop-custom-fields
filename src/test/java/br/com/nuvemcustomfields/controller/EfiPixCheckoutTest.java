package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"features.allowed=91771:scheduled-pix","payments.efi.open-finance.enabled=true",
        "payments.efi.open-finance.client-id=test","payments.efi.open-finance.client-secret=test",
        "payments.efi.open-finance.certificate=test","payments.efi.open-finance.sandbox=true",
        "payments.efi.open-finance.city-code=1234567","payments.efi.open-finance.beneficiary-key=test"})
@AutoConfigureMockMvc
class EfiPixCheckoutTest {
    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;
    @Autowired PaymentSubscriptionRepository subscriptions;
    @Autowired EfiScheduledPixGateway pix;
    @Autowired org.springframework.jdbc.core.JdbcTemplate db;
    @Autowired PaymentSubscriptionService billing;
    @MockitoBean PaymentGatewayRouter router;
    @MockitoBean EfiGateway card;
    @MockitoBean EfiRecurringPixClient client;

    @Test @Sql("/db/migration/V92__efi_open_finance_recurrences.sql")
    void allowsOnlyReleasedStoreAndActivatesOnConfirmedDebit() throws Exception {
        var amount=new BigDecimal("19.99");
        var session=merchant(91771);
        when(card.provider()).thenReturn(PaymentProviderType.EFI);
        when(card.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(card.supports(any())).thenReturn(true);
        when(card.amount(any(PlanType.class))).thenReturn(amount);
        when(router.forStore(any())).thenReturn(Optional.of(card));
        when(router.require(PaymentProviderType.EFI_PIX)).thenReturn(pix);
        var json=new ObjectMapper();
        UUID bank=UUID.randomUUID();
        var banks=json.createObjectNode();
        banks.putArray("participantes").addObject().put("identificador",bank.toString()).put("nome","Banco de teste");
        when(client.participants()).thenReturn(banks);
        var denied=merchant(91772);
        mvc.perform(get("/admin/billing/pay").param("plan","PREMIUM").session(denied)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Pagar com Pix agendado"))));
        mvc.perform(get("/admin/billing/pay").param("plan","PREMIUM").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Pagar com Pix agendado")));
        mvc.perform(get("/admin/billing/pay/pix").param("plan","PREMIUM").session(denied)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/billing/pay/pix").param("plan","PREMIUM").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Banco de teste")));
        UUID attempt=(UUID)session.getAttribute("efiPixAttempt");
        when(client.start(eq(91771L),eq(attempt),anyString(),eq(bank.toString()),eq(amount),any()))
                .thenReturn(new EfiRecurringPixClient.Consent("urn:efi:test",URI.create("https://bank.example/consent")));
        mvc.perform(post("/admin/billing/pay/pix").session(session).param("plan","PREMIUM").param("token",session.getAttribute("efiPixToken").toString())
                .param("bank",bank.toString()).param("cpf","12345678901").param("amount","19.99"))
                .andExpect(redirectedUrl("https://bank.example/consent"));
        assertThat(subscriptions.findByStoreId(91771L).orElseThrow().isAccessActive()).isFalse();
        var list=json.createArrayNode();
        var contract=list.addObject().put("idProprio",attempt.toString()).put("identificadorPagamento","urn:efi:test").put("valor","19.99").put("status","ativa");
        when(client.list(any(),any())).thenReturn(list);
        assertThat(billing.reconcile(91771L).isAccessActive()).isFalse();
        var provisional = stores.findByStoreId(91771L).orElseThrow();
        assertThat(provisional.getEffectivePlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(provisional.getPlan()).isEqualTo(PlanType.FREE);
        assertThat(provisional.getPixProvisionalUntil()).isEqualTo(LocalDate.now(EfiScheduledPixGateway.ZONE)
                .plusDays(2).atStartOfDay(EfiScheduledPixGateway.ZONE).toInstant());
        db.update("update efi_pix_recurrences set first_debit=? where request_id=?",
                LocalDate.now(EfiScheduledPixGateway.ZONE).minusDays(1),attempt.toString());
        assertThat(billing.reconcile(91771L).getStatus()).isEqualTo(PaymentSubscriptionStatus.PENDING);
        assertThat(stores.findByStoreId(91771L).orElseThrow().getEffectivePlan()).isEqualTo(PlanType.FREE);
        contract.putArray("recorrencias").addObject().put("dataOperacao",LocalDate.now(EfiScheduledPixGateway.ZONE).toString()).put("endToEndId","payment").put("status","aceito");
        assertThat(billing.reconcile(91771L).isAccessActive()).isTrue();
        assertThat(billing.reconcile(91771L).getLastPaymentId()).isEqualTo("payment");
        when(client.cancel("urn:efi:test")).thenAnswer(call -> contract.put("status","cancelada"));
        billing.cancel(91771L);
        verify(client).cancel("urn:efi:test");
        assertThat(billing.reconcile(91771L).getStatus()).isEqualTo(PaymentSubscriptionStatus.CANCELED);
        assertThat(stores.findByStoreId(91771L).orElseThrow().getEffectivePlan()).isEqualTo(PlanType.PREMIUM);
    }
    private MockHttpSession merchant(long id) {
        var store=new Store(); store.setStoreId(id); store.setAccessToken("test"); store.setStoreCountryCode("BR"); store.setStoreCurrency("BRL");
        store.setStoreEmail("pix@example.invalid"); stores.saveAndFlush(store);
        var session=new MockHttpSession(); session.setAttribute("storeId",id); return session;
    }
}
