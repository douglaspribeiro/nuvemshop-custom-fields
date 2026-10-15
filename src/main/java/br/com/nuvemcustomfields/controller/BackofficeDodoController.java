package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.DodoTestGateway;
import br.com.nuvemcustomfields.payment.DodoLiveGateway;
import br.com.nuvemcustomfields.payment.DodoGateway;
import br.com.nuvemcustomfields.properties.DodoTestProperties;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Controller
public class BackofficeDodoController {
    private final DodoTestGateway gateway;
    private final DodoTestProperties properties;
    private final DodoLiveGateway live;
    private final br.com.nuvemcustomfields.properties.NuvemshopBillingProperties billing;
    private final PaymentCatalogPriceRepository catalog;
    public BackofficeDodoController(DodoTestGateway gateway, DodoTestProperties properties, PaymentCatalogPriceRepository catalog, DodoLiveGateway live, br.com.nuvemcustomfields.properties.NuvemshopBillingProperties billing) {
        this.gateway = gateway; this.properties = properties; this.catalog = catalog; this.live = live; this.billing = billing;
    }
    @GetMapping("/backoffice/payments/{dodoMode:dodo|dodo-test}")
    public String page(@PathVariable String dodoMode, Model model, HttpSession session, HttpServletResponse response) {
        DodoGateway selected = dodoMode.equals("dodo-test") ? gateway : live;
        String sessionKey = dodoMode.equals("dodo-test") ? "dodoTestActionToken" : "dodoLiveActionToken";
        if (session.getAttribute(sessionKey) == null) session.setAttribute(sessionKey, UUID.randomUUID().toString());
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("actionToken", session.getAttribute(sessionKey));
        model.addAttribute("configured", selected.configured());
        model.addAttribute("allowedStoreIds", properties.allowedStoreIds());
        model.addAttribute("allTestStoresAllowed", properties.allowsAllStores());
        model.addAttribute("testMode", selected.provider() == PaymentProviderType.DODO_TEST);
        model.addAttribute("gatewayPath", "/backoffice/payments/" + dodoMode);
        model.addAttribute("gatewayName", selected.provider() == PaymentProviderType.DODO_TEST ? "Dodo de teste" : "Dodo live");
        var references = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (String country : java.util.List.of("AR", "CO", "CL", "MX")) {
            String source = country.equals("CL") || country.equals("MX") ? country : "US";
            var reference = billing.prices() == null ? null : billing.prices().entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(source)).map(java.util.Map.Entry::getValue).findFirst().orElse(null);
            if (reference != null && reference.premiumAmount() != null)
                references.add(java.util.Map.of("country", country, "currency", reference.currency(), "amount", reference.premiumAmount()));
        }
        model.addAttribute("references", references);
        model.addAttribute("plans", Arrays.stream(PlanType.values()).filter(PlanType::isBillable).toList());
        model.addAttribute("prices", catalog.findAllByOrderByCountryCodeAscPlanAsc().stream()
                .filter(p -> p.getProvider() == selected.provider()).toList());
        return "backoffice/dodo-test";
    }
    @PostMapping("/backoffice/payments/{dodoMode:dodo|dodo-test}/bind")
    @Transactional
    public String bind(@PathVariable String dodoMode, @RequestParam String actionToken, @RequestParam PlanType plan, @RequestParam String countryCode,
                       @RequestParam String productId, @RequestParam String currency, @RequestParam BigDecimal amount,
                       HttpSession session, RedirectAttributes flash) {
        DodoGateway selected = dodoMode.equals("dodo-test") ? gateway : live;
        token(session, actionToken, dodoMode.equals("dodo-test") ? "dodoTestActionToken" : "dodoLiveActionToken");
        try {
            String country = countryCode.strip().toUpperCase(Locale.ROOT);
            if (!country.matches("[A-Z]{2}") || country.equals("BR") || !plan.isBillable())
                throw new IllegalArgumentException("Selecione um plano pago e um país fora do Brasil.");
            var price = new PaymentCatalogPrice();
            price.setProvider(selected.provider()); price.setEnvironment(selected.environment());
            price.setCountryCode(country); price.setPlan(plan); price.setProviderPriceId(productId.strip());
            price.setCurrency(currency.strip().toUpperCase(Locale.ROOT)); price.setAmountValue(amount);
            price.setTaxMode("external"); price.setRecurring(true); price.setEnabled(false);
            // Validate a detached candidate first: a failure must preserve an existing working binding.
            selected.validateCatalog(price);
            var existing = catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(
                    selected.provider(), selected.environment(), country, plan).orElse(price);
            existing.setProviderPriceId(price.getProviderPriceId()); existing.setCurrency(price.getCurrency());
            existing.setAmountValue(price.getAmountValue()); existing.setTaxMode(price.getTaxMode()); existing.setRecurring(true);
            existing.setEnabled(false); existing.setValidationError(null); existing.setValidatedAt(java.time.Instant.now());
            catalog.save(existing);
            flash.addFlashAttribute("message", "Produto Dodo validado e vinculado. Habilite o preço em Pagamentos; no live, habilite também a rota do país.");
        } catch (RuntimeException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/backoffice/payments/" + dodoMode;
    }
    private static void token(HttpSession session, String supplied, String key) {
        if (!(session.getAttribute(key) instanceof String expected) || supplied == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Abra a página Dodo de teste novamente.");
    }
}
