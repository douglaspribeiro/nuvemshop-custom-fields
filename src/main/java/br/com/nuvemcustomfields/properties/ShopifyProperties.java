package br.com.nuvemcustomfields.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "shopify")
public record ShopifyProperties(
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank String scopes,
        @NotBlank String appBaseUrl,
        @NotBlank String apiVersion
) {
}
