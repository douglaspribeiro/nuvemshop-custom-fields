package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NuvemshopApiClientTest {

    @Test
    void readsStoreProfileCountryAndCurrency() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NuvemshopApiClient client = new NuvemshopApiClient(properties(), builder);
        Store store = new Store();
        store.setStoreId(123L);
        store.setAccessToken("store-token");

        server.expect(requestTo("https://api.example.com/v1/123/store?fields=name,country,country_code,main_currency,currency,locale"))
                .andExpect(method(GET))
                .andExpect(header("Authentication", "bearer store-token"))
                .andRespond(withSuccess("""
                        {
                          "name": { "es": "Tienda Test" },
                          "country": { "code": "AR" },
                          "main_currency": "ARS"
                        }
                        """, MediaType.APPLICATION_JSON));

        var profile = client.getStoreProfile(store);

        assertThat(profile.name()).isEqualTo("Tienda Test");
        assertThat(profile.countryCode()).isEqualTo("AR");
        assertThat(profile.currency()).isEqualTo("ARS");
        server.verify();
    }

    private NuvemshopProperties properties() {
        return new NuvemshopProperties(
                "client-123",
                "secret",
                "https://app.example.com/oauth/callback",
                "https://example.com/{clientId}/authorize",
                "https://example.com/token",
                "https://api.example.com",
                "https://app.example.com",
                "billing",
                "tests",
                "",
                ""
        );
    }
}
