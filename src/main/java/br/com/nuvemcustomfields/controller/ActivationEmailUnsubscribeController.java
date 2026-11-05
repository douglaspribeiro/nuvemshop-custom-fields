package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.ActivationEmailProtectionService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/activation-emails/unsubscribe")
public class ActivationEmailUnsubscribeController {
    private final ActivationEmailProtectionService protection;
    public ActivationEmailUnsubscribeController(ActivationEmailProtectionService protection) { this.protection = protection; }
    @ModelAttribute
    void privacy(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
        response.setHeader("Content-Security-Policy", "default-src 'none'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
    }
    // GET only confirms: scanners and link previews must not unsubscribe recipients.
    @GetMapping
    public String confirm(@RequestParam String token, Model model) {
        model.addAttribute("es", protection.spanish(token));
        model.addAttribute("token", token);
        model.addAttribute("done", false);
        return "activation-emails/unsubscribe";
    }
    // Also supports RFC 8058 one-click POST from List-Unsubscribe-Post.
    @PostMapping
    public String unsubscribe(@RequestParam String token, Model model) {
        boolean es = protection.spanish(token);
        protection.unsubscribe(token);
        model.addAttribute("es", es);
        model.addAttribute("done", true);
        return "activation-emails/unsubscribe";
    }
}
