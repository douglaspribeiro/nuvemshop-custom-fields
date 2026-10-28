package br.com.nuvemcustomfields.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs before multipart parsing. Never trusts unverified X-Forwarded-For headers. */
@Component @Order(-100)
public class BuyerUploadRateFilter extends OncePerRequestFilter {
    private final Cache<String,AtomicInteger> requests=Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(Duration.ofMinutes(1)).build();
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().matches("/public/stores/\\d+/buyer-uploads(?:/.*)?") || "OPTIONS".equals(request.getMethod());
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if(requests.get(request.getRemoteAddr(),key->new AtomicInteger()).incrementAndGet()>60){
            response.setStatus(429);response.setHeader("Retry-After","60");response.setHeader("Cache-Control","no-store");
            response.setContentType("application/json");response.getWriter().write("{\"error\":\"Muitas tentativas. Aguarde um minuto e tente novamente.\"}");return;
        }
        chain.doFilter(request,response);
    }
}
