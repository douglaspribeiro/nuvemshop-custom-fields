package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.service.CreemCatalogService;
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
public class BackofficeCreemPlansController {
    private final CreemCatalogService catalog;
    private final BackofficeProperties backoffice;
    public BackofficeCreemPlansController(CreemCatalogService catalog, BackofficeProperties backoffice) {
        this.catalog = catalog; this.backoffice = backoffice;
    }
    @PostMapping("/backoffice/plans/creem/create")
    public String create(@RequestParam String actionToken, @RequestParam PlanType plan,
            @RequestParam PaymentEnvironment environment, @RequestParam String countryCode,
            @RequestParam String name, @RequestParam String description, @RequestParam String currency,
            @RequestParam BigDecimal amount, @RequestParam String taxMode, HttpSession session, RedirectAttributes flash) {
        token(session, actionToken);
        try {
            String id = catalog.create(plan, environment, countryCode, name, description, currency, amount, taxMode, backoffice.username());
            flash.addFlashAttribute("message", "Produto Creem criado e código salvo: " + id + ". Habilite o preço e a rota em Pagamentos quando estiver pronto.");
        } catch (RuntimeException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/backoffice/plans#creem";
    }
    @PostMapping("/backoffice/plans/creem/{id}/resume")
    public String resume(@PathVariable Long id, @RequestParam String actionToken, HttpSession session, RedirectAttributes flash) {
        token(session, actionToken);
        try { flash.addFlashAttribute("message", "Código Creem salvo: " + catalog.resume(id)); }
        catch (RuntimeException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/backoffice/plans#creem";
    }
    @PostMapping("/backoffice/plans/creem/bind")
    public String bind(@RequestParam String actionToken, @RequestParam PlanType plan,
            @RequestParam PaymentEnvironment environment, @RequestParam String countryCode,
            @RequestParam String currency, @RequestParam BigDecimal amount, @RequestParam String taxMode,
            @RequestParam String productId, HttpSession session, RedirectAttributes flash) {
        token(session, actionToken);
        try {
            catalog.bind(plan, environment, countryCode, currency, amount, taxMode, productId);
            flash.addFlashAttribute("message", "Produto Creem validado e código salvo. Habilite o preço em Pagamentos quando estiver pronto.");
        } catch (RuntimeException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/backoffice/plans#creem";
    }
    private static void token(HttpSession session, String supplied) {
        if (!(session.getAttribute("planActionToken") instanceof String expected) || supplied == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Abra a página de planos novamente.");
    }
}
