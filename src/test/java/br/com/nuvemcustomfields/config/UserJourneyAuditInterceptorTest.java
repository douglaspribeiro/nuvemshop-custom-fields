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

class UserJourneyAuditInterceptorTest {
    public void action() { }
    final UserJourneyAuditService audits = mock(UserJourneyAuditService.class);
    final UserJourneyAuditInterceptor interceptor = new UserJourneyAuditInterceptor(audits);
    HandlerMethod handler() throws Exception { return new HandlerMethod(this, getClass().getMethod("action")); }
    MockHttpServletRequest request(String route) {
        var request = new MockHttpServletRequest("POST", route);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, route);
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
    @Test void auditFailureDoesNotBreakMerchantAction() throws Exception {
        var request = request("/admin"); var response = new MockHttpServletResponse();
        when(audits.start(any(),eq(true))).thenThrow(new IllegalStateException("database down"));
        when(audits.start(any(),eq(false))).thenThrow(new IllegalStateException("database down"));
        assertThat(interceptor.preHandle(request,response,handler())).isTrue();
        assertThatCode(() -> interceptor.afterCompletion(request,response,handler(),null)).doesNotThrowAnyException();
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
