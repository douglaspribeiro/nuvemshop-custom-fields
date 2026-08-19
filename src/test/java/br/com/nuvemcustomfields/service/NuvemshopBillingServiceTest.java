package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopBillingProperties;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.repository.PlanEventRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NuvemshopBillingServiceTest {

    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final PlanEventRepository planEventRepository = mock(PlanEventRepository.class);
    private final NuvemshopApiClient apiClient = mock(NuvemshopApiClient.class);

    @Test
    void ensuresRemotePlanAndUpdatesSubscriptionBeforeChangingLocalPlan() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Store store = store(false);
        when(storeRepository.findActiveByStoreId(123L)).thenReturn(Optional.of(store));
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> invocation.getArgument(0));

        server.expect(requestTo("https://api.example.com/2025-03/apps/client-123/plans"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer secret"))
                .andRespond(withSuccess("""
                        [{"code":"PREMIUM","external_reference":"PREMIUM"}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.example.com/2025-03/123/concepts/APP/services/client-123/subscriptions"))
                .andExpect(method(PATCH))
                .andExpect(header("Authorization", "Bearer store-token"))
                .andExpect(jsonPath("$.amount_currency").value("BRL"))
                .andExpect(jsonPath("$.amount_value").value(19.99))
                .andExpect(jsonPath("$.plan_external_id").value("PREMIUM"))
                .andRespond(withSuccess("""
                        {
                          "external_reference": "sub-123",
                          "amount_currency": "BRL",
                          "amount_value": 19.99,
                          "next_execution": "2026-06-16T00:00:00.000Z",
                          "last_execution": "2026-05-16T00:00:00.000Z",
                          "plan": { "code": "PREMIUM" }
                        }
                        """, MediaType.APPLICATION_JSON));

        Store saved = service(builder).subscribe(store, PlanType.PREMIUM);

        assertThat(saved.getPlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(saved.getSubscriptionId()).isEqualTo("sub-123");
        assertThat(saved.getBillingPlanExternalId()).isEqualTo("PREMIUM");
        assertThat(saved.getBillingLastError()).isNull();
        verify(planEventRepository).save(any());
        server.verify();
    }

    @Test
    void failedSubscriptionKeepsPreviousPlanAndStoresError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Store store = store(false);
        when(storeRepository.findActiveByStoreId(123L)).thenReturn(Optional.of(store));
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> invocation.getArgument(0));

        server.expect(requestTo("https://api.example.com/2025-03/apps/client-123/plans"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        [{"code":"PREMIUM","external_reference":"PREMIUM"}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.example.com/2025-03/123/concepts/APP/services/client-123/subscriptions"))
                .andExpect(method(PATCH))
                .andRespond(withBadRequest().body("Invalid currency").contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> service(builder).subscribe(store, PlanType.PREMIUM))
                .isInstanceOf(RuntimeException.class);

        assertThat(store.getPlan()).isEqualTo(PlanType.FREE);
        assertThat(store.getBillingLastError()).contains("Invalid currency");
        verify(planEventRepository, never()).save(any());
        server.verify();
    }

    @Test
    void courtesyPremiumDoesNotCallBillingApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Store store = store(true);
        when(storeRepository.findActiveByStoreId(123L)).thenReturn(Optional.of(store));

        assertThatThrownBy(() -> service(builder).subscribe(store, PlanType.PREMIUM))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Premium Cortesia");

        verify(storeRepository, never()).save(any());
        server.verify();
    }

    @Test
    void usesStoreCountryPriceAndCurrencyForArgentina() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Store store = store(false);
        store.setStoreCountryCode("AR");
        store.setStoreCurrency("ARS");
        when(storeRepository.findActiveByStoreId(123L)).thenReturn(Optional.of(store));
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> invocation.getArgument(0));

        server.expect(requestTo("https://api.example.com/2025-03/apps/client-123/plans"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        [{"code":"PREMIUM","external_reference":"PREMIUM"}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.example.com/2025-03/123/concepts/APP/services/client-123/subscriptions"))
                .andExpect(method(PATCH))
                .andExpect(jsonPath("$.amount_currency").value("ARS"))
                .andExpect(jsonPath("$.amount_value").value(5747))
                .andRespond(withSuccess("""
                        {
                          "external_reference": "sub-123",
                          "amount_currency": "ARS",
                          "amount_value": 5747,
                          "plan": { "code": "PREMIUM" }
                        }
                        """, MediaType.APPLICATION_JSON));

        Store saved = service(builder).subscribe(store, PlanType.PREMIUM);

        assertThat(saved.getBillingAmountCurrency()).isEqualTo("ARS");
        assertThat(saved.getBillingAmountValue()).isEqualByComparingTo(new BigDecimal("5747"));
        server.verify();
    }

    @Test
    void refreshesAndPersistsMissingStoreMarketBeforeShowingPrices() {
        RestClient.Builder builder = RestClient.builder();
        Store store = store(false);
        store.setStoreCountryCode(null);
        store.setStoreCurrency(null);
        when(apiClient.getStoreProfile(store)).thenReturn(new br.com.nuvemcustomfields.dto.StoreProfile("Loja", "BR", "BRL"));
        when(storeRepository.save(store)).thenReturn(store);

        BigDecimal amount = service(builder).amountFor(store, PlanType.PREMIUM);

        assertThat(amount).isEqualByComparingTo("19.99");
        assertThat(store.getStoreCountryCode()).isEqualTo("BR");
        assertThat(store.getStoreCurrency()).isEqualTo("BRL");
        verify(storeRepository).save(store);
    }

    @Test
    void asksForReinstallationWhenStoreMarketCannotBeRecovered() {
        RestClient.Builder builder = RestClient.builder();
        Store store = store(false);
        store.setStoreCountryCode(null);
        store.setStoreCurrency(null);
        when(storeRepository.findActiveByStoreId(123L)).thenReturn(Optional.of(store));
        when(apiClient.getStoreProfile(store)).thenThrow(new IllegalStateException("profile unavailable"));

        assertThatThrownBy(() -> service(builder).subscribe(store, PlanType.PREMIUM))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reinstale o app");

        verify(planEventRepository, never()).save(any());
    }

    @Test
    void rejectsUnsupportedStoreCurrencyBeforeCallingBillingApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Store store = store(false);
        store.setStoreCountryCode("UY");
        store.setStoreCurrency("UYU");
        when(storeRepository.findActiveByStoreId(123L)).thenReturn(Optional.of(store));

        assertThatThrownBy(() -> service(builder).subscribe(store, PlanType.PREMIUM))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sem preco configurado");

        verify(storeRepository, never()).save(any());
        server.verify();
    }

    @Test
    void internalFreePlanCannotSubscribeThroughBilling() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Store store = store(false);

        assertThatThrownBy(() -> service(builder).subscribe(store, PlanType.FREE_GRATIS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plano pago");

        verify(storeRepository, never()).findActiveByStoreId(123L);
        verify(storeRepository, never()).save(any());
        server.verify();
    }

    @Test
    void syncSkipsInternalFreePlanWhenRemotePlanIsMissing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Store store = store(false);
        store.setPlan(PlanType.FREE_GRATIS);
        when(storeRepository.findByStoreId(123L)).thenReturn(Optional.of(store));
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> invocation.getArgument(0));

        server.expect(requestTo("https://api.example.com/2025-03/123/concepts/APP/services/client-123/subscriptions"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service(builder).syncSubscription(store);

        assertThat(store.getPlan()).isEqualTo(PlanType.FREE_GRATIS);
        assertThat(store.getBillingLastError()).isNull();
        verify(storeRepository).save(store);
        server.verify();
    }

    private NuvemshopBillingService service(RestClient.Builder builder) {
        return new NuvemshopBillingService(
                new NuvemshopBillingProperties(
                        true,
                        "https://api.example.com/2025-03",
                        "APP",
                        "BRL",
                        "PREMIUM",
                        "PREMIUM_PLUS",
                        new BigDecimal("19.99"),
                        new BigDecimal("29.99"),
                        Map.of(
                                "BR", new NuvemshopBillingProperties.CountryPrice("BRL", new BigDecimal("19.99"), new BigDecimal("29.99")),
                                "AR", new NuvemshopBillingProperties.CountryPrice("ARS", new BigDecimal("5747"), new BigDecimal("8622")),
                                "CL", new NuvemshopBillingProperties.CountryPrice("CLP", new BigDecimal("3566"), new BigDecimal("5350")),
                                "MX", new NuvemshopBillingProperties.CountryPrice("MXN", new BigDecimal("67.49"), new BigDecimal("101.26")),
                                "CO", new NuvemshopBillingProperties.CountryPrice("COP", new BigDecimal("13146"), new BigDecimal("19723"))
                        )
                ),
                new NuvemshopProperties(
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
                ),
                storeRepository,
                planEventRepository,
                apiClient,
                builder
        );
    }

    private Store store(boolean courtesyPremium) {
        Store store = new Store();
        store.setStoreId(123L);
        store.setAccessToken("store-token");
        store.setCourtesyPremium(courtesyPremium);
        store.setStoreCountryCode("BR");
        store.setStoreCurrency("BRL");
        return store;
    }
}
