package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;

import java.math.BigDecimal;
import java.util.Optional;

public interface PaymentGateway {
    PaymentProviderType provider();
    boolean configured();
    boolean supports(Store store);
    BigDecimal amount(PlanType plan);
    GatewayCheckout createCheckout(Store store, PlanType plan, String externalReference, String returnUrl,
                                   String payerEmail);
    GatewaySubscription getSubscription(String subscriptionId);
    Optional<GatewayInvoice> getLatestInvoice(String subscriptionId);
    GatewayInvoice getInvoice(String invoiceId);
    void cancel(String subscriptionId);
    GatewayNotification verifyNotification(String body, String signature, String requestId, String dataId);
}
