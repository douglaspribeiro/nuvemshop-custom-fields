package br.com.nuvemcustomfields.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

/** Billing policy shared by sandbox, live and price presentation. */
public final class DodoMarketPricing {
    private DodoMarketPricing() { }
    public static String currency(String country) {
        return switch (country == null ? "" : country.toUpperCase(Locale.ROOT)) {
            case "BR" -> "BRL";
            case "CL" -> "CLP";
            case "MX" -> "MXN";
            default -> "USD";
        };
    }
    public static void validate(String country, String currency, BigDecimal amount) {
        if (country == null || !country.matches("[A-Z]{2}") || country.equals("BR") || !currency(country).equals(currency))
            throw new IllegalArgumentException("Use CLP no Chile, MXN no México e USD nos demais países fora do Brasil.");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Informe um preço positivo.");
        try { amount.setScale(Currency.getInstance(currency).getDefaultFractionDigits(), RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("O valor tem casas decimais inválidas para " + currency + "."); }
    }
    public static BigDecimal fromMinor(BigDecimal amount, String currency) {
        return amount.movePointLeft(Currency.getInstance(currency).getDefaultFractionDigits());
    }
}
