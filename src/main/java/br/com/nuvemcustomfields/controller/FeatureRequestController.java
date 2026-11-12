package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.*;
import br.com.nuvemcustomfields.i18n.Messages;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.UUID;

@Controller
public class FeatureRequestController {
    private static final String TOKEN="featureRequestSubmissionToken";
    private static final String BACKOFFICE_TOKEN="featureRequestResponseCsrf";
    private final FeatureRequestService requests;
    private final AdminStoreService stores;
    private final Messages messages;
    public FeatureRequestController(FeatureRequestService requests,AdminStoreService stores,Messages messages){this.requests=requests;this.stores=stores;this.messages=messages;}
    @GetMapping("/admin/suggestions")
    public String form(@RequestParam(required=false) String interest,@RequestParam(required=false) Long requestId,HttpSession session,Model model,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");var store=stores.requireCurrentStore(session);
        String token=UUID.randomUUID().toString();session.setAttribute(TOKEN,token);
        model.addAttribute("store",store);model.addAttribute("submissionToken",token);model.addAttribute("suggestions",requests.forStore(store.getStoreId(),requestId));
        if (java.util.Set.of("uploads", "templates", "bulk").contains(interest == null ? "" : interest)) {
            String feature = messages.get("merchant.comingsoon." + interest + ".title");
            if (!model.containsAttribute("draftTitle")) model.addAttribute("draftTitle", messages.get("merchant.comingsoon.request.title", feature));
            if (!model.containsAttribute("draftDescription")) model.addAttribute("draftDescription", messages.get("merchant.comingsoon.request.description", feature));
        }
        return "admin/suggestions";
    }
    @PostMapping("/admin/suggestions")
    public String submit(@RequestParam String title,@RequestParam String description,@RequestParam String submissionToken,HttpSession session,RedirectAttributes flash){
        var store=stores.requireCurrentStore(session);
        if(!submissionToken.equals(session.getAttribute(TOKEN)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Formulário inválido. Abra a página novamente.");
        try{requests.submit(store.getStoreId(),title,description,submissionToken);flash.addFlashAttribute("message",messages.get("suggestions.success"));}
        catch(IllegalArgumentException ex){flash.addFlashAttribute("error",messages.get("suggestions.invalid"));flash.addFlashAttribute("draftTitle",title);flash.addFlashAttribute("draftDescription",description);}
        return "redirect:/admin/suggestions";
    }
    @GetMapping("/backoffice/suggestions")
    public String backoffice(Model model,HttpServletResponse response,HttpSession session){
        response.setHeader("Cache-Control","no-store");
        if(session.getAttribute(BACKOFFICE_TOKEN)==null)session.setAttribute(BACKOFFICE_TOKEN,UUID.randomUUID().toString());
        var recent=requests.recent();var tokens=new java.util.HashMap<Long,String>();recent.forEach(r->tokens.put(r.getId(),UUID.randomUUID().toString()));
        model.addAttribute("suggestions",recent);model.addAttribute("updateTokens",tokens);model.addAttribute("csrfToken",session.getAttribute(BACKOFFICE_TOKEN));
        model.addAttribute("statuses",br.com.nuvemcustomfields.entity.FeatureRequestStatus.values());
        return "backoffice/suggestions";
    }
    @PostMapping("/backoffice/suggestions/{id}/respond")
    public String respond(@PathVariable Long id,@RequestParam br.com.nuvemcustomfields.entity.FeatureRequestStatus status,
            @RequestParam(defaultValue="") String message,@RequestParam long revision,@RequestParam String submissionToken,
            @RequestParam String csrfToken,HttpSession session,RedirectAttributes flash) {
        if(!csrfToken.equals(session.getAttribute(BACKOFFICE_TOKEN)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Formulário inválido. Abra a página novamente.");
        try{requests.respond(id,status,message,revision,submissionToken);flash.addFlashAttribute("message","Atualização publicada. Respostas por e-mail são enviadas pelo sistema em até um minuto, quando o envio estiver configurado.");}
        catch(IllegalArgumentException ex){flash.addFlashAttribute("error",ex.getMessage());flash.addFlashAttribute("draftRequestId",id);flash.addFlashAttribute("draftMessage",message);}
        return "redirect:/backoffice/suggestions#suggestion-"+id;
    }
}
