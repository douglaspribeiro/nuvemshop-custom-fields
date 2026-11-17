package br.com.nuvemcustomfields.entity;

import java.math.BigDecimal;
import java.time.Instant;

/** Durable first-charge binding shared by winback and manually issued coupons. */
public interface EfiFirstPaymentDiscountUse {
    Long getStoreId();
    String getExternalReference();
    String getSubscriptionId();
    Instant getUsedAt();
    BigDecimal getRegularAmount();
    void subscription(String id);
    void firstPayment(String id);
    void used();
    void restored();
    void stopped();
}
