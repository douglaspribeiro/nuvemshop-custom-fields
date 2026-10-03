package br.com.nuvemcustomfields.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "nuvemshop.billing")
public record NuvemshopBillingProperties(
        boolean enabled,
        @NotBlank String apiBaseUrl,
        String conceptCode,
        @NotBlank String currency,
        @NotBlank String premiumExternalId,
        @NotBlank String premiumPlusExternalId,
        @NotBlank String premiumUltraExternalId,
        BigDecimal premiumAmount,
        BigDecimal premiumPlusAmount,
        BigDecimal premiumUltraAmount,
        Map<String, CountryPrice> prices
) {
    @ConstructorBinding
    public NuvemshopBillingProperties { }

    public record CountryPrice(
            @NotBlank String currency,
            BigDecimal premiumAmount,
            BigDecimal premiumPlusAmount,
            BigDecimal premiumUltraAmount
    ) {
        @ConstructorBinding
        public CountryPrice { }

        public CountryPrice(String currency, BigDecimal premiumAmount, BigDecimal premiumPlusAmount) {
            this(currency, premiumAmount, premiumPlusAmount, BigDecimal.ZERO);
        }
    }

    public NuvemshopBillingProperties(boolean enabled, String apiBaseUrl, String conceptCode, String currency,
                                      String premiumExternalId, String premiumPlusExternalId,
                                      BigDecimal premiumAmount, BigDecimal premiumPlusAmount,
                                      Map<String, CountryPrice> prices) {
        this(enabled, apiBaseUrl, conceptCode, currency, premiumExternalId, premiumPlusExternalId, "premium-ultra-5990",
                premiumAmount, premiumPlusAmount, BigDecimal.ZERO, prices);
    }
}
