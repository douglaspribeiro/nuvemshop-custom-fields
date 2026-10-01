package br.com.nuvemcustomfields.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "winback.discount")
public record WinbackDiscountProperties(boolean enabled, boolean allowProduction) { }
