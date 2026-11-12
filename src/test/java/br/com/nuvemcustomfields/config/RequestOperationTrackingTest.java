package br.com.nuvemcustomfields.config;

import jakarta.servlet.AsyncEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.*;
import org.springframework.mock.web.*;
import org.springframework.web.servlet.*;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class RequestOperationTrackingTest {
    @ParameterizedTest @ValueSource(strings={"POST","PUT","PATCH","DELETE"})
    void changesAreTrackedAtInfoWithoutBodyCookiesOrQueryStrings(String method,CapturedOutput output) throws Exception {
        var request=new MockHttpServletRequest(method,"/backoffice/stores/12/plan");
        request.setQueryString("token=secret-query");request.setContent("secret-body".getBytes());
        request.addHeader("Cookie","session-secret");request.addHeader("X-Request-Id","secret-invalid-id");
        request.getSession().setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,12L);
        var response=new MockHttpServletResponse();
        new RequestLoggingFilter().doFilter(request,response,(req,res)->{
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/backoffice/stores/{storeId}/plan");
            request.setAttribute(RequestLoggingFilter.ACTION,"BackofficeController.override");response.setStatus(302);
        });
        assertThat(output.getOut()).contains("request.start","request.end","method="+method,"store_id=12","outcome=REDIRECTED",response.getHeader("X-Request-Id"))
                .doesNotContain("secret-query","secret-body","session-secret","secret-invalid-id");
    }
    @Test void handledExceptionsAreTrackedWithoutChangingTheResponseOrExposingTheMessage(CapturedOutput output) throws Exception {
        var request=new MockHttpServletRequest("POST","/public/stores/12/buyer-uploads");var response=new MockHttpServletResponse();
        new RequestLoggingFilter().doFilter(request,response,(req,res)->{
            assertThat(new RequestTrackingInterceptor().resolveException(request,response,null,
                    new br.com.nuvemcustomfields.service.BuyerUploadLedger.Rejected("upload.quota.exceeded"))).isNull();
            response.setStatus(400);
        });
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(output.getOut()).contains("outcome=REJECTED","error_type=Rejected","error_code=upload.quota.exceeded");
    }
    @Test void unexpectedFailureHasCorrelationAndTypeWithoutSecretMessageOrStackTrace(CapturedOutput output) {
        var request=new MockHttpServletRequest("GET","/winback/short-secret-token");var response=new MockHttpServletResponse();
        assertThatThrownBy(()->new RequestLoggingFilter().doFilter(request,response,(req,res)->{throw new IOException("private-provider-payload");}))
                .isInstanceOf(IOException.class);
        assertThat(output.getOut()).contains("request.error","outcome=ERROR","error_type=IOException","/winback/[link]")
                .doesNotContain("private-provider-payload","short-secret-token");
    }
    @Test void validationReturnedWithHttp200IsARejection(CapturedOutput output) throws Exception {
        var request=new MockHttpServletRequest("POST","/admin/products/12/fields");var response=new MockHttpServletResponse();
        new RequestLoggingFilter().doFilter(request,response,(req,res)->new RequestTrackingInterceptor().postHandle(request,response,null,
                new ModelAndView("editor","error","private-form-value")));
        assertThat(output.getOut()).contains("status=200","outcome=REJECTED").doesNotContain("private-form-value");
    }
    @Test void asyncFailureIsLoggedOnceWithTheOriginalId(CapturedOutput output) throws Exception {
        var request=new MockHttpServletRequest("GET","/admin/buyer-uploads/file/download");var response=new MockHttpServletResponse();
        request.setAsyncSupported(true);
        new RequestLoggingFilter().doFilter(request,response,(req,res)->request.startAsync(request,response));
        var context=(MockAsyncContext)request.getAsyncContext();
        for(var listener:context.getListeners())listener.onError(new AsyncEvent(context,request,response,new IOException("secret-stream")));
        context.complete();
        assertThat(output.getOut()).contains("request.error","error_type=IOException",response.getHeader("X-Request-Id")).doesNotContain("secret-stream");
        assertThat(output.getOut().split("request.error",-1)).hasSize(2);
    }
    @Test void anAlreadyCompletedAsyncRequestDoesNotFailBecauseOfTracking(CapturedOutput output) {
        var request=new MockHttpServletRequest("POST","/tracking/fast") {
            @Override public boolean isAsyncStarted(){return true;}
            @Override public jakarta.servlet.AsyncContext getAsyncContext(){throw new IllegalStateException("already complete");}
        };
        assertThatCode(()->new RequestLoggingFilter().doFilter(request,new MockHttpServletResponse(),(req,res)->{})).doesNotThrowAnyException();
        assertThat(output.getOut()).contains("outcome=COMPLETED").doesNotContain("request.error");
    }
    @Test void ordinarySuccessfulReadsStayQuiet(CapturedOutput output) throws Exception {
        new RequestLoggingFilter().doFilter(new MockHttpServletRequest("GET","/assets/app.js"),new MockHttpServletResponse(),(req,res)->{});
        assertThat(output.getOut()).doesNotContain("request.start","request.end","request.error");
    }
}
