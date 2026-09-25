package br.com.nuvemcustomfields.properties;

import br.com.nuvemcustomfields.entity.PlanType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "payments.efi")
public record EfiProperties(boolean enabled, boolean sandbox, String clientId, String clientSecret,
                            String payeeCode, String premiumPlanId, String premiumPlusPlanId,
                            BigDecimal premiumAmount, BigDecimal premiumPlusAmount) {
    public boolean configured() {
        return enabled && filled(clientId) && filled(clientSecret) && filled(payeeCode)
                && filled(premiumPlanId) && filled(premiumPlusPlanId);
    }

    public String planId(PlanType plan) {
        return switch (plan) {
            case PREMIUM -> premiumPlanId;
            case PREMIUM_PLUS -> premiumPlusPlanId;
            default -> throw new IllegalArgumentException("Plano não disponível para assinatura.");
        };
    }

    public BigDecimal amount(PlanType plan) {
        return switch (plan) {
            case PREMIUM -> premiumAmount;
            case PREMIUM_PLUS -> premiumPlusAmount;
            default -> BigDecimal.ZERO;
        };
    }

    private static boolean filled(String value) { return value != null && !value.isBlank(); }
}
