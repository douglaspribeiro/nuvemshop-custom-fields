package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.service.PlanCatalogService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class BackofficePlansController {
    private static final String TOKEN_KEY = "planActionToken";
    private final PlanCatalogService catalog;

    public BackofficePlansController(PlanCatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/backoffice/plans")
    public String list(Model model, HttpSession session, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        if (session.getAttribute(TOKEN_KEY) == null) session.setAttribute(TOKEN_KEY, UUID.randomUUID().toString());
        model.addAttribute("actionToken", session.getAttribute(TOKEN_KEY));
        model.addAttribute("plans", catalog.allVersions());
        model.addAttribute("planTypes", PlanType.values());
        model.addAttribute("today", catalog.today());
        model.addAttribute("currentPlanIds", catalog.activePlansByType().values().stream()
                .map(p -> p.getId()).collect(Collectors.toSet()));
        return "backoffice/plans";
    }

    @PostMapping("/backoffice/plans")
    public String save(@RequestParam String actionToken, @RequestParam PlanType planType,
            @RequestParam String displayName, @RequestParam(defaultValue = "") String description,
            @RequestParam(defaultValue = "") String billingExternalId, @RequestParam String currency,
            @RequestParam BigDecimal amount, @RequestParam long productLimit, @RequestParam long fieldLimit,
            @RequestParam long imageProductLimit, @RequestParam int imageOptionLimit,
            @RequestParam LocalDate effectiveFrom, HttpSession session, RedirectAttributes flash) {
        if (!(session.getAttribute(TOKEN_KEY) instanceof String expected)
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actionToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Abra a página de planos novamente.");
        }
        try {
            catalog.createVersion(planType, displayName, description, billingExternalId, currency,
                    amount, productLimit, fieldLimit, imageProductLimit, imageOptionLimit, effectiveFrom, null);
            flash.addFlashAttribute("message", "Versão criada. Os limites entram em vigor na data indicada.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/backoffice/plans";
    }
}
