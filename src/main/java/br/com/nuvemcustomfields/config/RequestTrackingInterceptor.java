package br.com.nuvemcustomfields.config;

import jakarta.servlet.http.*;
import org.springframework.validation.BindingResult;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.support.RequestContextUtils;

/** Observes failures without consuming exceptions or changing controller responses. */
public class RequestTrackingInterceptor implements HandlerInterceptor, HandlerExceptionResolver {
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
        if(handler instanceof HandlerMethod method)request.setAttribute(RequestLoggingFilter.ACTION,
                method.getBeanType().getSimpleName()+"."+method.getMethod().getName());
        return true;
    }
    @Override public void postHandle(HttpServletRequest request,HttpServletResponse response,Object handler,ModelAndView mv) {
        if(mv!=null&&(mv.getModel().containsKey("error")||mv.getModel().containsKey("templateError")
                ||mv.getModel().values().stream().anyMatch(v->v instanceof BindingResult b&&b.hasErrors())))
            request.setAttribute(RequestLoggingFilter.REJECTED,true);
        var flash=RequestContextUtils.getOutputFlashMap(request);
        if(flash!=null&&flash.containsKey("error"))request.setAttribute(RequestLoggingFilter.REJECTED,true);
    }
    @Override public ModelAndView resolveException(HttpServletRequest request,HttpServletResponse response,Object handler,Exception ex) {
        request.setAttribute(RequestLoggingFilter.FAILURE,ex);
        return null;
    }
}
