package br.com.nuvemcustomfields.payment;

public record GatewayCheckout(String subscriptionId, String checkoutUrl, String providerStatus) {
}
