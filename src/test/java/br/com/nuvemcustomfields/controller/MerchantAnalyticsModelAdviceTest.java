package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;

class MerchantAnalyticsModelAdviceTest {
    final MerchantAnalyticsModelAdvice advice=new MerchantAnalyticsModelAdvice();
    @Test void identifiesTheAuthenticatedStoreWithoutTrustingQueryParameters() {
        var req=new MockHttpServletRequest("GET","/admin/products/55/fields");
        req.getSession().setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,959276L);
        req.setParameter("storeId","8389134"); req.setParameter("email","private@example.com");
        assertThat(advice.context(req)).containsOnly(entry("store_id","959276"),entry("admin_screen","product_fields"));
    }
    @Test void doesNotAttributeAnonymousBootstrapBackofficeOrOperatorTrafficToAMerchant() {
        var req=new MockHttpServletRequest("GET","/admin/billing");
        assertThat(advice.context(req)).isEmpty();
        req.getSession().setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,12L);
        req.setRequestURI("/backoffice/stores/12"); assertThat(advice.context(req)).isEmpty();
        req.setRequestURI("/admin/embedded"); assertThat(advice.context(req)).isEmpty();
        req.setRequestURI("/admin/billing");
        req.getSession().setAttribute(BackofficeSessionInterceptor.STORE_MODE_SESSION_KEY,true);
        assertThat(advice.context(req)).isEmpty(); assertThat(advice.operator(req)).isTrue();
    }
    @Test void theSameBrowserCanSwitchStoreWithoutKeepingThePreviousIdentity() {
        var req=new MockHttpServletRequest("GET","/admin/billing/reactivate");
        req.getSession().setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,12L);
        assertThat(advice.context(req)).containsEntry("store_id","12").containsEntry("admin_screen","reactivation");
        req.getSession().setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,34L);
        assertThat(advice.context(req)).containsEntry("store_id","34");
    }
}
