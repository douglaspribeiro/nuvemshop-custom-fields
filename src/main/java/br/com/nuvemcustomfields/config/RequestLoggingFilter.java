package br.com.nuvemcustomfields.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = requestId(request);
        long startedAt = System.currentTimeMillis();
        String previousRequestId = MDC.get(REQUEST_ID_MDC_KEY);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            logStart(request, requestId);
            filterChain.doFilter(request, response);
            logEnd(request, response, requestId, startedAt);
        } catch (ServletException | IOException | RuntimeException ex) {
            long durationMs = System.currentTimeMillis() - startedAt;
            LOGGER.error(
                    "request.error request_id={} method={} uri={} status={} duration_ms={} message={}",
                    requestId,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs,
                    ex.getMessage(),
                    ex
            );
            throw ex;
        } finally {
            restorePreviousRequestId(previousRequestId);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return false;
    }

    private void logStart(HttpServletRequest request, String requestId) {
        HttpSession session = request.getSession(false);
        Object storeId = session == null ? null : session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY);
        Object backofficeAuthenticated = session == null ? null : session.getAttribute(BackofficeSessionInterceptor.SESSION_KEY);
        LOGGER.info(
                "request.start request_id={} method={} uri={} remote_addr={} session_id={} store_id={} backoffice_authenticated={}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr(),
                session == null ? null : session.getId(),
                storeId,
                backofficeAuthenticated
        );
    }

    private void logEnd(HttpServletRequest request, HttpServletResponse response, String requestId, long startedAt) {
        long durationMs = System.currentTimeMillis() - startedAt;
        LOGGER.info(
                "request.end request_id={} method={} uri={} status={} duration_ms={}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                durationMs
        );
    }

    private String requestId(HttpServletRequest request) {
        String headerValue = request.getHeader(REQUEST_ID_HEADER);
        if (headerValue != null) {
            try {
                return UUID.fromString(headerValue.strip()).toString();
            } catch (IllegalArgumentException ignored) {
                LOGGER.debug("request.id.invalid_header value={}", headerValue);
            }
        }
        return UUID.randomUUID().toString();
    }

    private void restorePreviousRequestId(String previousRequestId) {
        if (previousRequestId == null) {
            MDC.remove(REQUEST_ID_MDC_KEY);
            return;
        }
        MDC.put(REQUEST_ID_MDC_KEY, previousRequestId);
    }
}
