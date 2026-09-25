package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PaymentNotificationOutbox;
import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PlanType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PaymentNotificationOutboxRepositoryTest {
    @Autowired PaymentNotificationOutboxRepository outbox;

    @Test
    void findsOnlyDueUndeliveredPayments() {
        PaymentNotificationOutbox notification = new PaymentNotificationOutbox();
        notification.setProvider(PaymentProviderType.EFI);
        notification.setPaymentId("charge-123");
        notification.setStoreId(123L);
        notification.setPlan(PlanType.PREMIUM);
        notification.setCurrency("BRL");
        notification.setAmountValue(new BigDecimal("19.99"));
        notification.setNextAttemptAt(Instant.now().minusSeconds(1));
        outbox.saveAndFlush(notification);

        assertThat(outbox.findDue(Instant.now(), PageRequest.of(0, 20)))
                .extracting(PaymentNotificationOutbox::getPaymentId)
                .containsExactly("charge-123");

        notification.setDeliveredAt(Instant.now());
        outbox.saveAndFlush(notification);
        assertThat(outbox.findDue(Instant.now(), PageRequest.of(0, 20))).isEmpty();
    }
}
