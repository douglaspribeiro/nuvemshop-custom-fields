package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.PaymentAttemptRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/** Retain old checkout identity so delayed events cannot activate the newly selected plan. */
final class PendingCheckoutReplacement {
    private PendingCheckoutReplacement() { }

    static void record(PaymentSubscription local, PaymentAttemptRepository attempts) {
        if (local.getProviderSubscriptionId() != null)
            throw new IllegalStateException("A assinatura anterior precisa ser conciliada antes da troca de plano.");
        if (local.getProviderCheckoutId() != null) {
            if (local.getExternalReference() == null || local.getExternalReference().isBlank())
                throw new IllegalStateException("Não foi possível identificar a tentativa anterior.");
            PaymentAttempt attempt = attempts.findByExternalReference(local.getExternalReference()).orElseGet(() -> {
                PaymentAttempt created = new PaymentAttempt();
                created.setStoreId(local.getStoreId()); created.setProvider(local.getProvider());
                created.setEnvironment(local.getProviderEnvironment()); created.setCountryCode(local.getCountryCode());
                created.setPlan(local.getPlan()); created.setCurrency(local.getCurrency());
                created.setAmountValue(local.getAmountValue()); created.setExternalReference(local.getExternalReference());
                created.setCheckoutTokenHash(hash(local.getExternalReference())); created.setExpiresAt(Instant.now());
                return created;
            });
            attempt.setProviderTransactionId(local.getProviderCheckoutId());
            attempt.setStatus(PaymentAttemptStatus.REPLACED);
            attempt.setLastError("Checkout substituído por uma nova escolha de plano; acompanhar notificações tardias.");
            attempts.saveAndFlush(attempt);
        }
        local.setStatus(PaymentSubscriptionStatus.CANCELED);
    }

    private static String hash(String reference) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(reference.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
