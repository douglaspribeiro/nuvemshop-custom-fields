package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;

/** Persist the requested target before asking Creem to charge the proportional adjustment. */
@Service
public class CreemUpgradeService {
    private final StoreRepository stores;
    private final PaymentSubscriptionRepository subscriptions;
    private final CreemGateway gateway;
    private final ObjectProvider<PaymentSubscriptionService> service;
    private final TransactionTemplate transaction;
    public CreemUpgradeService(StoreRepository stores, PaymentSubscriptionRepository subscriptions, CreemGateway gateway,
            ObjectProvider<PaymentSubscriptionService> service, PlatformTransactionManager manager) {
        this.stores = stores; this.subscriptions = subscriptions; this.gateway = gateway; this.service = service;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public void upgrade(Long storeId, PlanType target, BigDecimal confirmedAmount) {
        Reservation reservation = transaction.execute(status -> {
            Store store = stores.findActiveByStoreIdForUpdate(storeId).orElseThrow();
            PaymentSubscription local = subscriptions.findByStoreId(storeId).orElseThrow();
            if (local.getProvider() != PaymentProviderType.CREEM || local.getProviderEnvironment() != gateway.environment()
                    || !local.isAccessActive() || local.getStatus() != PaymentSubscriptionStatus.ACTIVE
                    || local.getProviderSubscriptionId() == null || local.isCancellationPending()
                    || local.getCancellationEffectiveAt() != null || store.isCourtesyPremium()
                    || target == null || !target.isUpgradeFrom(local.getPlan()) || !gateway.planAvailable(store, target))
                throw new IllegalArgumentException("Plano ainda não disponível para esta assinatura.");
            if (local.getUpgradePlan() != null)
                throw new PaymentGatewayException("A alteração anterior ainda precisa ser conciliada. Nenhuma nova cobrança foi iniciada.");
            if (!local.getCurrency().equals(gateway.currency(store)))
                throw new IllegalArgumentException("A moeda do catálogo mudou. Contate o suporte para alterar este contrato.");
            BigDecimal amount = gateway.amount(store, target);
            if (confirmedAmount == null || amount.compareTo(confirmedAmount) != 0)
                throw new IllegalArgumentException("O preço mudou. Revise o valor antes de confirmar.");
            GatewaySubscription remote = gateway.getSubscription(local.getProviderSubscriptionId());
            if (!"active".equals(remote.status()) || !local.getProviderPriceId().equals(remote.priceId())
                    || !local.getCurrency().equals(remote.currency()) || local.getAmountValue().compareTo(remote.amount()) != 0)
                throw new IllegalArgumentException("A assinatura precisa ser conciliada antes do upgrade.");
            local.requestUpgrade(target, amount, gateway.priceId(store, target)); local.captureAnalytics();
            if (local.getAnalyticsFirstPaymentId() == null) local.setAnalyticsFirstPaymentId("legacy");
            subscriptions.saveAndFlush(local);
            return new Reservation(store, local.getProviderSubscriptionId());
        });
        // Do not replay this non-idempotent call if its outcome is unknown.
        gateway.changeSubscriptionPlan(reservation.id(), reservation.store(), target);
        transaction.executeWithoutResult(status -> service.getObject().synchronizeFromSubscription(PaymentProviderType.CREEM, reservation.id()));
    }
    private record Reservation(Store store, String id) { }
}
