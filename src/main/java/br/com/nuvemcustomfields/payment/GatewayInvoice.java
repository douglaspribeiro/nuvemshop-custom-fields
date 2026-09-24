package br.com.nuvemcustomfields.payment;

public record GatewayInvoice(String id, String subscriptionId, String paymentId, String paymentStatus) {
    public boolean approved() {
        return "approved".equalsIgnoreCase(paymentStatus);
    }
}
