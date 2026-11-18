package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.properties.FeatureAccessProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

class ProductComplementsAccessInterceptorTest {
    @Test void permitsOnlyStoresWithTheProductComplementsFeature() throws Exception {
        var interceptor = new ProductComplementsAccessInterceptor(new FeatureAccessProperties("42:product-complements"));

        assertThat(interceptor.preHandle(request(42L), new MockHttpServletResponse(), new Object())).isTrue();
        var denied = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request(99L), denied, new Object())).isFalse();
        assertThat(denied.getStatus()).isEqualTo(403);
    }

    private MockHttpServletRequest request(long storeId) {
        var request = new MockHttpServletRequest();
        var session = new MockHttpSession();
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, storeId);
        request.setSession(session);
        return request;
    }
}
