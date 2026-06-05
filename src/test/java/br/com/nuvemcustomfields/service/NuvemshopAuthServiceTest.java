package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class NuvemshopAuthServiceTest {

    @Test
    void buildsAuthorizationUrlWithRequiredOAuthParameters() {
        NuvemshopProperties properties = new NuvemshopProperties(
                "client-123",
                "secret",
                "https://app.example.com/oauth/callback",
                "https://www.tiendanube.com/apps/{clientId}/authorize",
                "https://www.tiendanube.com/apps/authorize/token",
                "https://api.tiendanube.com",
                "https://app.example.com",
                "read_products,read_orders,write_scripts,read_scripts,billing,read_store",
                "NuvemCustomFields tests",
                "",
                ""
        );

        NuvemshopAuthService service = new NuvemshopAuthService(
                properties,
                mock(StoreRepository.class),
                mock(NuvemshopApiClient.class),
                mock(WebhookRegistrationService.class),
                mock(ScriptInstallService.class),
                mock(IntegrationLogService.class),
                RestClient.builder()
        );

        assertThat(service.buildAuthorizationUrl("csrf-code"))
                .isEqualTo("https://www.tiendanube.com/apps/client-123/authorize?state=csrf-code");
    }

    @Test
    void exchangesCodeUsingFormUrlEncodedPayload() {
        NuvemshopProperties properties = new NuvemshopProperties(
                "client-123",
                "secret",
                "https://app.example.com/oauth/callback",
                "https://www.tiendanube.com/apps/{clientId}/authorize",
                "https://www.tiendanube.com/apps/authorize/token",
                "https://api.tiendanube.com",
                "https://app.example.com",
                "read_products",
                "NuvemCustomFields tests",
                "",
                ""
        );
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        StoreRepository storeRepository = mock(StoreRepository.class);
        WebhookRegistrationService webhookRegistrationService = mock(WebhookRegistrationService.class);
        ScriptInstallService scriptInstallService = mock(ScriptInstallService.class);
        IntegrationLogService integrationLogService = mock(IntegrationLogService.class);
        NuvemshopApiClient apiClient = mock(NuvemshopApiClient.class);

        when(storeRepository.findByStoreId(987L)).thenReturn(Optional.empty());
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(apiClient.getStoreName(any(Store.class))).thenReturn("Loja Teste");

        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("client_id", "client-123");
        expectedForm.add("client_secret", "secret");
        expectedForm.add("grant_type", "authorization_code");
        expectedForm.add("code", "oauth-code");

        server.expect(requestTo("https://www.tiendanube.com/apps/authorize/token"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess(
                        "{\"access_token\":\"token-123\",\"token_type\":\"bearer\",\"scope\":\"read_products\",\"user_id\":987}",
                        MediaType.TEXT_HTML
                ));

        NuvemshopAuthService service = new NuvemshopAuthService(
                properties,
                storeRepository,
                apiClient,
                webhookRegistrationService,
                scriptInstallService,
                integrationLogService,
                builder
        );

        Store store = service.exchangeCodeAndUpsertStore("oauth-code");

        assertThat(store.getStoreId()).isEqualTo(987L);
        assertThat(store.getStoreName()).isEqualTo("Loja Teste");
        assertThat(store.getAccessToken()).isEqualTo("token-123");
        verify(webhookRegistrationService).registerRequiredWebhooks(store);
        verify(scriptInstallService).installPersonalizerScript(store);
        verify(integrationLogService).info(987L, "oauth.installed", "Loja instalada ou reconectada via OAuth.");
        server.verify();
    }
}
