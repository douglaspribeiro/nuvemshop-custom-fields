package br.com.nuvemcustomfields.dto;

import br.com.nuvemcustomfields.entity.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Local contract snapshot. Payment amounts/dates and payouts have no persisted source. */
public record SubscriptionReportRow(Long id, String name, String plan, String subscriptionPlan,
        String source, String environment, String subscriptionId, String status, String access,
        String amount, String currency, String paymentId, String paymentStatus, String nextPayment,
        String lastSynced, boolean eligible, String mrrReason) {
    private static String known(Object value) {
        return value == null || value.toString().isBlank() ? "Não informado" : value.toString();
    }
    private static String date(java.time.Instant value, ZoneId zone) {
        return value == null ? "Não informado" : DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z").format(value.atZone(zone));
    }
    public static SubscriptionReportRow from(Store store, PaymentSubscription sub, String exclusion, ZoneId zone) {
        boolean legacy = sub == null && store.getSubscriptionId() != null && !store.getSubscriptionId().isBlank();
        return new SubscriptionReportRow(store.getStoreId(),
                store.getStoreName() == null || store.getStoreName().isBlank() ? "Loja sem nome" : store.getStoreName(),
                store.getPlan().getDisplayName(), sub == null || sub.getPlan() == null ? "Não informado" : sub.getPlan().getDisplayName(),
                sub == null ? (legacy ? "Contrato legado" : "Sem contrato") : known(sub.getProvider()),
                sub == null ? "Não informado" : known(sub.getProviderEnvironment()),
                known(sub == null ? store.getSubscriptionId() : sub.getProviderSubscriptionId()),
                sub == null ? "Não informado" : known(sub.getStatus()) + (sub.isCancellationPending() ? " · Cancelamento solicitado" : ""),
                "Loja " + (store.isActive() ? "ativa" : "inativa") + " · " + (store.isBillingSuspended() ? "Cobrança suspensa" : "Cobrança não suspensa")
                        + (sub == null ? " · Acesso da assinatura não informado" : sub.isAccessActive() ? " · Acesso da assinatura ativo" : " · Acesso da assinatura inativo"),
                known(sub == null ? store.getBillingAmountValue() : sub.getAmountValue()),
                known(sub == null ? store.getBillingAmountCurrency() : sub.getCurrency()),
                sub == null ? "Não informado" : known(sub.getLastPaymentId()),
                sub == null ? "Não informado" : known(sub.getLastPaymentStatus()),
                sub == null ? known(store.getBillingNextExecution()) : date(sub.getNextPaymentAt(), zone),
                date(sub == null ? store.getBillingLastSyncedAt() : sub.getLastSyncedAt(), zone),
                exclusion == null, exclusion == null ? "Elegível" : exclusion);
    }
}
