package br.com.nuvemcustomfields.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "nuvemshop.billing")
public record NuvemshopBillingProperties(
        boolean enabled,
        @NotBlank String apiBaseUrl,
        String conceptCode
) {
}
