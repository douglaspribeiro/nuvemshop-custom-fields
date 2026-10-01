package br.com.nuvemcustomfields.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "winback")
public record WinbackProperties(boolean enabled, String queueUrl, String region) { }
