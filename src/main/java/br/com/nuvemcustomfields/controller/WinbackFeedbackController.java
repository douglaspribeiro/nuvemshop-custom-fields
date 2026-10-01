package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.WinbackCampaignService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/winback")
public class WinbackFeedbackController {
    private final WinbackCampaignService service;
    public WinbackFeedbackController(WinbackCampaignService service) { this.service = service; }
    @ModelAttribute
    public void privateResponse(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
    }
    @GetMapping("/{id}")
    public String feedback(@PathVariable String id, Model model) {
        var mail = service.publicEmail(id);
        model.addAttribute("emailId", id);
        model.addAttribute("coupon", service.publicCoupon(id));
        model.addAttribute("expired", mail.getCreatedAt().plus(30, java.time.temporal.ChronoUnit.DAYS)
                .isBefore(java.time.Instant.now()));
        return "winback/feedback";
    }
    @PostMapping("/{id}")
    public String respond(@PathVariable String id, @RequestParam(defaultValue = "") String reason,
            @RequestParam(defaultValue = "") String response, @RequestParam(defaultValue = "false") boolean optOut,
            Model model) {
        service.respond(id, reason, response, optOut);
        model.addAttribute("optedOut", optOut);
        model.addAttribute("coupon", optOut ? null : service.publicCoupon(id));
        return "winback/thanks";
    }
}
