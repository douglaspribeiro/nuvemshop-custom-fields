package br.com.nuvemcustomfields.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "nuvemshop")
public record NuvemshopProperties(
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank String redirectUri,
        @NotBlank String authUrl,
        @NotBlank String tokenUrl,
        @NotBlank String apiBaseUrl,
        @NotBlank String appBaseUrl,
        @NotBlank String scopes,
        @NotBlank String userAgent,
        /**
         * Script legado de vitrine (DOM, sem SDK). A homologacao Nuvemshop exige que ele
         * coexista com o script NubeSDK enquanto houver temas nao migrados.
         */
        String scriptId,
        /** App NubeSDK de checkout. */
        String checkoutScriptId,
        /** App NubeSDK de vitrine; coexiste com o legado durante a migracao dos temas. */
        String storefrontSdkScriptId
) {
}
