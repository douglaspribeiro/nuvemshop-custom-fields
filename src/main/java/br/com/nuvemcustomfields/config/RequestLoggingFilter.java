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
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = requestId(request);
        long startedAt = System.currentTimeMillis();
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
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
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/assets/") || uri.startsWith("/styles/");
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
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue;
        }
        return UUID.randomUUID().toString();
    }
}
