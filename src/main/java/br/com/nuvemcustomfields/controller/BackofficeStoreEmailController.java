package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.StoreAdminEmailService;
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
public class BackofficeStoreEmailController {
    private static final String TOKEN_KEY = "storeAdminEmailToken";
    private final StoreAdminEmailService emails;

    public BackofficeStoreEmailController(StoreAdminEmailService emails) { this.emails = emails; }

    public static String token(HttpSession session) {
        if (!(session.getAttribute(TOKEN_KEY) instanceof String))
            session.setAttribute(TOKEN_KEY, UUID.randomUUID().toString());
        return (String) session.getAttribute(TOKEN_KEY);
    }

    @PostMapping("/backoffice/stores/{storeId}/email")
    public String send(@PathVariable Long storeId, @RequestParam String actionToken,
            @RequestParam String subject, @RequestParam String body,
            HttpSession session, RedirectAttributes flash) {
        synchronized (session) {
            Object saved = session.getAttribute(TOKEN_KEY);
            if (!(saved instanceof String expected) || !MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), actionToken.getBytes(StandardCharsets.UTF_8)))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reabra o detalhe da loja antes de enviar.");
            // Consume before SMTP so repeated submissions cannot send the same form twice.
            session.removeAttribute(TOKEN_KEY);
        }
        try {
            emails.send(storeId, subject, body);
            flash.addFlashAttribute("message", "E-mail enviado ao administrador da loja.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            flash.addFlashAttribute("emailSubject", subject);
            flash.addFlashAttribute("emailBody", body);
        }
        return "redirect:/backoffice/stores/" + storeId + "#store-email";
    }
}
