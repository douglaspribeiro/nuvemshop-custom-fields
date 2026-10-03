package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.StoreErasureRequestService;
import br.com.nuvemcustomfields.service.WinbackCampaignService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@Controller
public class BackofficeStoreDepartureController {
    public static final String TOKEN_KEY = "storeDepartureActionToken";
    private final StoreErasureRequestService requests;
    private final WinbackCampaignService campaigns;
    private final boolean mailEnabled;
    public BackofficeStoreDepartureController(StoreErasureRequestService requests, WinbackCampaignService campaigns,
            @Value("${winback.mail-enabled:false}") boolean mailEnabled) {
        this.requests = requests; this.campaigns = campaigns; this.mailEnabled = mailEnabled;
    }
    public static String token(HttpSession session) {
        if (!(session.getAttribute(TOKEN_KEY) instanceof String)) session.setAttribute(TOKEN_KEY, UUID.randomUUID().toString());
        return (String) session.getAttribute(TOKEN_KEY);
    }
    private void validate(HttpSession session, String supplied) {
        Object saved = session.getAttribute(TOKEN_KEY);
        if (!(saved instanceof String expected) || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Abra a lista novamente antes de executar esta ação.");
    }
    @PostMapping("/backoffice/stores/{storeId}/erase")
    public String erase(@PathVariable Long storeId, @RequestParam String actionToken,
            @RequestParam(defaultValue="") String confirmation, HttpSession session, RedirectAttributes flash) {
        validate(session, actionToken);
        if (!"APAGAR".equals(confirmation)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirme a exclusão definitiva.");
        requests.eraseManually(storeId);
        flash.addFlashAttribute("message", "Dados da loja excluídos definitivamente. O total anônimo de saídas foi preservado.");
        return "redirect:/backoffice/stores?status=uninstalled";
    }
    @PostMapping("/backoffice/stores/{storeId}/departure-reason")
    public String reason(@PathVariable Long storeId, @RequestParam String actionToken,
            @RequestParam String reason, @RequestParam(defaultValue="") String justification,
            HttpSession session, RedirectAttributes flash) {
        validate(session, actionToken);
        requests.recordReason(storeId, reason, justification);
        flash.addFlashAttribute("message", "Motivo e justificativa registrados manualmente.");
        return "redirect:/backoffice/stores?status=uninstalled";
    }
    @PostMapping("/backoffice/stores/{storeId}/winback")
    public String winback(@PathVariable Long storeId, @RequestParam String actionToken,
            @RequestParam(defaultValue="false") boolean authorizeErasureContact,
            HttpSession session, RedirectAttributes flash) {
        validate(session, actionToken);
        try {
            if (!mailEnabled) throw new IllegalStateException("Envio de reconquista desativado neste ambiente.");
            String emailId = campaigns.prepareManual(storeId, authorizeErasureContact);
            if (emailId == null) flash.addFlashAttribute("message", "A reconquista deste ciclo já foi solicitada. Não enviamos novamente.");
            else {
                campaigns.send(emailId);
                flash.addFlashAttribute("message", "Solicitação de reconquista processada. Confira o status em Reconquistas.");
            }
        } catch (Exception ex) {
            flash.addFlashAttribute("error", "Não foi possível concluir o envio. Confira a elegibilidade da loja, a configuração de e-mail e o status em Reconquistas antes de tentar novamente.");
        }
        return "redirect:/backoffice/stores?status=uninstalled";
    }
}
