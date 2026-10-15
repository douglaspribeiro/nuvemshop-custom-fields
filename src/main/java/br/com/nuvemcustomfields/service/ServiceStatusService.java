package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.UptimeRobotProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Publishes only allowlisted fields from one monitor; no upstream URLs or diagnostic messages. */
@Service
public class ServiceStatusService {
    private final UptimeRobotProperties properties;
    private final RestClient client;
    private final Clock clock;
    private Snapshot lastSuccess;
    private Instant nextAttempt = Instant.MIN;
    private Snapshot cached;

    @Autowired
    public ServiceStatusService(UptimeRobotProperties properties) {
        this(properties, clientBuilder().build(), Clock.systemUTC());
    }

    ServiceStatusService(UptimeRobotProperties properties, RestClient client, Clock clock) {
        this.properties = properties;
        this.client = client;
        this.clock = clock;
    }

    private static RestClient.Builder clientBuilder() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return RestClient.builder().requestFactory(factory);
    }

    /** Serializes refreshes, including failed requests, across all visitors in this instance. */
    public synchronized Snapshot current() {
        if (!properties.configured()) return new Snapshot("unconfigured", false, null, List.of(), List.of());
        if (clock.instant().isBefore(nextAttempt)) return cached;
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("api_key", properties.apiKey().strip());
            form.add("format", "json");
            form.add("custom_uptime_ratios", "1-7-30");
            form.add("logs", "1");
            form.add("log_types", "1-2");
            form.add("logs_limit", "10");
            var response = client.post().uri("https://api.uptimerobot.com/v2/getMonitors")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(JsonNode.class);
            cached = parse(response, clock.instant());
            lastSuccess = cached;
        } catch (RuntimeException ex) {
            // Exceptions may contain credentials or upstream diagnostics. Never publish or log them.
            cached = lastSuccess == null
                    ? new Snapshot("unknown", true, null, List.of(), List.of())
                    : new Snapshot(lastSuccess.state(), true, lastSuccess.updatedAt(), lastSuccess.uptime(), lastSuccess.events());
        }
        nextAttempt = clock.instant().plusSeconds(60);
        return cached;
    }

    static Snapshot parse(JsonNode response, Instant now) {
        if (response == null || !"ok".equals(response.path("stat").asText())
                || !response.path("monitors").isArray() || response.path("monitors").size() != 1) {
            throw new IllegalStateException("Expected one monitor. Use a monitor-specific API key.");
        }
        var monitor = response.path("monitors").get(0);
        String state = switch (monitor.path("status").asInt(-1)) {
            case 0 -> "paused";
            case 1 -> "pending";
            case 2 -> "up";
            case 8 -> "checking";
            case 9 -> "down";
            default -> "unknown";
        };
        var uptime = new ArrayList<Uptime>();
        var values = monitor.path("custom_uptime_ratios").asText("").split("-", -1);
        int[] days = {1, 7, 30};
        for (int i = 0; i < days.length; i++) {
            String value = null;
            if (i < values.length) {
                try {
                    var number = new BigDecimal(values[i]);
                    if (number.signum() >= 0 && number.compareTo(BigDecimal.valueOf(100)) <= 0)
                        value = number.stripTrailingZeros().toPlainString();
                } catch (NumberFormatException ignored) { }
            }
            uptime.add(new Uptime(days[i], value));
        }
        var events = new ArrayList<Event>();
        if (monitor.path("logs").isArray()) {
            for (var log : monitor.path("logs")) {
                int type = log.path("type").asInt();
                long timestamp = log.path("datetime").asLong();
                if ((type == 1 || type == 2) && timestamp > 0 && timestamp <= now.getEpochSecond()) {
                    events.add(new Event(type == 1 ? "down" : "up", Instant.ofEpochSecond(timestamp)));
                }
            }
        }
        events.sort(java.util.Comparator.comparing(Event::at).reversed());
        return new Snapshot(state, false, now, List.copyOf(uptime), events.stream().limit(10).toList());
    }

    public record Snapshot(String state, boolean stale, Instant updatedAt, List<Uptime> uptime, List<Event> events) { }
    public record Uptime(int days, String percent) { }
    public record Event(String state, Instant at) { }
}
