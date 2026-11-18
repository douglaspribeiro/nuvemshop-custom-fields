package br.com.nuvemcustomfields.entity;

public enum PaymentProviderType {
    MERCADO_PAGO,
    EFI,
    EFI_PIX,
    PADDLE,
    CREEM,
    DODO,
    DODO_TEST,
    STRIPE,
    DLOCAL_GO;

    public boolean isAvailable() { return this != PADDLE && this != DLOCAL_GO; }

    public boolean isStripeOrCreem() { return this == STRIPE || this == CREEM; }

    public boolean isDodo() { return this == DODO || this == DODO_TEST; }
}
