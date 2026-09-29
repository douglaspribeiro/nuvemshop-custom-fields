package br.com.nuvemcustomfields.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Conta somente trafego originado pela vitrine/checkout das lojas.
 *
 * Store, produto, URL completa, IP e parametros nao entram como tags: sao valores
 * controlados por uma entrada publica e criariam series ilimitadas no Prometheus.
 */
@Component
public class StorefrontTrafficMetricsFilter extends OncePerRequestFilter {

    private final MeterRegistry meterRegistry;

    public StorefrontTrafficMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return trafficKind(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String kind = trafficKind(request.getRequestURI());
        try {
            filterChain.doFilter(request, response);
        } finally {
            Counter.builder("ncf.storefront.http.requests")
                    .description("HTTP requests made by storefront and checkout scripts")
                    .tag("kind", kind)
                    .tag("method", request.getMethod())
                    .tag("status", Integer.toString(response.getStatus()))
                    .register(meterRegistry)
                    .increment();
        }
    }

    private String trafficKind(String path) {
        return switch (path) {
            case "/assets/nuvemshop-personalizer.js" -> "storefront_legacy_script";
            case "/assets/nuvemshop-storefront-sdk.js" -> "storefront_sdk_script";
            case "/assets/nuvemshop-patagonia.js" -> "storefront_patagonia_script";
            case "/assets/nuvemshop-checkout.js" -> "checkout_sdk_script";
            case "/public/script-events" -> "storefront_script_beacon";
            default -> {
                if (path.matches("/public/stores/[^/]+/personalization")) {
                    yield "storefront_personalization";
                }
                if (path.matches("/public/stores/[^/]+/style")) {
                    yield "checkout_style";
                }
                yield null;
            }
        };
    }
}
