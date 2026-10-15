package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.UptimeRobotProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ServiceStatusServiceTest {
    private static final String RESPONSE = """
        {"stat":"ok","monitors":[{"status":2,"url":"https://private.example","friendly_name":"internal",
        "custom_uptime_ratios":"100.000-99.750-98.500","logs":[
        {"type":1,"datetime":1735689600,"reason":{"detail":"secret"}},
        {"type":2,"datetime":1735693200},{"type":99,"datetime":1735693201}]}]}
        """;
    private static final Instant NOW = Instant.parse("2025-01-02T00:00:00Z");

    @Test void postsCredentialInBodyAndCachesSuccessAndFailureWithoutLosingLastUpdate() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);
        var service = new ServiceStatusService(new UptimeRobotProperties("monitor-secret"), builder.build(), clock);
        server.expect(requestTo("https://api.uptimerobot.com/v2/getMonitors"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("api_key=monitor-secret")))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.uptimerobot.com/v2/getMonitors"))
                .andRespond(withServerError());
        var first = service.current();
        assertThat(first.state()).isEqualTo("up");
        assertThat(first.stale()).isFalse();
        assertThat(first.uptime()).extracting(ServiceStatusService.Uptime::percent).containsExactly("100", "99.75", "98.5");
        assertThat(first.events()).extracting(ServiceStatusService.Event::state).containsExactly("up", "down");
        assertThat(first.toString()).doesNotContain("secret", "internal", "private.example");
        assertThat(service.current()).isSameAs(first);
        when(clock.instant()).thenReturn(NOW.plusSeconds(61));
        var stale = service.current();
        assertThat(stale.stale()).isTrue();
        assertThat(stale.updatedAt()).isEqualTo(NOW);
        assertThat(stale.events()).isEqualTo(first.events());
        assertThat(service.current()).isSameAs(stale);
        server.verify();
    }

    @Test void noCredentialDoesNotCallUpstreamAndFirstFailureIsUnknown() {
        var client = mock(RestClient.class);
        assertThat(new ServiceStatusService(new UptimeRobotProperties(""), client, Clock.systemUTC()).current().state())
                .isEqualTo("unconfigured");
        verifyNoInteractions(client);
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.uptimerobot.com/v2/getMonitors"))
                .andRespond(withSuccess("{\"stat\":\"fail\",\"error\":\"private\"}", MediaType.APPLICATION_JSON));
        var status = new ServiceStatusService(new UptimeRobotProperties("secret"), builder.build(), Clock.systemUTC()).current();
        assertThat(status.state()).isEqualTo("unknown");
        assertThat(status.updatedAt()).isNull();
        assertThat(status.stale()).isTrue();
        server.verify();
    }

    @Test void mapsEveryStateAndRejectsResponsesWithMultipleMonitors() throws Exception {
        var mapper = new ObjectMapper();
        int[] codes = {0, 1, 2, 8, 9, 123};
        String[] states = {"paused", "pending", "up", "checking", "down", "unknown"};
        for (int i = 0; i < codes.length; i++) {
            var node = mapper.readTree("{\"stat\":\"ok\",\"monitors\":[{\"status\":" + codes[i] + "}]}");
            assertThat(ServiceStatusService.parse(node, NOW).state()).isEqualTo(states[i]);
        }
        var multiple = mapper.readTree("{\"stat\":\"ok\",\"monitors\":[{},{}]}");
        assertThatThrownBy(() -> ServiceStatusService.parse(multiple, NOW)).isInstanceOf(IllegalStateException.class);
        var invalid = mapper.readTree("{\"stat\":\"ok\",\"monitors\":[{\"custom_uptime_ratios\":\"NaN-101-\"}]}");
        assertThat(ServiceStatusService.parse(invalid, NOW).uptime()).allMatch(value -> value.percent() == null);
        assertThat(new UptimeRobotProperties("secret").toString()).doesNotContain("secret");
    }
}
