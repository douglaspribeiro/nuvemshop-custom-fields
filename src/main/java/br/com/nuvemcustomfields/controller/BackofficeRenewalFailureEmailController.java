package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.RenewalFailureEmailService;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BackofficeRenewalFailureEmailController {
    private final RenewalFailureEmailService emails;
    public BackofficeRenewalFailureEmailController(RenewalFailureEmailService emails) { this.emails = emails; }

    public static String token(HttpSession session, Long storeId, String fingerprint) {
        String key = "renewalFailureEmail:" + storeId;
        synchronized (session) {
            if (!(session.getAttribute(key) instanceof String saved) || !saved.startsWith(fingerprint + ":"))
                session.setAttribute(key, fingerprint + ":" + UUID.randomUUID());
            return (String) session.getAttribute(key);
        }
    }

    @PostMapping("/backoffice/stores/{storeId}/payment/renewal-failure-email")
    public String send(@PathVariable Long storeId, @RequestParam String actionToken,
            @RequestParam String fingerprint, HttpSession session, RedirectAttributes flash) {
        String key = "renewalFailureEmail:" + storeId;
        synchronized (session) {
            if (!(session.getAttribute(key) instanceof String expected) || !expected.startsWith(fingerprint + ":")
                    || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actionToken.getBytes(StandardCharsets.UTF_8)))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reabra o detalhe da loja antes de enviar.");
            session.removeAttribute(key);
        }
        try {
            emails.send(storeId, fingerprint);
            flash.addFlashAttribute("message", "Aviso de falha na renovação enviado ao e-mail cadastrado da loja.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/backoffice/stores/" + storeId + "#renewal-failure-email";
    }
}
