package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.service.StripeCatalogService;
import br.com.nuvemcustomfields.properties.BackofficeProperties;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Controller
public class BackofficeStripePlansController {
    private final StripeCatalogService catalog;
    private final BackofficeProperties backoffice;
    public BackofficeStripePlansController(StripeCatalogService catalog, BackofficeProperties backoffice) {
        this.catalog = catalog; this.backoffice = backoffice;
    }
    @PostMapping("/backoffice/plans/stripe/create")
    public String create(@RequestParam String actionToken, @RequestParam PlanType plan,
            @RequestParam PaymentEnvironment environment, @RequestParam String name, @RequestParam String description,
            @RequestParam String currency, @RequestParam BigDecimal amount, HttpSession session, RedirectAttributes flash) {
        token(session, actionToken);
        try {
            String priceId = catalog.create(plan, environment, name, description, currency, amount, backoffice.username());
            flash.addFlashAttribute("message", "Produto e preço mensal Stripe criados. ID salvo: " + priceId
                    + ". Habilite o preço e a rota da Argentina em Pagamentos quando estiver pronto.");
        } catch (RuntimeException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/backoffice/plans#stripe";
    }
    @PostMapping("/backoffice/plans/stripe/{id}/resume")
    public String resume(@PathVariable Long id, @RequestParam String actionToken, HttpSession session, RedirectAttributes flash) {
        token(session, actionToken);
        try { flash.addFlashAttribute("message", "Preço Stripe validado e salvo: " + catalog.resume(id)); }
        catch (RuntimeException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/backoffice/plans#stripe";
    }
    @PostMapping("/backoffice/plans/stripe/bind")
    public String bind(@RequestParam String actionToken, @RequestParam PlanType plan,
            @RequestParam PaymentEnvironment environment, @RequestParam String currency, @RequestParam BigDecimal amount,
            @RequestParam String priceId, HttpSession session, RedirectAttributes flash) {
        token(session, actionToken);
        try {
            catalog.bind(plan, environment, currency, amount, priceId);
            flash.addFlashAttribute("message", "Preço Stripe validado e salvo. Habilite o preço e a rota da Argentina em Pagamentos.");
        } catch (RuntimeException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/backoffice/plans#stripe";
    }
    private static void token(HttpSession session, String supplied) {
        if (!(session.getAttribute("planActionToken") instanceof String expected) || supplied == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Abra a página de planos novamente.");
    }
}
