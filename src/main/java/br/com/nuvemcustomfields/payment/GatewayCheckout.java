package br.com.nuvemcustomfields.payment;

public record GatewayCheckout(String subscriptionId, String checkoutResourceId, String checkoutUrl,
                              String providerStatus) {
}
