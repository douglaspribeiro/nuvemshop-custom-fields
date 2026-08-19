package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.ProductSummary;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
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

        server.expect(requestTo("https://api.example.com/v1/123/store?fields=name,country,main_currency"))
                .andExpect(method(GET))
                .andExpect(header("Authentication", "bearer store-token"))
                .andRespond(withSuccess("""
                        {
                          "name": { "es": "Tienda Test" },
                          "country": "AR",
                          "main_currency": "ARS"
                        }
                        """, MediaType.APPLICATION_JSON));

        var profile = client.getStoreProfile(store);

        assertThat(profile.name()).isEqualTo("Tienda Test");
        assertThat(profile.countryCode()).isEqualTo("AR");
        assertThat(profile.currency()).isEqualTo("ARS");
        server.verify();
    }

    @Test
    void listProductsSendsPaginationParamsAndReadsTotalCount() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NuvemshopApiClient client = new NuvemshopApiClient(properties(), builder);

        server.expect(requestTo("https://api.example.com/v1/123/products?page=2&per_page=50&fields=id,name"))
                .andExpect(method(GET))
                .andExpect(header("Authentication", "bearer store-token"))
                .andRespond(withSuccess("""
                        [
                          { "id": 10, "name": { "pt": "Caneca" } },
                          { "id": 11, "name": "Camiseta" }
                        ]
                        """, MediaType.APPLICATION_JSON)
                        .headers(headers("X-Total-Count", "137")));

        var page = client.listProducts(store(), 2, 50, null);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.perPage()).isEqualTo(50);
        assertThat(page.totalCount()).isEqualTo(137L);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.hasPrevious()).isTrue();
        assertThat(page.items()).extracting(ProductSummary::name).containsExactly("Caneca", "Camiseta");
        server.verify();
    }

    @Test
    void listProductsForwardsSearchQueryAndDetectsLastPageFromLinkHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NuvemshopApiClient client = new NuvemshopApiClient(properties(), builder);

        server.expect(requestTo("https://api.example.com/v1/123/products?page=1&per_page=50&fields=id,name&q=caneca%20azul"))
                .andExpect(method(GET))
                .andRespond(withSuccess("[{ \"id\": 10, \"name\": \"Caneca azul\" }]", MediaType.APPLICATION_JSON)
                        .headers(headers("Link", "<https://api.example.com/v1/123/products?page=1>; rel=\"first\"")));

        var page = client.listProducts(store(), 1, 50, "  caneca azul  ");

        assertThat(page.query()).isEqualTo("caneca azul");
        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrevious()).isFalse();
        assertThat(page.totalKnown()).isFalse();
        server.verify();
    }

    @Test
    void listScriptsSweepsEveryPage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NuvemshopApiClient client = new NuvemshopApiClient(properties(), builder);

        server.expect(requestTo("https://api.example.com/v1/123/scripts?page=1&per_page=200"))
                .andExpect(method(GET))
                .andRespond(withSuccess("[{ \"id\": 1 }]", MediaType.APPLICATION_JSON)
                        .headers(headers("Link", "<https://api.example.com/v1/123/scripts?page=2>; rel=\"next\"")));
        server.expect(requestTo("https://api.example.com/v1/123/scripts?page=2&per_page=200"))
                .andExpect(method(GET))
                .andRespond(withSuccess("[{ \"id\": 2 }]", MediaType.APPLICATION_JSON));

        JsonNode scripts = client.listScripts(store());

        assertThat(scripts.isArray()).isTrue();
        assertThat(scripts).hasSize(2);
        assertThat(scripts.get(0).path("id").asInt()).isEqualTo(1);
        assertThat(scripts.get(1).path("id").asInt()).isEqualTo(2);
        server.verify();
    }

    /**
     * Regressao: com o envelope {"result": [...]} a varredura devolvia lista vazia e o
     * chamador reinstalava scripts/webhooks duplicados.
     */
    @Test
    void listScriptsUnwrapsResultEnvelope() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NuvemshopApiClient client = new NuvemshopApiClient(properties(), builder);

        server.expect(requestTo("https://api.example.com/v1/123/scripts?page=1&per_page=200"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{ \"result\": [{ \"id\": 7100 }] }", MediaType.APPLICATION_JSON));

        JsonNode scripts = client.listScripts(store());

        assertThat(scripts).hasSize(1);
        assertThat(scripts.get(0).path("id").asInt()).isEqualTo(7100);
        server.verify();
    }

    private HttpHeaders headers(String name, String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(name, value);
        return headers;
    }

    private Store store() {
        Store store = new Store();
        store.setStoreId(123L);
        store.setAccessToken("store-token");
        return store;
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
                "",
                ""
        );
    }
}
