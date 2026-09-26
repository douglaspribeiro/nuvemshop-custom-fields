package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PaymentEnvironment;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;

import java.math.BigDecimal;
import java.util.Optional;

public interface PaymentGateway {
    PaymentProviderType provider();
    boolean configured();
    boolean supports(Store store);
    BigDecimal amount(PlanType plan);
    default BigDecimal amount(Store store, PlanType plan) { return amount(plan); }
    default String currency(Store store) { return "BRL"; }
    default String priceId(Store store, PlanType plan) { return null; }
    default PaymentEnvironment environment() { return PaymentEnvironment.PRODUCTION; }
    GatewayCheckout createCheckout(Store store, PlanType plan, String externalReference, String returnUrl);
    GatewaySubscription getSubscription(String subscriptionId);
    Optional<GatewayInvoice> getLatestInvoice(String subscriptionId);
    GatewayInvoice getInvoice(String invoiceId);
    void cancel(String subscriptionId);
    default void cancelImmediately(String subscriptionId) { cancel(subscriptionId); }
    void cancelCheckout(String checkoutResourceId);
    GatewayNotification verifyNotification(String body, String signature, String requestId, String dataId);
}
