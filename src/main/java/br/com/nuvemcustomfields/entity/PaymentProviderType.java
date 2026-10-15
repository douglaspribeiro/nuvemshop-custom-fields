package br.com.nuvemcustomfields.entity;

public enum PaymentProviderType {
    MERCADO_PAGO,
    EFI,
    PADDLE,
    CREEM,
    DODO,
    DODO_TEST;

    public boolean isDodo() { return this == DODO || this == DODO_TEST; }
}
