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
    private final FeatureRequestService requests;
    private final AdminStoreService stores;
    private final Messages messages;
    public FeatureRequestController(FeatureRequestService requests,AdminStoreService stores,Messages messages){this.requests=requests;this.stores=stores;this.messages=messages;}
    @GetMapping("/admin/suggestions")
    public String form(HttpSession session,Model model,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");var store=stores.requireCurrentStore(session);
        String token=UUID.randomUUID().toString();session.setAttribute(TOKEN,token);
        model.addAttribute("store",store);model.addAttribute("submissionToken",token);model.addAttribute("suggestions",requests.forStore(store.getStoreId()));
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
    public String backoffice(Model model,HttpServletResponse response){response.setHeader("Cache-Control","no-store");model.addAttribute("suggestions",requests.recent());return "backoffice/suggestions";}
}
