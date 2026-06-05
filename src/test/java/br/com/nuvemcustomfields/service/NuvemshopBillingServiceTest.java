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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NuvemshopBillingServiceTest {

    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final PlanEventRepository planEventRepository = mock(PlanEventRepository.class);

    @Test
    void ensuresRemotePlanAndUpdatesSubscriptionBeforeChangingLocalPlan() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Store store = store(false);
        when(storeRepository.findActiveByStoreId(123L)).thenReturn(Optional.of(store));
        when(storeRepository.save(any(Store.class))).thenAnswer(invocation -> invocation.getArgument(0));

        server.expect(requestTo("https://api.example.com/2025-03/apps/client-123/plans"))
                .andExpect(method(POST))
                .andExpect(header("Authentication", "bearer secret"))
                .andExpect(jsonPath("$.external_reference").value("PREMIUM"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.example.com/2025-03/concepts/APP/services/client-123/subscriptions"))
                .andExpect(method(PATCH))
                .andExpect(header("Authentication", "bearer store-token"))
                .andExpect(jsonPath("$.amount_currency").value("BRL"))
                .andExpect(jsonPath("$.amount_value").value(9.99))
                .andExpect(jsonPath("$.plan_external_id").value("PREMIUM"))
                .andRespond(withSuccess("""
                        {
                          "external_reference": "sub-123",
                          "amount_currency": "BRL",
                          "amount_value": 9.99,
                          "next_execution": "2026-06-16",
                          "last_execution": "2026-05-16",
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
                .andExpect(method(POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.example.com/2025-03/concepts/APP/services/client-123/subscriptions"))
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

    private NuvemshopBillingService service(RestClient.Builder builder) {
        return new NuvemshopBillingService(
                new NuvemshopBillingProperties(
                        true,
                        "https://api.example.com/2025-03",
                        "APP",
                        "BRL",
                        "PREMIUM",
                        "PREMIUM_PLUS",
                        new BigDecimal("9.99"),
                        new BigDecimal("19.99")
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
                        ""
                ),
                storeRepository,
                planEventRepository,
                builder
        );
    }

    private Store store(boolean courtesyPremium) {
        Store store = new Store();
        store.setStoreId(123L);
        store.setAccessToken("store-token");
        store.setCourtesyPremium(courtesyPremium);
        return store;
    }
}
