package br.com.nuvemcustomfields.properties;

import br.com.nuvemcustomfields.entity.PlanType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "payments.mercado-pago")
public record MercadoPagoProperties(
        boolean enabled,
        String apiBaseUrl,
        String accessToken,
        String webhookSecret,
        int graceDays,
        BigDecimal premiumAmount,
        BigDecimal premiumPlusAmount,
        BigDecimal premiumUltraAmount
) {
    @ConstructorBinding
    public MercadoPagoProperties { }

    public MercadoPagoProperties(boolean enabled, String apiBaseUrl, String accessToken, String webhookSecret,
                                 int graceDays, BigDecimal premiumAmount, BigDecimal premiumPlusAmount) {
        this(enabled, apiBaseUrl, accessToken, webhookSecret, graceDays, premiumAmount, premiumPlusAmount, BigDecimal.ZERO);
    }
    public boolean configured() {
        return enabled && hasText(apiBaseUrl) && hasText(accessToken) && hasText(webhookSecret);
    }

    public BigDecimal amount(PlanType plan) {
        return switch (plan) {
            case PREMIUM -> premiumAmount;
            case PREMIUM_PLUS -> premiumPlusAmount;
            case PREMIUM_ULTRA -> premiumUltraAmount;
            case FREE, FREE_GRATIS -> BigDecimal.ZERO;
        };
    }

    public int safeGraceDays() {
        return Math.max(0, graceDays);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
