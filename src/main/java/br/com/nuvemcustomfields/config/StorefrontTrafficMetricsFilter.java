package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.repository.StoreRepository;
import org.springframework.dao.DataAccessException;
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
 * Apenas IDs de lojas ativas entram como tag: valores recebidos em rotas publicas
 * nao podem criar series arbitrarias no Prometheus.
 */
@Component
public class StorefrontTrafficMetricsFilter extends OncePerRequestFilter {

    public static final String ACTIVE_STORE_ID_ATTRIBUTE = StorefrontTrafficMetricsFilter.class.getName() + ".activeStoreId";
    public static final String PERSONALIZED_PRODUCT_ID_ATTRIBUTE = StorefrontTrafficMetricsFilter.class.getName() + ".personalizedProductId";
    public static final String PRODUCT_REQUEST_METRIC = "ncf.storefront.personalized.product.requests";

    private final MeterRegistry meterRegistry;
    private final StoreRepository storeRepository;

    public StorefrontTrafficMetricsFilter(MeterRegistry meterRegistry, StoreRepository storeRepository) {
        this.meterRegistry = meterRegistry;
        this.storeRepository = storeRepository;
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
            String storeId = activeStoreId(request);
            Counter.builder("ncf.storefront.http.requests")
                    .description("HTTP requests made by storefront and checkout scripts")
                    .tag("kind", kind)
                    .tag("method", request.getMethod())
                    .tag("status", Integer.toString(response.getStatus()))
                    .tag("store_id", storeId)
                    .register(meterRegistry)
                    .increment();
            Object product = request.getAttribute(PERSONALIZED_PRODUCT_ID_ATTRIBUTE);
            if ("storefront_personalization".equals(kind) && "GET".equals(request.getMethod())
                    && response.getStatus() >= 200 && response.getStatus() < 300
                    && request.getAttribute(ACTIVE_STORE_ID_ATTRIBUTE) instanceof Long id
                    && product instanceof Long productId) {
                Counter.builder(PRODUCT_REQUEST_METRIC)
                        .description("Successful configuration reads of enabled products with personalization fields; not unique visitors")
                        .tag("store_id", id.toString()).tag("product_id", productId.toString())
                        .register(meterRegistry).increment();
            }
        }
    }

    private String activeStoreId(HttpServletRequest request) {
        Object validated = request.getAttribute(ACTIVE_STORE_ID_ATTRIBUTE);
        if (validated instanceof Long id) {
            return id.toString();
        }
        // O download do script legado nao passa por controller e traz a loja na query.
        if (!"/assets/nuvemshop-personalizer.js".equals(request.getRequestURI())) {
            return "unknown";
        }
        try {
            long id = Long.parseLong(request.getParameter("store"));
            return id > 0 && storeRepository.existsByStoreIdAndUninstalledAtIsNull(id)
                    ? Long.toString(id) : "unknown";
        } catch (NumberFormatException | DataAccessException ignored) {
            return "unknown";
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
