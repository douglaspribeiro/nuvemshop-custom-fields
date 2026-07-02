package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.NuvemshopTokenResponse;
import br.com.nuvemcustomfields.dto.StoreProfile;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.repository.StoreRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.regex.Pattern;

@Service
public class NuvemshopAuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NuvemshopAuthService.class);
    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern.compile("(\"access_token\"\\s*:\\s*\")[^\"]+(\")");
    private static final Pattern CLIENT_SECRET_PATTERN = Pattern.compile("(\"client_secret\"\\s*:\\s*\")[^\"]+(\")");

    private final NuvemshopProperties properties;
    private final StoreRepository storeRepository;
    private final NuvemshopApiClient apiClient;
    private final WebhookRegistrationService webhookRegistrationService;
    private final ScriptInstallService scriptInstallService;
    private final IntegrationLogService integrationLogService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NuvemshopAuthService(
            NuvemshopProperties properties,
            StoreRepository storeRepository,
            NuvemshopApiClient apiClient,
            WebhookRegistrationService webhookRegistrationService,
            ScriptInstallService scriptInstallService,
            IntegrationLogService integrationLogService,
            RestClient.Builder builder
    ) {
        this.properties = properties;
        this.storeRepository = storeRepository;
        this.apiClient = apiClient;
        this.webhookRegistrationService = webhookRegistrationService;
        this.scriptInstallService = scriptInstallService;
        this.integrationLogService = integrationLogService;
        this.restClient = builder.defaultHeader("User-Agent", properties.userAgent()).build();
    }

    public String buildAuthorizationUrl(String state) {
        String authorizeUrl = properties.authUrl().replace("{clientId}", properties.clientId());
        LOGGER.info(
                "nuvemshop.oauth.authorization_url.build auth_url={} client_id_present={} redirect_uri={} app_base_url={} scopes={}",
                properties.authUrl(),
                !properties.clientId().isBlank(),
                properties.redirectUri(),
                properties.appBaseUrl(),
                properties.scopes()
        );
        return UriComponentsBuilder.fromUriString(authorizeUrl)
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    @Transactional
    public Store exchangeCodeAndUpsertStore(String code) {
        LOGGER.info("nuvemshop.oauth.exchange.start code_present={}", code != null && !code.isBlank());
        MultiValueMap<String, String> payload = new LinkedMultiValueMap<>();
        payload.add("client_id", properties.clientId());
        payload.add("client_secret", properties.clientSecret());
        payload.add("grant_type", "authorization_code");
        payload.add("code", code);

        NuvemshopTokenResponse token;
        try {
            String responseBody = restClient.post()
                    .uri(properties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            token = parseTokenResponse(responseBody);
        } catch (RestClientResponseException ex) {
            LOGGER.error(
                    "nuvemshop.oauth.exchange.error status={} response_body={}",
                    ex.getStatusCode(),
                    sanitizeForLog(ex.getResponseBodyAsString()),
                    ex
            );
            throw ex;
        } catch (RuntimeException ex) {
            LOGGER.error("nuvemshop.oauth.exchange.error message={}", ex.getMessage(), ex);
            throw ex;
        }

        if (token == null || token.storeId() == null || token.accessToken() == null || token.accessToken().isBlank()) {
            LOGGER.error("nuvemshop.oauth.exchange.incomplete_token token_present={} store_id_present={}", token != null, token != null && token.storeId() != null);
            throw new IllegalStateException("OAuth da Nuvemshop retornou token incompleto.");
        }

        LOGGER.info("nuvemshop.oauth.exchange.done store_id={} scope={}", token.storeId(), token.scope());
        Store store = storeRepository.findByStoreId(token.storeId()).orElseGet(Store::new);
        store.setStoreId(token.storeId());
        store.setAccessToken(token.accessToken());
        store.setScope(token.scope());
        try {
            StoreProfile profile = apiClient.getStoreProfile(store);
            store.setStoreName(profile.name());
            store.setStoreCountryCode(profile.countryCode());
            store.setStoreCurrency(profile.currency());
        } catch (RuntimeException ex) {
            LOGGER.warn("nuvemshop.oauth.store_profile.unavailable store_id={} message={}", token.storeId(), ex.getMessage());
        }
        store.setUninstalledAt(null);
        Store saved = storeRepository.save(store);
        webhookRegistrationService.registerRequiredWebhooks(saved);
        try {
            scriptInstallService.installPersonalizerScript(saved);
        } catch (RuntimeException ex) {
            LOGGER.error("nuvemshop.oauth.script_install.failed store_id={} message={}", saved.getStoreId(), ex.getMessage(), ex);
            integrationLogService.warn(saved.getStoreId(), "script.install.failed", "Falha ao instalar script na vitrine: " + ex.getMessage());
        }
        integrationLogService.info(saved.getStoreId(), "oauth.installed", "Loja instalada ou reconectada via OAuth.");
        LOGGER.info("nuvemshop.oauth.installation.done store_id={}", saved.getStoreId());
        return saved;
    }

    private NuvemshopTokenResponse parseTokenResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            LOGGER.error("nuvemshop.oauth.exchange.empty_response");
            throw new IllegalStateException("OAuth da Nuvemshop retornou resposta vazia.");
        }
        try {
            return objectMapper.readValue(responseBody, NuvemshopTokenResponse.class);
        } catch (JsonProcessingException ex) {
            LOGGER.error("nuvemshop.oauth.exchange.invalid_json response_body={}", sanitizeForLog(responseBody), ex);
            throw new IllegalStateException("OAuth da Nuvemshop retornou resposta invalida.", ex);
        }
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = ACCESS_TOKEN_PATTERN.matcher(value).replaceAll("$1***$2");
        sanitized = CLIENT_SECRET_PATTERN.matcher(sanitized).replaceAll("$1***$2");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }
}
