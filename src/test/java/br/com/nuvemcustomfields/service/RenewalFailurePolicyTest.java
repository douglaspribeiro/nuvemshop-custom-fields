package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.RenewalFailureEmailRepository;
import java.time.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RenewalFailurePolicyTest {
    final RenewalFailureEmailRepository queue=mock(RenewalFailureEmailRepository.class);
    final RenewalFailurePolicy policy=new RenewalFailurePolicy(queue);
    Store store; PaymentSubscription sub; GatewaySubscription remote;
    GatewayInvoice failed=new GatewayInvoice("charge", "contract", "charge", "unpaid");
    @BeforeEach void setup() {
        store=new Store(); store.setStoreId(12L); store.setPlan(PlanType.PREMIUM_ULTRA);
        sub=new PaymentSubscription(); sub.setStoreId(12L); sub.setProvider(PaymentProviderType.EFI);
        sub.setProviderSubscriptionId("contract"); sub.setPlan(PlanType.PREMIUM_ULTRA); sub.setAccessActive(true);
        remote=new GatewaySubscription("contract","plan","ref","active","BRL",new BigDecimal("59.90"),null);
    }
    @Test void keepsPaidAccessForExactlyOneDayAndQueuesOneEmail() {
        assertThat(policy.apply(sub,store,remote,failed,false)).isTrue();
        assertThat(sub.getStatus()).isEqualTo(PaymentSubscriptionStatus.PAST_DUE);
        assertThat(sub.isAccessActive()).isTrue(); assertThat(store.getPlan()).isEqualTo(PlanType.PREMIUM_ULTRA);
        assertThat(Duration.between(sub.getRenewalFailureDetectedAt(),sub.getGraceUntil())).isEqualTo(Duration.ofDays(1));
        Instant deadline=sub.getGraceUntil();
        policy.apply(sub,store,remote,failed,false);
        policy.apply(sub,store,remote,new GatewayInvoice("retry","contract","retry","unpaid"),false);
        assertThat(sub.getGraceUntil()).isEqualTo(deadline);
        assertThat(sub.getRenewalFailurePaymentId()).isEqualTo("charge");
        verify(queue,times(1)).save(any(RenewalFailureEmail.class));
    }
    @Test void aConfirmedPaymentClearsTheFailureAndGrace() {
        policy.apply(sub,store,remote,failed,false);
        assertThat(policy.apply(sub,store,remote,new GatewayInvoice("paid","contract","paid","paid"),false)).isFalse();
        assertThat(sub.getGraceUntil()).isNull(); assertThat(sub.getRenewalFailurePaymentId()).isNull();
    }
    @Test void anExpiredGraceCannotBeRestartedByRepeatedWebhooks() {
        sub.startRenewalFailure(Instant.now().minus(Duration.ofHours(25)),"charge");
        Instant old=sub.getGraceUntil(); policy.apply(sub,store,remote,failed,false);
        assertThat(sub.getStatus()).isEqualTo(PaymentSubscriptionStatus.ERROR);
        assertThat(sub.getGraceUntil()).isEqualTo(old); verifyNoInteractions(queue);
    }
    @Test void noGraceForAnInitialFailureAnUpgradeOrACourtesy() {
        sub.setAccessActive(false); assertThat(policy.apply(sub,store,remote,failed,false)).isFalse();
        sub.setAccessActive(true); assertThat(policy.apply(sub,store,remote,failed,true)).isFalse();
        store.setCourtesyPremium(true); assertThat(policy.apply(sub,store,remote,failed,false)).isFalse();
        verifyNoInteractions(queue);
    }
    @Test void noGraceForCancellationRefundSandboxOrAnotherContract() {
        var canceled=new GatewaySubscription("contract","plan","ref","canceled","BRL",new BigDecimal("59.90"),null);
        assertThat(policy.apply(sub,store,canceled,failed,false)).isFalse();
        assertThat(policy.apply(sub,store,remote,new GatewayInvoice("r","contract","r","refunded"),false)).isFalse();
        assertThat(policy.apply(sub,store,remote,new GatewayInvoice("r","other","r","unpaid"),false)).isFalse();
        sub.setProviderEnvironment(PaymentEnvironment.SANDBOX);
        assertThat(policy.apply(sub,store,remote,failed,false)).isFalse(); verifyNoInteractions(queue);
    }
    @Test void anOpenStripeInvoiceRequiresAnOverdueContractToCountAsFailure() {
        assertThat(RenewalFailurePolicy.failedCharge(PaymentProviderType.STRIPE,"active","open")).isFalse();
        assertThat(RenewalFailurePolicy.failedCharge(PaymentProviderType.STRIPE,"past_due","open")).isTrue();
        assertThat(RenewalFailurePolicy.failedCharge(PaymentProviderType.STRIPE,"unpaid","uncollectible")).isTrue();
        assertThat(RenewalFailurePolicy.failedCharge(PaymentProviderType.EFI,"active","waiting")).isFalse();
    }
}
