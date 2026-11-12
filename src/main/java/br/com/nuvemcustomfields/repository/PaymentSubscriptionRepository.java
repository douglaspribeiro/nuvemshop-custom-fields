package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PaymentSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.time.Instant;
import br.com.nuvemcustomfields.entity.PaymentSubscriptionStatus;

public interface PaymentSubscriptionRepository extends JpaRepository<PaymentSubscription, Long> {
    @org.springframework.data.jpa.repository.Query("select p.storeId from PaymentSubscription p, Store s where p.storeId=s.storeId and p.renewalFailureDetectedAt is not null and p.graceUntil <= :now and s.plan in :plans")
    List<Long> findExpiredRenewalGraceStoreIds(Instant now, Collection<br.com.nuvemcustomfields.entity.PlanType> plans);
    @org.springframework.data.jpa.repository.Query("select p.storeId from PaymentSubscription p where p.webhookDowngradeAfter is not null and p.webhookDowngradeAfter <= :now")
    List<Long> findExpiredWebhookDowngrades(Instant now);
    List<PaymentSubscription> findByWinbackRestorePendingTrue();
    Optional<PaymentSubscription> findByStoreId(Long storeId);
    List<PaymentSubscription> findByStoreIdIn(Collection<Long> storeIds);
    Optional<PaymentSubscription> findByProviderSubscriptionId(String providerSubscriptionId);
    Optional<PaymentSubscription> findByProviderCheckoutId(String providerCheckoutId);
    Optional<PaymentSubscription> findByExternalReference(String externalReference);
    List<PaymentSubscription> findByProviderAndStatusIn(
            br.com.nuvemcustomfields.entity.PaymentProviderType provider,
            java.util.Collection<br.com.nuvemcustomfields.entity.PaymentSubscriptionStatus> statuses
    );
    List<PaymentSubscription> findByStatusAndAccessActiveTrueAndNextPaymentAtLessThanEqual(
            PaymentSubscriptionStatus status, Instant nextPaymentAt);
    List<PaymentSubscription> findByProviderAndStatusAndAccessActiveFalseAndPendingStartedAtLessThanEqual(
            br.com.nuvemcustomfields.entity.PaymentProviderType provider,
            PaymentSubscriptionStatus status, Instant pendingStartedAt);
}
