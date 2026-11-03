package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static br.com.nuvemcustomfields.entity.RenewalFailureEmail.State.*;

class RenewalFailureEmailDispatcherTest {
    final RenewalFailureEmailRepository queue=mock(RenewalFailureEmailRepository.class);
    final PaymentSubscriptionRepository subs=mock(PaymentSubscriptionRepository.class);
    final RenewalFailureEmailService emails=mock(RenewalFailureEmailService.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    RenewalFailureEmailDispatcher worker; RenewalFailureEmail row; PaymentSubscription sub;
    @BeforeEach void setup() {
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        worker=new RenewalFailureEmailDispatcher(queue,subs,emails,manager);
        row=new RenewalFailureEmail(); row.setStoreId(12L); row.setProvider(PaymentProviderType.EFI);
        row.setSubscriptionId("contract"); row.setPaymentId("charge");
        sub=new PaymentSubscription(); sub.setProvider(PaymentProviderType.EFI); sub.setProviderSubscriptionId("contract");
        sub.startRenewalFailure(java.time.Instant.now(),"charge");
        when(queue.findDue(eq(PENDING),any(),any())).thenReturn(List.of(row),List.of());
        when(queue.saveAndFlush(row)).thenReturn(row); when(queue.findById(any())).thenReturn(Optional.of(row));
        when(subs.findByStoreId(12L)).thenReturn(Optional.of(sub));
        when(emails.preview(12L)).thenReturn(new RenewalFailureEmailService.Notice(true,null,"owner@example.com","subject","body","hash","action","https://app.example.com"));
    }
    @Test void sendsOnlyAfterPersistingTheClaimAndRecordsDelivery() {
        worker.dispatch();
        var order=inOrder(queue,emails);
        order.verify(queue).saveAndFlush(row); order.verify(emails).sendAutomatic(12L,"hash");
        assertThat(row.getState()).isEqualTo(SENT);
    }
    @Test void skipsARecoveredPayment() {
        sub.clearRenewalFailure(); worker.dispatch();
        assertThat(row.getState()).isEqualTo(SKIPPED); verify(emails,never()).sendAutomatic(any(),any());
    }
    @Test void anIndeterminateSmtpFailureNeedsReviewAndIsNotAutomaticallyRetried() {
        doThrow(new IllegalStateException("SMTP timeout")).when(emails).sendAutomatic(12L,"hash");
        worker.dispatch(); assertThat(row.getState()).isEqualTo(REVIEW);
        verify(emails,times(1)).sendAutomatic(12L,"hash");
    }
}
