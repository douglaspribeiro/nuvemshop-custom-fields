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

    public static boolean creemOverdue(PaymentProviderType provider, String status) {
        return provider == PaymentProviderType.CREEM
                && Set.of("past_due", "unpaid").contains(String.valueOf(status).toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean recordedFailure(PaymentSubscription sub) {
        return failedCharge(sub.getProvider(), sub.getProviderStatus(), sub.getLastPaymentStatus())
                || (creemOverdue(sub.getProvider(), sub.getProviderStatus())
                    && sub.getRenewalFailureDetectedAt() != null && sub.getRenewalFailurePaymentId() != null);
    }

    private static boolean previouslyPaid(PaymentSubscription sub) {
        return sub.getLastPaymentId() != null && !sub.getLastPaymentId().isBlank()
                && new GatewayInvoice(null, null, null, sub.getLastPaymentStatus()).approved();
    }

    // Creem can keep last_transaction_id pointing at the previous successful payment.
    // This is an incident key, not a fabricated transaction or a change to that paid charge.
    private static String incidentKey(PaymentSubscription sub, GatewaySubscription remote) {
        if (sub.getRenewalFailureDetectedAt() != null) return sub.getRenewalFailurePaymentId();
        String anchor = remote.id() + "\n" + sub.getLastPaymentId() + "\n" + remote.currentPeriodEnd();
        try {
            return "creem-overdue:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(anchor.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    /** Returns true only for a failed renewal of a previously paid, still-existing contract. */
    public boolean apply(PaymentSubscription sub, Store store, GatewaySubscription remote, GatewayInvoice invoice, boolean upgrading) {
        return apply(sub, store, remote, invoice, upgrading, sub.getLastPaymentId());
    }

    public boolean apply(PaymentSubscription sub, Store store, GatewaySubscription remote, GatewayInvoice invoice,
            boolean upgrading, String previousPaymentId) {
        boolean statusFailure = creemOverdue(sub.getProvider(), remote.status())
                && java.util.Objects.equals(sub.getProviderSubscriptionId(), remote.id())
                && (sub.getRenewalFailureDetectedAt() != null || previouslyPaid(sub))
                && (invoice == null || (invoice.approved() && java.util.Objects.equals(invoice.paymentId(), previousPaymentId)));
        if (invoice != null && invoice.approved() && !statusFailure) { sub.clearRenewalFailure(); return false; }
        String status = remote.status() == null ? "" : remote.status().toLowerCase(java.util.Locale.ROOT);
        if ((!statusFailure && (invoice == null || invoice.paymentId() == null || invoice.paymentId().isBlank()
                    || !failedCharge(sub.getProvider(), remote.status(), invoice.paymentStatus())))
                || (invoice != null && invoice.subscriptionId() != null && !invoice.subscriptionId().equals(remote.id()))
                || sub.getProviderEnvironment() != PaymentEnvironment.PRODUCTION || sub.getProvider() == PaymentProviderType.DODO_TEST
                || remote.id() == null || remote.id().isBlank() || !store.isActive() || store.isCourtesyPremium() || store.isManualPlanOverride()
                || upgrading || sub.getUpgradePlan() != null || sub.isUpgradePaymentPending()
                || sub.isCancellationPending() || remote.cancellationEffectiveAt() != null
                || Set.of("cancelled", "canceled", "expired", "failed", "incomplete", "incomplete_expired").contains(status)
                || (sub.getRenewalFailureDetectedAt() == null && (!sub.isAccessActive() || !store.getEffectivePlan().isBillable()))) return false;
        boolean first = sub.getRenewalFailureDetectedAt() == null;
        sub.startRenewalFailure(Instant.now(), statusFailure ? incidentKey(sub, remote) : invoice.paymentId());
        sub.setStatus(Instant.now().isBefore(sub.getGraceUntil()) ? PaymentSubscriptionStatus.PAST_DUE
                : sub.getProvider() == PaymentProviderType.EFI ? PaymentSubscriptionStatus.ERROR : PaymentSubscriptionStatus.PAST_DUE);
        if (Instant.now().isBefore(sub.getGraceUntil())) sub.setAccessActive(true);
        if (first && !emails.existsByProviderAndSubscriptionIdAndPaymentId(sub.getProvider(), remote.id(), sub.getRenewalFailurePaymentId())) {
            var notice = new RenewalFailureEmail(); notice.setStoreId(sub.getStoreId()); notice.setProvider(sub.getProvider());
            notice.setSubscriptionId(remote.id()); notice.setPaymentId(sub.getRenewalFailurePaymentId());
            notice.setNextAttemptAt(Instant.now().plus(java.time.Duration.ofHours(1))); emails.save(notice);
        }
        return true;
    }
}
