package br.com.nuvemcustomfields.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Request correlation and bounded, payload-free operation tracking at production log levels. */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    static final String FAILURE = RequestLoggingFilter.class.getName()+".failure";
    static final String REJECTED = RequestLoggingFilter.class.getName()+".rejected";
    static final String ACTION = RequestLoggingFilter.class.getName()+".action";
    static final String UNHANDLED = RequestLoggingFilter.class.getName()+".unhandled";
    private static final Set<String> CHANGES=Set.of("POST","PUT","PATCH","DELETE");

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id=requestId(request), previous=MDC.get(REQUEST_ID_MDC_KEY);
        long start=System.nanoTime();var logged=new AtomicBoolean();
        MDC.put(REQUEST_ID_MDC_KEY,id);request.setAttribute(REQUEST_ID_ATTRIBUTE,id);response.setHeader(REQUEST_ID_HEADER,id);
        if(CHANGES.contains(request.getMethod())) LOGGER.info("request.start request_id={} method={} uri={} store_id={}",
                id,request.getMethod(),safeUri(request),storeId(request));
        try {
            chain.doFilter(request,response);
            if(request.isAsyncStarted()) {
                try {request.getAsyncContext().addListener(new AsyncListener() {
                public void onComplete(AsyncEvent event){end(request,response,id,start,logged);}
                public void onError(AsyncEvent event){request.setAttribute(UNHANDLED,true);if(event.getThrowable()!=null)request.setAttribute(FAILURE,event.getThrowable());end(request,response,id,start,logged);}
                public void onTimeout(AsyncEvent event){request.setAttribute(UNHANDLED,true);request.setAttribute(FAILURE,new java.util.concurrent.TimeoutException());end(request,response,id,start,logged);}
                public void onStartAsync(AsyncEvent event){try{event.getAsyncContext().addListener(this);}catch(IllegalStateException completed){end(request,response,id,start,logged);}}
                });} catch(IllegalStateException completed) {end(request,response,id,start,logged);}
            } else end(request,response,id,start,logged);
        } catch(ServletException|IOException|RuntimeException e) {
            request.setAttribute(FAILURE,e);request.setAttribute(UNHANDLED,true);end(request,response,id,start,logged);throw e;
        } finally {
            if(previous==null)MDC.remove(REQUEST_ID_MDC_KEY);else MDC.put(REQUEST_ID_MDC_KEY,previous);
        }
    }
    private void end(HttpServletRequest request,HttpServletResponse response,String id,long start,AtomicBoolean logged) {
        if(!logged.compareAndSet(false,true))return;
        Object failure=request.getAttribute(FAILURE);
        boolean error=Boolean.TRUE.equals(request.getAttribute(UNHANDLED))||response.getStatus()>=500;
        boolean rejected=!error&&(failure!=null||response.getStatus()>=400||Boolean.TRUE.equals(request.getAttribute(REJECTED)));
        if(!error&&!rejected&&!CHANGES.contains(request.getMethod())&&!LOGGER.isDebugEnabled())return;
        String outcome=error?"ERROR":rejected?"REJECTED":response.getStatus()>=300?"REDIRECTED":"COMPLETED";
        Throwable cause=failure instanceof Throwable t?t:null;
        // Exception messages and Throwable arguments can contain credentials, payloads or recipient data.
        var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        while(cause!=null&&cause.getCause()!=null&&seen.add(cause))cause=cause.getCause();
        String location="none";
        if(cause!=null)for(var frame:cause.getStackTrace())if(frame.getClassName().startsWith("br.com.nuvemcustomfields.")){location=frame.toString();break;}
        var log=error?LOGGER.atError():rejected?LOGGER.atWarn():CHANGES.contains(request.getMethod())?LOGGER.atInfo():LOGGER.atDebug();
        log.log("request.{} request_id={} method={} uri={} action={} store_id={} product_id={} field_id={} status={} outcome={} duration_ms={} error_type={} error_code={} error_location={}",
                error?"error":"end",id,request.getMethod(),safeUri(request),Objects.toString(request.getAttribute(ACTION),"none"),
                storeId(request),variable(request,"productId"),variable(request,"fieldId"),response.getStatus(),outcome,
                (System.nanoTime()-start)/1_000_000,cause==null?"none":cause.getClass().getSimpleName(),errorCode(cause),location);
    }
    private static String errorCode(Throwable cause) {
        if(cause instanceof br.com.nuvemcustomfields.service.BuyerUploadLedger.Rejected) {
            String code=cause.getMessage();
            if(code!=null&&code.matches("upload\\.[a-z_.]{1,80}"))return code;
        }
        return "none";
    }
    private static Long storeId(HttpServletRequest request) {
        try {
            var session=request.getSession(false);
            if(session!=null&&session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY) instanceof Long id)return id;
        } catch(IllegalStateException ignored) { /* The operation may have invalidated its session. */ }
        Long id=variable(request,"storeId");return id==null?variable(request,"store"):id;
    }
    private static Long variable(HttpServletRequest request,String name) {
        var variables=request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if(variables instanceof Map<?,?> map)try{return Long.valueOf(Objects.toString(map.get(name),""));}catch(NumberFormatException ignored){}
        return null;
    }
    private String requestId(HttpServletRequest request) {
        String value=request.getHeader(REQUEST_ID_HEADER);
        if(value!=null)try{return UUID.fromString(value.strip()).toString();}catch(IllegalArgumentException ignored){}
        return UUID.randomUUID().toString();
    }
    private static String safeUri(HttpServletRequest request) {
        Object pattern=request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String uri=pattern instanceof String s?s:request.getRequestURI();
        if(uri.startsWith("/winback/"))return "/winback/[link]";
        // Before MVC mapping, redact opaque URL segments (including file IDs and magic-link tokens).
        uri=uri.replaceAll("[\\r\\n\\t]","_").replaceAll("(?<=/)[^/]{32,}","[redacted]");
        return uri.length()>256?uri.substring(0,256):uri;
    }
}
