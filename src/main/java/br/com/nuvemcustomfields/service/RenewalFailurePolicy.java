package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.RenewalFailureEmailRepository;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RenewalFailurePolicy {
    private final RenewalFailureEmailRepository emails;
    public RenewalFailurePolicy(RenewalFailureEmailRepository emails) { this.emails = emails; }

    public static boolean failedCharge(PaymentProviderType provider, String contractStatus, String chargeStatus) {
        String charge=String.valueOf(chargeStatus).toLowerCase(java.util.Locale.ROOT);
        if (Set.of("unpaid", "failed", "rejected", "declined", "refused").contains(charge)) return true;
        return provider == PaymentProviderType.STRIPE && Set.of("open", "uncollectible").contains(charge)
                && Set.of("past_due", "unpaid").contains(String.valueOf(contractStatus).toLowerCase(java.util.Locale.ROOT));
    }

    /** Returns true only for a failed renewal of a previously paid, still-existing contract. */
    public boolean apply(PaymentSubscription sub, Store store, GatewaySubscription remote, GatewayInvoice invoice, boolean upgrading) {
        if (invoice != null && invoice.approved()) { sub.clearRenewalFailure(); return false; }
        String status = remote.status() == null ? "" : remote.status().toLowerCase(java.util.Locale.ROOT);
        if (invoice == null || invoice.paymentId() == null || invoice.paymentId().isBlank()
                || (invoice.subscriptionId() != null && !invoice.subscriptionId().equals(remote.id()))
                || !failedCharge(sub.getProvider(), remote.status(), invoice.paymentStatus())
                || sub.getProviderEnvironment() != PaymentEnvironment.PRODUCTION || sub.getProvider() == PaymentProviderType.DODO_TEST
                || remote.id() == null || remote.id().isBlank() || !store.isActive() || store.isCourtesyPremium() || store.isManualPlanOverride()
                || upgrading || sub.getUpgradePlan() != null || sub.isUpgradePaymentPending()
                || sub.isCancellationPending() || remote.cancellationEffectiveAt() != null
                || Set.of("cancelled", "canceled", "expired", "failed", "incomplete", "incomplete_expired").contains(status)
                || (sub.getRenewalFailureDetectedAt() == null && (!sub.isAccessActive() || !store.getEffectivePlan().isBillable()))) return false;
        boolean first = sub.getRenewalFailureDetectedAt() == null;
        sub.startRenewalFailure(Instant.now(), invoice.paymentId());
        sub.setStatus(Instant.now().isBefore(sub.getGraceUntil()) ? PaymentSubscriptionStatus.PAST_DUE
                : sub.getProvider() == PaymentProviderType.EFI ? PaymentSubscriptionStatus.ERROR : PaymentSubscriptionStatus.PAST_DUE);
        if (Instant.now().isBefore(sub.getGraceUntil())) sub.setAccessActive(true);
        if (first && !emails.existsByProviderAndSubscriptionIdAndPaymentId(sub.getProvider(), remote.id(), sub.getRenewalFailurePaymentId())) {
            var notice = new RenewalFailureEmail(); notice.setStoreId(sub.getStoreId()); notice.setProvider(sub.getProvider());
            notice.setSubscriptionId(remote.id()); notice.setPaymentId(sub.getRenewalFailurePaymentId()); emails.save(notice);
        }
        return true;
    }
}
