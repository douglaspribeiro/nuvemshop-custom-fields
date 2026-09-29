package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.repository.StoreRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorefrontTrafficMetricsFilterTest {

    @Test
    void countsStorefrontConfigurationRequestsWithBoundedTags() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StorefrontTrafficMetricsFilter filter = new StorefrontTrafficMetricsFilter(registry, mock(StoreRepository.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/stores/123/personalization");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        assertThat(registry.get("ncf.storefront.http.requests")
                .tags("kind", "storefront_personalization", "method", "GET", "status", "200", "store_id", "unknown")
                .counter()
                .count()).isEqualTo(1);
    }

    @Test
    void usesOnlyValidatedStoreIdFromController() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StorefrontTrafficMetricsFilter filter = new StorefrontTrafficMetricsFilter(registry, mock(StoreRepository.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/stores/123/personalization");

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, ignoredResponse) ->
                servletRequest.setAttribute(StorefrontTrafficMetricsFilter.ACTIVE_STORE_ID_ATTRIBUTE, 123L));

        assertThat(registry.get("ncf.storefront.http.requests").tag("store_id", "123").counter().count()).isEqualTo(1);
    }

    @Test
    void validatesLegacyScriptStoreBeforeTagging() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StoreRepository stores = mock(StoreRepository.class);
        when(stores.existsByStoreIdAndUninstalledAtIsNull(123L)).thenReturn(true);
        StorefrontTrafficMetricsFilter filter = new StorefrontTrafficMetricsFilter(registry, stores);
        MockHttpServletRequest valid = new MockHttpServletRequest("GET", "/assets/nuvemshop-personalizer.js");
        valid.setParameter("store", "123");
        MockHttpServletRequest invalid = new MockHttpServletRequest("GET", "/assets/nuvemshop-personalizer.js");
        invalid.setParameter("store", "999");

        filter.doFilter(valid, new MockHttpServletResponse(), (request, response) -> { });
        filter.doFilter(invalid, new MockHttpServletResponse(), (request, response) -> { });

        assertThat(registry.get("ncf.storefront.http.requests").tag("store_id", "123").counter().count()).isEqualTo(1);
        assertThat(registry.get("ncf.storefront.http.requests").tag("store_id", "unknown").counter().count()).isEqualTo(1);
    }

    @Test
    void leavesAdminTrafficOutOfTheStorefrontCounter() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StorefrontTrafficMetricsFilter filter = new StorefrontTrafficMetricsFilter(registry, mock(StoreRepository.class));
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
