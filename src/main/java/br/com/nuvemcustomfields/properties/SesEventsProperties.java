package br.com.nuvemcustomfields.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notifications.ses-events")
public record SesEventsProperties(boolean enabled, String queueUrl, String region, String configurationSet) { }
