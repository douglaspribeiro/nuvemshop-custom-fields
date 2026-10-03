package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.payment.EfiGateway;
import br.com.nuvemcustomfields.payment.PaymentGatewayException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class BackofficeEfiPlansController {
    private final EfiGateway efi;

    public BackofficeEfiPlansController(EfiGateway efi) { this.efi = efi; }

    @GetMapping("/backoffice/payments/efi/plans")
    public String plans(@RequestParam(defaultValue = "0") int offset, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        int safeOffset = Math.max(0, Math.min(offset, Integer.MAX_VALUE - 50));
        model.addAttribute("environment", efi.environment());
        model.addAttribute("offset", safeOffset);
        model.addAttribute("plans", List.of());
        model.addAttribute("hasNext", false);
        try {
            var plans = efi.listPlans(safeOffset);
            model.addAttribute("plans", plans);
            model.addAttribute("hasNext", plans.size() == 50);
        } catch (PaymentGatewayException ex) {
            model.addAttribute("error", "Não foi possível consultar os planos. Confira se a Efí está habilitada, as credenciais e a permissão da API de Emissão de cobranças no ambiente indicado.");
        }
        return "backoffice/efi-plans";
    }
}
