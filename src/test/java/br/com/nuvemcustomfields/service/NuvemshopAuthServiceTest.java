package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
                "read_products,write_scripts,read_scripts,billing,read_store",
                "NuvemCustomFields tests"
        );

        NuvemshopAuthService service = new NuvemshopAuthService(
                properties,
                mock(StoreRepository.class),
                mock(WebhookRegistrationService.class),
                mock(IntegrationLogService.class),
                RestClient.builder()
        );

        assertThat(service.buildAuthorizationUrl("csrf-code"))
                .isEqualTo("https://www.tiendanube.com/apps/client-123/authorize?state=csrf-code");
    }
}
