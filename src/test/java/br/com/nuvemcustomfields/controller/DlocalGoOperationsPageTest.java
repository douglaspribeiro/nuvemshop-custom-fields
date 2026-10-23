package br.com.nuvemcustomfields.controller;
import br.com.nuvemcustomfields.config.*;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DlocalGoOperationsPageTest {
    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;
    @MockitoBean DlocalGoUpgradeService upgrades;
    @MockitoBean DlocalGoRefundService refunds;
    @Test void rendersProportionalReviewAndRefundConfirmationUsingRealTemplates() throws Exception {
        var store=new Store();store.setStoreId(999142L);store.setAccessToken("test");store.setStoreCountryCode("AR");store.setPlan(PlanType.PREMIUM);stores.saveAndFlush(store);
        var session=new MockHttpSession();session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,999142L);
        var op=new DlocalGoOperation();op.setKind(DlocalGoOperation.Kind.UPGRADE);op.setTargetPlan(PlanType.PREMIUM_PLUS);op.setDueAmount(new BigDecimal("50.00"));
        op.setTargetAmount(new BigDecimal("200.00"));op.setPeriodEnd(Instant.now().plusSeconds(86400));op.setStoreId(999142L);op.setEnvironment(PaymentEnvironment.SANDBOX);
        when(upgrades.quote(999142L,PlanType.PREMIUM_PLUS)).thenReturn(op);
        mvc.perform(get("/admin/billing/upgrade/dlocal-go").session(session).param("plan","PREMIUM_PLUS"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("/admin/billing/upgrade/dlocal-go/pay")))
                .andExpect(content().string(containsString("ARS")));
        var admin=new MockHttpSession();admin.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true);
        when(refunds.recent()).thenReturn(List.of(op));
        mvc.perform(get("/backoffice/payments/dlocal-go/refunds").session(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Revisar reembolso")));
        op.setKind(DlocalGoOperation.Kind.REFUND);op.setInvoiceId("20:3:1");when(refunds.require(op.getId())).thenReturn(op);
        mvc.perform(get("/backoffice/payments/dlocal-go/refunds/"+op.getId()).session(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Confirmar reembolso")));
    }
    @Test void refundRoutesRemainProtectedByBackofficeSession() throws Exception {
        mvc.perform(post("/backoffice/payments/dlocal-go/refunds/unknown/execute"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/backoffice/login"));
        verify(refunds,never()).execute(any());
    }
}
