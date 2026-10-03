package br.com.nuvemcustomfields.properties;

import br.com.nuvemcustomfields.entity.PlanType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "payments.efi")
public record EfiProperties(boolean enabled, boolean sandbox, String clientId, String clientSecret,
                            String payeeCode, String premiumPlanId, String premiumPlusPlanId, String premiumUltraPlanId,
                            BigDecimal premiumAmount, BigDecimal premiumPlusAmount, BigDecimal premiumUltraAmount) {
    @ConstructorBinding
    public EfiProperties { }

    public EfiProperties(boolean enabled, boolean sandbox, String clientId, String clientSecret, String payeeCode,
                         String premiumPlanId, String premiumPlusPlanId, BigDecimal premiumAmount, BigDecimal premiumPlusAmount) {
        this(enabled, sandbox, clientId, clientSecret, payeeCode, premiumPlanId, premiumPlusPlanId, null,
                premiumAmount, premiumPlusAmount, BigDecimal.ZERO);
    }
    public boolean configured() {
        return credentialsConfigured() && filled(premiumPlanId) && filled(premiumPlusPlanId);
    }

    public boolean credentialsConfigured() {
        return enabled && filled(clientId) && filled(clientSecret) && filled(payeeCode);
    }

    public String planId(PlanType plan) {
        return switch (plan) {
            case PREMIUM -> premiumPlanId;
            case PREMIUM_PLUS -> premiumPlusPlanId;
            case PREMIUM_ULTRA -> premiumUltraPlanId;
            default -> throw new IllegalArgumentException("Plano não disponível para assinatura.");
        };
    }

    public BigDecimal amount(PlanType plan) {
        return switch (plan) {
            case PREMIUM -> premiumAmount;
            case PREMIUM_PLUS -> premiumPlusAmount;
            case PREMIUM_ULTRA -> premiumUltraAmount;
            default -> BigDecimal.ZERO;
        };
    }

    private static boolean filled(String value) { return value != null && !value.isBlank(); }
}
