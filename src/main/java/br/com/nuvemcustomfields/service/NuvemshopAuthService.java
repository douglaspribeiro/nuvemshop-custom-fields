package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.NuvemshopTokenResponse;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Service
public class NuvemshopAuthService {

    private final NuvemshopProperties properties;
    private final StoreRepository storeRepository;
    private final WebhookRegistrationService webhookRegistrationService;
    private final RestClient restClient;

    public NuvemshopAuthService(
            NuvemshopProperties properties,
            StoreRepository storeRepository,
            WebhookRegistrationService webhookRegistrationService,
            RestClient.Builder builder
    ) {
        this.properties = properties;
        this.storeRepository = storeRepository;
        this.webhookRegistrationService = webhookRegistrationService;
        this.restClient = builder.defaultHeader("User-Agent", properties.userAgent()).build();
    }

    public String buildAuthorizationUrl(String state) {
        String authorizeUrl = properties.authUrl().replace("{clientId}", properties.clientId());
        return UriComponentsBuilder.fromUriString(authorizeUrl)
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    @Transactional
    public Store exchangeCodeAndUpsertStore(String code) {
        Map<String, String> payload = Map.of(
                "client_id", properties.clientId(),
                "client_secret", properties.clientSecret(),
                "grant_type", "authorization_code",
                "code", code
        );

        NuvemshopTokenResponse token = restClient.post()
                .uri(properties.tokenUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(NuvemshopTokenResponse.class);

        if (token == null || token.storeId() == null || token.accessToken() == null || token.accessToken().isBlank()) {
            throw new IllegalStateException("OAuth da Nuvemshop retornou token incompleto.");
        }

        Store store = storeRepository.findByStoreId(token.storeId()).orElseGet(Store::new);
        store.setStoreId(token.storeId());
        store.setAccessToken(token.accessToken());
        store.setScope(token.scope());
        store.setUninstalledAt(null);
        Store saved = storeRepository.save(store);
        webhookRegistrationService.registerRequiredWebhooks(saved);
        return saved;
    }
}
