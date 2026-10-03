package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.repository.UpgradeCouponRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BackofficeUpgradeCouponsTest {
    @Autowired MockMvc mvc;
    @Autowired UpgradeCouponRepository coupons;
    @Test void requiresBackofficeLoginAndSessionToken() throws Exception {
        mvc.perform(get("/backoffice/payments/coupons")).andExpect(status().is3xxRedirection());
        var session=admin();
        mvc.perform(post("/backoffice/payments/coupons").session(session).param("actionToken","invalid")
                .param("code","MVC_FORBIDDEN").param("discountPercent","25").param("environment","PRODUCTION")
                .param("targetPlan","PREMIUM_ULTRA")).andExpect(status().isForbidden());
        assertThat(coupons.findByCode("MVC_FORBIDDEN")).isEmpty();
    }
    @Test void rendersCrudAndCreatesUpdatesAndDeletesCoupon() throws Exception {
        var session=admin();
        var page=mvc.perform(get("/backoffice/payments/coupons").session(session)).andExpect(status().isOk()).andReturn();
        assertThat(page.getResponse().getContentAsString()).contains("Criar cupom","Máximo de utilizações","Utilizações por loja","Últimas 100 utilizações");
        String token=(String)session.getAttribute(BackofficeUpgradeCouponsController.TOKEN_KEY);
        mvc.perform(post("/backoffice/payments/coupons").session(session).param("actionToken",token)
                .param("code","mvc_cupom").param("discountPercent","12.50").param("environment","SANDBOX")
                .param("targetPlan","PREMIUM_ULTRA").param("enabled","true").param("maxUses","10")
                .param("startsAt","2026-10-01T09:30").param("endsAt","2027-10-01T09:30"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("message"));
        var c=coupons.findByCode("MVC_CUPOM").orElseThrow();
        assertThat(c.getDiscountPercent()).isEqualByComparingTo("12.50");assertThat(c.isEnabled()).isTrue();
        assertThat(c.getMaxUses()).isEqualTo(10);assertThat(c.getMaxUsesPerStore()).isEqualTo(1);
        var rendered=mvc.perform(get("/backoffice/payments/coupons").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(rendered).contains("MVC_CUPOM","2026-10-01T09:30","Salvar alterações");
        mvc.perform(post("/backoffice/payments/coupons").session(session).param("actionToken",token)
                .param("id",c.getId().toString()).param("code","MVC_CUPOM").param("discountPercent","15")
                .param("environment","SANDBOX").param("targetPlan","PREMIUM_ULTRA").param("maxUsesPerStore","2"))
                .andExpect(status().is3xxRedirection());
        c=coupons.findByCode("MVC_CUPOM").orElseThrow();assertThat(c.isEnabled()).isFalse();assertThat(c.getMaxUsesPerStore()).isEqualTo(2);
        mvc.perform(post("/backoffice/payments/coupons/{id}/delete",c.getId()).session(session)
                .param("actionToken",token)).andExpect(status().isBadRequest());
        mvc.perform(post("/backoffice/payments/coupons/{id}/delete",c.getId()).session(session)
                .param("actionToken",token).param("confirmation","EXCLUIR")).andExpect(status().is3xxRedirection());
        assertThat(coupons.findByCode("MVC_CUPOM")).isEmpty();
    }
    private MockHttpSession admin(){var s=new MockHttpSession();s.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true);return s;}
}
