package br.com.nuvemcustomfields.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "uptimerobot")
public record UptimeRobotProperties(String apiKey) {
    public boolean configured() { return apiKey != null && !apiKey.isBlank(); }

    // Record's generated toString must never expose the credential.
    @Override public String toString() { return "UptimeRobotProperties[configured=" + configured() + "]"; }
}
