package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.MerchantInterfacePreference.Choice;
import br.com.nuvemcustomfields.service.MerchantInterfaceService;
import br.com.nuvemcustomfields.service.PlanLimitService;
import br.com.nuvemcustomfields.i18n.Messages;
import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.service.SupportService;
import br.com.nuvemcustomfields.repository.StoreRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
public class PublicPagesController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicPagesController.class);

    private final StoreRepository storeRepository;
    private final SupportService supportService;
    private final Messages messages;
    private final PlanLimitService planLimits;
    private final MerchantInterfaceService merchantInterface;

    public PublicPagesController(StoreRepository storeRepository, SupportService supportService, Messages messages,
                                 PlanLimitService planLimits, MerchantInterfaceService merchantInterface) {
        this.storeRepository = storeRepository;
        this.supportService = supportService;
        this.messages = messages;
        this.planLimits = planLimits;
        this.merchantInterface = merchantInterface;
    }

    @GetMapping("/privacy")
    public String privacyAlias() {
        return "redirect:/privacy/";
    }

    @GetMapping("/privacy/")
    public String privacy() {
        return "public/privacy";
    }

    @GetMapping("/precos")
    public String pricingAlias() { return "redirect:/precos/"; }

    @GetMapping("/precos/")
    public String pricing(Model model) {
        model.addAttribute("planDefinitions", planLimits.planDefinitions());
        return "public/pricing";
    }

    @GetMapping("/termos")
    public String termsAlias() { return "redirect:/termos/"; }

    @GetMapping("/termos/")
    public String terms() { return "public/terms"; }

    @GetMapping("/contato")
    public String contactAlias() { return "redirect:/contato/"; }

    @GetMapping("/contato/")
    public String contact() { return "public/contact"; }

    @GetMapping("/support")
    public String supportAlias() {
        return "redirect:/support/";
    }

    @GetMapping("/support/")
    public String support(@RequestParam(name = "app", required = false) String app, HttpSession session, Model model) {
        Store store = currentStore(session);
        populateSupportModel(model, store, app);
        model.addAttribute("tickets", store == null ? List.of() : supportService.ticketsForStore(store.getStoreId()));
        return "public/support";
    }

    @PostMapping("/support/tickets")
    public String openTicket(
            @RequestParam String subject,
            @RequestParam String message,
            @RequestParam(name = "attachment", required = false) MultipartFile attachment,
            @RequestParam(name = "app", required = false) String app,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        Store store = currentStore(session);
        if (store == null) {
            redirectAttributes.addFlashAttribute("error", messages.get("support.flash.unauthenticated"));
            return "redirect:/support/";
        }
        preserveSupportApp(redirectAttributes, app);
        try {
            requireAttachmentAccess(store, attachment);
            var ticket = supportService.openTicket(store, subject, message, SupportAttachmentWeb.merchantAttachment(attachment));
            LOGGER.info("support.ticket.created ticket_id={} store_id={}", ticket.getId(), store.getStoreId());
            redirectAttributes.addFlashAttribute("message", messages.get("support.flash.sent"));
            return "redirect:/support/tickets/" + ticket.getId();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (SupportAttachmentWeb.AttachmentTooLarge ex) {
            redirectAttributes.addFlashAttribute("error", messages.get("support.attachment.tooLarge"));
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("error", messages.get("support.attachment.readError"));
        }
        if (subject.length() <= 160) {
            redirectAttributes.addFlashAttribute("supportSubject", subject);
        }
        preserveDraft(redirectAttributes, message);
        return "redirect:/support/";
    }

    @GetMapping("/support/tickets/{ticketId}")
    public String ticket(@PathVariable Long ticketId, @RequestParam(name = "app", required = false) String app,
                         HttpSession session, Model model) {
        Store store = currentStore(session);
        if (store == null) {
            return "redirect:/support/";
        }
        var ticket = supportService.requireStoreTicket(ticketId, store.getStoreId());
        populateSupportModel(model, store, app);
        model.addAttribute("ticket", ticket);
        model.addAttribute("messages", supportService.messages(ticketId));
        model.addAttribute("attachmentsByMessage", supportService.attachmentsByMessage(ticketId));
        return "public/support-ticket";
    }

    @GetMapping("/support/tickets/{ticketId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> supportAttachment(@PathVariable Long ticketId, @PathVariable Long attachmentId,
                                                    HttpSession session) {
        Store store = currentStore(session);
        if (store == null) {
            return ResponseEntity.notFound().build();
        }
        return supportService.storeAttachment(ticketId, attachmentId, store.getStoreId())
                .map(SupportAttachmentWeb::download)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/support/tickets/{ticketId}/messages")
    public String reply(
            @PathVariable Long ticketId,
            @RequestParam String message,
            @RequestParam(name = "attachment", required = false) MultipartFile attachment,
            @RequestParam(name = "app", required = false) String app,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        Store store = currentStore(session);
        if (store == null) {
            redirectAttributes.addFlashAttribute("error", messages.get("support.flash.unauthenticated"));
            return "redirect:/support/";
        }
        preserveSupportApp(redirectAttributes, app);
        try {
            requireAttachmentAccess(store, attachment);
            supportService.replyFromStore(ticketId, store.getStoreId(), message, SupportAttachmentWeb.merchantAttachment(attachment));
            redirectAttributes.addFlashAttribute("message", messages.get("support.flash.replied"));
            return "redirect:/support/tickets/" + ticketId;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (SupportAttachmentWeb.AttachmentTooLarge ex) {
            redirectAttributes.addFlashAttribute("error", messages.get("support.attachment.tooLarge"));
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("error", messages.get("support.attachment.readError"));
        }
        preserveDraft(redirectAttributes, message);
        return "redirect:/support/tickets/" + ticketId;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", messages.get("support.attachment.tooLarge"));
        return "redirect:/support/";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String invalidTicket(IllegalArgumentException ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        LOGGER.warn("support.ticket.invalid message={}", ex.getMessage());
        redirectAttributes.addFlashAttribute("error", messages.get("support.flash.notfound"));
        preserveSupportApp(redirectAttributes, request.getParameter("app"));
        return "redirect:/support/";
    }

    private void requireAttachmentAccess(Store store, MultipartFile attachment) {
        long storeId = store.getStoreId();
        if (attachment != null && !attachment.isEmpty()
                && (!merchantInterface.isEligible(storeId) || merchantInterface.getChoice(storeId).orElse(null) != Choice.PREVIEW)) {
            throw new IllegalArgumentException(messages.get("support.attachment.previewOnly"));
        }
    }

    private void populateSupportModel(Model model, Store store, String app) {
        model.addAttribute("store", store);
        model.addAttribute("supportInComplements", store != null && "complements".equals(app)
                && Boolean.TRUE.equals(model.getAttribute("productComplementsVisible")));
    }

    private void preserveSupportApp(RedirectAttributes redirectAttributes, String app) {
        if ("complements".equals(app)) {
            redirectAttributes.addAttribute("app", app);
        }
    }

    private void preserveDraft(RedirectAttributes redirectAttributes, String message) {
        if (message.length() <= 5000) {
            redirectAttributes.addFlashAttribute("supportDraft", message);
        }
    }

    private Store currentStore(HttpSession session) {
        Object storeId = session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY);
        if (storeId instanceof Long id) {
            return storeRepository.findActiveByStoreId(id).orElse(null);
        }
        return null;
    }

}
