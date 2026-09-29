package br.com.nuvemcustomfields.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class StorefrontTrafficMetricsFilterTest {

    @Test
    void countsStorefrontConfigurationRequestsWithBoundedTags() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StorefrontTrafficMetricsFilter filter = new StorefrontTrafficMetricsFilter(registry);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/stores/123/personalization");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        assertThat(registry.get("ncf.storefront.http.requests")
                .tags("kind", "storefront_personalization", "method", "GET", "status", "200")
                .counter()
                .count()).isEqualTo(1);
    }

    @Test
    void leavesAdminTrafficOutOfTheStorefrontCounter() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StorefrontTrafficMetricsFilter filter = new StorefrontTrafficMetricsFilter(registry);
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(
                new MockHttpServletRequest("GET", "/admin/dashboard"),
                new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> chainCalled.set(true)
        );

        assertThat(chainCalled).isTrue();
        assertThat(registry.find("ncf.storefront.http.requests").counter()).isNull();
    }
}
