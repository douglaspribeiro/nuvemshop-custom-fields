package br.com.nuvemcustomfields.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CurrencyEstimateServiceTest {
    final RestClient.Builder builder = RestClient.builder();
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    final Clock clock = mock(Clock.class);
    final Instant now = Instant.parse("2026-10-15T12:00:00Z");
    CurrencyEstimateService service() { when(clock.instant()).thenReturn(now); return new CurrencyEstimateService(builder.build(),true,clock); }
    @Test void cachesDailyRatesAndNeverConvertsTheBillingCurrency() {
        var service=service();
        server.expect(requestTo("https://open.er-api.com/v6/latest/USD")).andRespond(withSuccess("{\"result\":\"success\",\"base_code\":\"USD\",\"time_last_update_unix\":"+now.minusSeconds(3600).getEpochSecond()+",\"rates\":{\"ARS\":1500,\"COP\":4000}}",MediaType.APPLICATION_JSON));
        var ar=service.estimate("AR","USD",new BigDecimal("4.99")).orElseThrow();
        assertThat(ar.formatted()).isEqualTo("ARS 7.485");
        assertThat(service.estimate("CO","USD",new BigDecimal("4.99")).orElseThrow().formatted()).isEqualTo("COP 19.960");
        assertThat(service.estimate("CL","CLP",new BigDecimal("4199"))).isEmpty();
        assertThat(service.estimate("AR","ARS",new BigDecimal("4199"))).isEmpty(); server.verify();
    }
    @Test void unavailableRatesDoNotInventAnEstimateOrRetryOnEveryRender() {
        var service=service();server.expect(requestTo("https://open.er-api.com/v6/latest/USD")).andRespond(withServerError());
        assertThat(service.estimate("AR","USD",BigDecimal.TEN)).isEmpty();
        assertThat(service.estimate("CO","USD",BigDecimal.TEN)).isEmpty();server.verify();
    }
    @Test void hidesRatesOlderThanTwoDays() {
        var service=service();
        server.expect(requestTo("https://open.er-api.com/v6/latest/USD")).andRespond(withSuccess("{\"result\":\"success\",\"base_code\":\"USD\",\"time_last_update_unix\":"+now.minusSeconds(200000).getEpochSecond()+",\"rates\":{\"ARS\":1500,\"COP\":4000}}",MediaType.APPLICATION_JSON));
        assertThat(service.estimate("AR","USD",BigDecimal.TEN)).isEmpty();server.verify();
    }
}
