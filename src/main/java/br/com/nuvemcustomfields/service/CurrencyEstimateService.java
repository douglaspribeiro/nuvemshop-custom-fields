package br.com.nuvemcustomfields.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;

/** Estimates only: these rates never enter a subscription or checkout quote. */
@Service
public class CurrencyEstimateService {
    private final RestClient client;
    private final boolean enabled;
    private final Clock clock;
    private Instant retryAt = Instant.MIN;
    private Instant updatedAt;
    private Map<String, BigDecimal> rates = Map.of();
    @Autowired
    public CurrencyEstimateService(RestClient.Builder builder, @Value("${pricing.fx-estimates.enabled:true}") boolean enabled) {
        this(timedClient(builder), enabled, Clock.systemUTC());
    }
    CurrencyEstimateService(RestClient client, boolean enabled, Clock clock) {
        this.client = client; this.enabled = enabled; this.clock = clock;
    }
    private static RestClient timedClient(RestClient.Builder builder) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(2)); return builder.requestFactory(factory).build();
    }
    public synchronized Optional<Estimate> estimate(String country, String billingCurrency, BigDecimal amount) {
        String currency = "AR".equals(country) ? "ARS" : "CO".equals(country) ? "COP" : null;
        if (!enabled || currency == null || !"USD".equals(billingCurrency) || amount == null || amount.signum() <= 0) return Optional.empty();
        Instant now = clock.instant();
        if (!now.isBefore(retryAt)) {
            retryAt = now.plus(Duration.ofMinutes(20));
            try {
                JsonNode data = client.get().uri("https://open.er-api.com/v6/latest/USD").retrieve().body(JsonNode.class);
                if (data != null && "success".equals(data.path("result").asText()) && "USD".equals(data.path("base_code").asText())) {
                    Instant date = Instant.ofEpochSecond(data.path("time_last_update_unix").asLong());
                    var ars = data.path("rates").path("ARS").decimalValue();
                    var cop = data.path("rates").path("COP").decimalValue();
                    if (date.isAfter(now.minus(Duration.ofHours(48))) && !date.isAfter(now.plusSeconds(300)) && ars.signum() > 0 && cop.signum() > 0) {
                        rates = Map.of("ARS", ars, "COP", cop); updatedAt = date; retryAt = now.plus(Duration.ofHours(24));
                    }
                }
            } catch (RuntimeException ignored) { /* Keep a recent quote; otherwise show the USD price alone. */ }
        }
        if (updatedAt == null || !updatedAt.isAfter(now.minus(Duration.ofHours(48)))) return Optional.empty();
        return Optional.of(new Estimate(currency, amount.multiply(rates.get(currency)).setScale(0, RoundingMode.HALF_UP), updatedAt.atZone(ZoneOffset.UTC).toLocalDate()));
    }
    public record Estimate(String currency, BigDecimal amount, LocalDate date) {
        public String formatted() {
            var number = java.text.NumberFormat.getIntegerInstance(Locale.forLanguageTag(currency.equals("ARS") ? "es-AR" : "es-CO"));
            return currency + " " + number.format(amount);
        }
    }
}
