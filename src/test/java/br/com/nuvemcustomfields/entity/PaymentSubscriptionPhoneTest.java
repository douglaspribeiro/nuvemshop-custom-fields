package br.com.nuvemcustomfields.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentSubscriptionPhoneTest {
    @Test void keepsCountryCodeWithoutAssumingBrazilForForeignPhones() {
        var subscription = new PaymentSubscription();
        subscription.capturePayerPhone(" +54 (11) 1234-5678 ");
        assertThat(subscription.getPayerPhone()).isEqualTo("+541112345678");
        subscription.capturePayerPhone("300 1234567");
        assertThat(subscription.getPayerPhone()).isEqualTo("3001234567");
    }

    @Test void missingOrMalformedNumbersDoNotEraseCapturedContact() {
        var subscription = new PaymentSubscription();
        subscription.capturePayerPhone("+5511999999999");
        for (String invalid : new String[]{null, "", " ", "null", "abc11999999999", "123", "+1234567890123456", "++5511999999999"}) {
            subscription.capturePayerPhone(invalid);
            assertThat(subscription.getPayerPhone()).isEqualTo("+5511999999999");
        }
    }
}
