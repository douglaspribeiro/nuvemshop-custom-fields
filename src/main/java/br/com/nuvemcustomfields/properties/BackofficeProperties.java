package br.com.nuvemcustomfields.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "backoffice")
public record BackofficeProperties(@NotBlank String username, @NotBlank String password) {
}
