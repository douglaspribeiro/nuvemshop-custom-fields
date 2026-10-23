package br.com.nuvemcustomfields.entity;

public enum PaymentProviderType {
    MERCADO_PAGO,
    EFI,
    PADDLE,
    CREEM,
    DODO,
    DODO_TEST,
    STRIPE,
    DLOCAL_GO;

    public boolean isStripeOrCreem() { return this == STRIPE || this == CREEM; }

    public boolean isDodo() { return this == DODO || this == DODO_TEST; }
}
