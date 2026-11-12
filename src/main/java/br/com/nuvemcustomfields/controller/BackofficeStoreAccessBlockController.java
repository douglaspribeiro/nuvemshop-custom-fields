package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.StoreAccessBlockService;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BackofficeStoreAccessBlockController {
    private final StoreAccessBlockService blocks;
    public BackofficeStoreAccessBlockController(StoreAccessBlockService blocks) { this.blocks = blocks; }

    @PostMapping("/backoffice/stores/{storeId}/force-uninstall")
    public String block(@PathVariable Long storeId, @RequestParam String actionToken,
            @RequestParam String confirmation, HttpSession session, RedirectAttributes flash) {
        Object token = session.getAttribute(BackofficeStoreDepartureController.TOKEN_KEY);
        if (!(token instanceof String expected) || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actionToken.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!String.valueOf(storeId).equals(confirmation.strip()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Digite o ID da loja para confirmar.");
        blocks.forceUninstall(storeId);
        flash.addFlashAttribute("message", "Acesso encerrado e reinstalação bloqueada permanentemente para este ID.");
        return "redirect:/backoffice/stores/" + storeId;
    }
}
