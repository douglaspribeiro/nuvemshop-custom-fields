package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.entity.UserJourneyAudit;
import br.com.nuvemcustomfields.service.UserJourneyAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class UserJourneyAuditInterceptorTest {
    public void action() { }
    final UserJourneyAuditService audits = mock(UserJourneyAuditService.class);
    final UserJourneyAuditInterceptor interceptor = new UserJourneyAuditInterceptor(audits);
    HandlerMethod handler() throws Exception { return new HandlerMethod(this, getClass().getMethod("action")); }
    MockHttpServletRequest request(String route) {
        var request = new MockHttpServletRequest("POST", route);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, route);
        request.setAttribute(org.springframework.web.servlet.DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE,new org.springframework.web.servlet.FlashMap());
        request.getSession().setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, 12L);
        return request;
    }
    @Test void oauthUsesNewIdentityAndDoesNotPretendToKnowPriorState() throws Exception {
        var request = request("/oauth/callback"); var response = new MockHttpServletResponse();
        when(audits.start(any(), eq(false))).thenReturn(5L);
        interceptor.preHandle(request,response,handler());
        verifyNoInteractions(audits);
        request.getSession().setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, 99L);
        interceptor.afterCompletion(request,response,handler(),null);
        var event = ArgumentCaptor.forClass(UserJourneyAudit.class);
        verify(audits).start(event.capture(),eq(false));
        assertThat(event.getValue().storeId).isEqualTo(99L);
        verify(audits).complete(5L,"COMPLETED",200);
    }
    @Test void unexpectedErrorsAndAccessRedirectsHaveDistinctOutcomes() throws Exception {
        var request = request("/admin"); var response = new MockHttpServletResponse();
        when(audits.start(any(), eq(true))).thenReturn(6L);
        interceptor.preHandle(request,response,handler());
        interceptor.afterCompletion(request,response,handler(),new RuntimeException("secret payload"));
        verify(audits).complete(6L,"ERROR",200);
        var another = request("/admin"); response.sendRedirect("/admin/embedded");
        interceptor.preHandle(another,response,handler()); interceptor.afterCompletion(another,response,handler(),null);
        verify(audits).complete(6L,"ACCESS_REQUIRED",302);
    }
    @Test void auditFailureDoesNotBreakMerchantAction(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        var request = request("/admin"); var response = new MockHttpServletResponse();
        when(audits.start(any(),eq(true))).thenThrow(new IllegalStateException("database down"));
        when(audits.start(any(),eq(false))).thenThrow(new IllegalStateException("database down"));
        assertThat(interceptor.preHandle(request,response,handler())).isTrue();
        assertThatCode(() -> interceptor.afterCompletion(request,response,handler(),null)).doesNotThrowAnyException();
        assertThat(output.getAll()).contains("admin.change", "store_id=12", "status=200")
                .doesNotContain("database down");
    }
    @Test void rejectedTemplatePageIsLoggedWithoutPayload(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        var request=request("/admin/templates/review");var response=new MockHttpServletResponse();
        request.setParameter("sensitive","buyer-secret");
        when(audits.start(any(),eq(true))).thenReturn(9L);
        interceptor.preHandle(request,response,handler());
        var view=new org.springframework.web.servlet.ModelAndView("admin/template-choose");
        view.addObject("templateError","private error details");
        interceptor.postHandle(request,response,handler(),view);
        interceptor.afterCompletion(request,response,handler(),null);
        assertThat(output.getAll()).contains("admin.error", "outcome=REJECTED", "status=200")
                .doesNotContain("buyer-secret", "private error details");
        verify(audits).complete(9L,"REJECTED",200);
    }
    @Test void successfulReadsAreSilentButReadFailuresAreLogged(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        for (String route : new String[]{"/admin/templates", "/admin/images/{id}", "/admin/billing/status", "/admin/buyer-uploads/download-zip"}) {
            var request=request(route);request.setMethod(route.endsWith("download-zip")?"POST":"GET");var response=new MockHttpServletResponse();
            interceptor.preHandle(request,response,handler());interceptor.afterCompletion(request,response,handler(),null);
        }
        assertThat(output.getAll()).doesNotContain("admin.change", "admin.error", "admin.flow");
        var request=request("/admin/images/{id}");request.setMethod("GET");var response=new MockHttpServletResponse();
        response.setStatus(500);
        interceptor.preHandle(request,response,handler());interceptor.afterCompletion(request,response,handler(),new IllegalStateException("private details"));
        assertThat(output.getAll()).contains("admin.error", "outcome=ERROR", "error_type=IllegalStateException")
                .doesNotContain("private details", "admin.change");
    }
    @Test void anonymousVisitsImagesAndPollingDoNotProduceMerchantEvents() throws Exception {
        for (String route : new String[]{"/admin/images/{id}","/admin/billing/status","/admin/help"}) {
            var request=request(route); request.getSession().removeAttribute(AdminSessionInterceptor.STORE_SESSION_KEY);
            var response=new MockHttpServletResponse();
            interceptor.preHandle(request,response,handler()); interceptor.afterCompletion(request,response,handler(),null);
        }
        verifyNoInteractions(audits);
    }
}
