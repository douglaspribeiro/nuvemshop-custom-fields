package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PaymentSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface PaymentSubscriptionRepository extends JpaRepository<PaymentSubscription, Long> {
    Optional<PaymentSubscription> findByStoreId(Long storeId);
    Optional<PaymentSubscription> findByProviderSubscriptionId(String providerSubscriptionId);
    Optional<PaymentSubscription> findByProviderCheckoutId(String providerCheckoutId);
    Optional<PaymentSubscription> findByExternalReference(String externalReference);
    List<PaymentSubscription> findByProviderAndStatusIn(
            br.com.nuvemcustomfields.entity.PaymentProviderType provider,
            java.util.Collection<br.com.nuvemcustomfields.entity.PaymentSubscriptionStatus> statuses
    );
}
