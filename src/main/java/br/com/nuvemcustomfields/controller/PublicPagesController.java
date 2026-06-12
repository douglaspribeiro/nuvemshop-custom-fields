package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.service.SupportService;
import br.com.nuvemcustomfields.repository.StoreRepository;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PublicPagesController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicPagesController.class);

    private final StoreRepository storeRepository;
    private final SupportService supportService;

    public PublicPagesController(StoreRepository storeRepository, SupportService supportService) {
        this.storeRepository = storeRepository;
        this.supportService = supportService;
    }

    @GetMapping("/privacy")
    public String privacyAlias() {
        return "redirect:/privacy/";
    }

    @GetMapping("/privacy/")
    public String privacy() {
        return "public/privacy";
    }

    @GetMapping("/support")
    public String supportAlias() {
        return "redirect:/support/";
    }

    @GetMapping("/support/")
    public String support(HttpSession session, Model model) {
        Store store = currentStore(session);
        model.addAttribute("store", store);
        model.addAttribute("tickets", store == null ? java.util.List.of() : supportService.ticketsForStore(store.getStoreId()));
        return "public/support";
    }

    @PostMapping("/support/tickets")
    public String openTicket(
            @RequestParam String subject,
            @RequestParam String message,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        Store store = currentStore(session);
        if (store == null) {
            redirectAttributes.addFlashAttribute("error", "Instale ou reconecte o aplicativo para acessar o suporte.");
            return "redirect:/support/";
        }
        try {
            var ticket = supportService.openTicket(store, subject, message);
            LOGGER.info("support.ticket.created ticket_id={} store_id={}", ticket.getId(), store.getStoreId());
            redirectAttributes.addFlashAttribute("message", "Mensagem enviada ao suporte.");
            return "redirect:/support/tickets/" + ticket.getId();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/support/";
        }
    }

    @GetMapping("/support/tickets/{ticketId}")
    public String ticket(@PathVariable Long ticketId, HttpSession session, Model model) {
        Store store = currentStore(session);
        if (store == null) {
            return "redirect:/support/";
        }
        var ticket = supportService.requireStoreTicket(ticketId, store.getStoreId());
        model.addAttribute("store", store);
        model.addAttribute("ticket", ticket);
        model.addAttribute("messages", supportService.messages(ticketId));
        return "public/support-ticket";
    }

    @PostMapping("/support/tickets/{ticketId}/messages")
    public String reply(
            @PathVariable Long ticketId,
            @RequestParam String message,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        Store store = currentStore(session);
        if (store == null) {
            redirectAttributes.addFlashAttribute("error", "Instale ou reconecte o aplicativo para acessar o suporte.");
            return "redirect:/support/";
        }
        try {
            supportService.replyFromStore(ticketId, store.getStoreId(), message);
            redirectAttributes.addFlashAttribute("message", "Resposta enviada.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/support/tickets/" + ticketId;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String invalidTicket(IllegalArgumentException ex, RedirectAttributes redirectAttributes) {
        LOGGER.warn("support.ticket.invalid message={}", ex.getMessage());
        redirectAttributes.addFlashAttribute("error", "Chamado nao encontrado para esta loja.");
        return "redirect:/support/";
    }

    private Store currentStore(HttpSession session) {
        Object storeId = session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY);
        if (storeId instanceof Long id) {
            return storeRepository.findActiveByStoreId(id).orElse(null);
        }
        return null;
    }

}
