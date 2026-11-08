package br.com.nuvemcustomfields.payment;

public record GatewayInvoice(String id, String subscriptionId, String paymentId, String paymentStatus,
                             String currency, java.math.BigDecimal amountPaid, String payerPhone) {
    public GatewayInvoice(String id, String subscriptionId, String paymentId, String paymentStatus,
                          String currency, java.math.BigDecimal amountPaid) {
        this(id, subscriptionId, paymentId, paymentStatus, currency, amountPaid, null);
    }
    public GatewayInvoice(String id, String subscriptionId, String paymentId, String paymentStatus) {
        this(id, subscriptionId, paymentId, paymentStatus, null, null);
    }
    public boolean approved() {
        return "partialRefund".equalsIgnoreCase(paymentStatus)
                || "approved".equalsIgnoreCase(paymentStatus)
                || "paid".equalsIgnoreCase(paymentStatus)
                || "settled".equalsIgnoreCase(paymentStatus);
    }
}
