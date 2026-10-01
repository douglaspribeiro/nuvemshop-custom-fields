package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.repository.SupportTicketRepository;
import br.com.nuvemcustomfields.service.WinbackTrackingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/backoffice/winback")
public class BackofficeWinbackController {
    private final WinbackTrackingService tracking;
    private final SupportTicketRepository tickets;
    private final br.com.nuvemcustomfields.service.WinbackCampaignService campaigns;
    public BackofficeWinbackController(WinbackTrackingService tracking, SupportTicketRepository tickets,
            br.com.nuvemcustomfields.service.WinbackCampaignService campaigns) {
        this.tracking = tracking; this.tickets = tickets;
        this.campaigns = campaigns;
    }
    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "") String stage,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            Model model) {
        var campaigns = tracking.list(page, q, stage, from, to);
        model.addAttribute("page", campaigns);
        model.addAttribute("query", q);
        model.addAttribute("stage", stage);
        model.addAttribute("fromDate", from);
        model.addAttribute("toDate", to);
        model.addAttribute("rows", campaigns.getContent().stream().map(c -> tracking.detail(c.getId())).toList());
        return "backoffice/winback";
    }
    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        var detail = tracking.detail(id);
        model.addAttribute("detail", detail);
        // Store tickets are contextual: they are not attributed to an email click.
        model.addAttribute("tickets", tickets.findByStoreIdOrderByLastMessageAtDesc(detail.campaign().getStoreId()));
        return "backoffice/winback-detail";
    }
    @PostMapping("/{id}/feature-status")
    public String featureStatus(@PathVariable String id, @RequestParam String status) {
        campaigns.featureStatus(id, status);
        return "redirect:/backoffice/winback/" + id;
    }
}
