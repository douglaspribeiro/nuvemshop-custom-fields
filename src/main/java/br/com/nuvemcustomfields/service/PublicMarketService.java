package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.properties.NuvemshopBillingProperties;
import br.com.nuvemcustomfields.repository.StoreRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Public prices are informational; payment routing continues to use the store's country. */
@Service
public class PublicMarketService {
    private static final String SESSION_KEY = "publicMarketCountry";
    private static final Map<String, String> COUNTRIES = countries();
    private final NuvemshopBillingProperties billing;
    private final StoreRepository stores;

    public PublicMarketService(NuvemshopBillingProperties billing, StoreRepository stores) {
        this.billing = billing;
        this.stores = stores;
    }

    public Map<String, String> countryOptions() { return COUNTRIES; }

    public Market resolve(HttpServletRequest request) {
        var session = request.getSession(false);
        String country = normalize(request.getParameter("country"));
        if (COUNTRIES.containsKey(country)) {
            request.getSession().setAttribute(SESSION_KEY, country);
        } else {
            country = session == null ? "" : normalize((String) session.getAttribute(SESSION_KEY));
            if (!COUNTRIES.containsKey(country) && session != null
                    && session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY) instanceof Long id) {
                country = stores.findActiveByStoreId(id).map(s -> normalize(s.getStoreCountryCode())).orElse("");
            }
            if (country.isEmpty()) country = normalize(request.getHeader("CF-IPCountry"));
            if (country.isEmpty() || country.equals("XX") || country.equals("T1")) {
                country = normalize(request.getLocale().getCountry());
                if (country.isEmpty()) country = request.getHeader("Accept-Language") == null
                        || "pt".equals(request.getLocale().getLanguage()) ? "BR" : "US";
            }
        }
        String priceCountry = COUNTRIES.containsKey(country) ? country : "US";
        var prices = billing.prices();
        var price = prices == null ? null : prices.entrySet().stream()
                .filter(e -> priceCountry.equalsIgnoreCase(e.getKey())).map(Map.Entry::getValue).findFirst().orElse(null);
        if (price == null && priceCountry.equals("BR")) {
            price = new NuvemshopBillingProperties.CountryPrice(billing.currency(), billing.premiumAmount(),
                    billing.premiumPlusAmount(), billing.premiumUltraAmount());
        }
        return new Market(priceCountry, COUNTRIES.get(priceCountry), priceCountry.equals("BR") ? "Efí" : "Creem",
                price == null ? "" : price.currency(),
                price == null ? "Consulte" : format(price.premiumAmount(), price.currency(), priceCountry),
                price == null ? "Consulte" : format(price.premiumPlusAmount(), price.currency(), priceCountry),
                price == null ? "Consulte" : format(price.premiumUltraAmount(), price.currency(), priceCountry));
    }

    private String format(BigDecimal amount, String currency, String country) {
        if (amount == null) return "Consulte";
        var unit = Currency.getInstance(currency);
        var locale = Locale.forLanguageTag(switch (country) {
            case "BR" -> "pt-BR"; case "AR" -> "es-AR"; case "CL" -> "es-CL";
            case "MX" -> "es-MX"; case "CO" -> "es-CO"; default -> "en-US";
        });
        var number = NumberFormat.getNumberInstance(locale);
        number.setMinimumFractionDigits(unit.getDefaultFractionDigits());
        number.setMaximumFractionDigits(unit.getDefaultFractionDigits());
        return (country.equals("BR") ? "R$" : unit.getCurrencyCode()) + " " + number.format(amount);
    }

    private static String normalize(String value) { return value == null ? "" : value.strip().toUpperCase(Locale.ROOT); }
    private static Map<String, String> countries() {
        var countries = new LinkedHashMap<String, String>();
        countries.put("BR", "Brasil"); countries.put("AR", "Argentina"); countries.put("CL", "Chile");
        countries.put("MX", "México"); countries.put("CO", "Colômbia"); countries.put("US", "Demais países / USD");
        return java.util.Collections.unmodifiableMap(countries);
    }

    public record Market(String country, String countryName, String provider, String currency,
                         String essentialPrice, String proPrice, String ultraPrice) { }
}
