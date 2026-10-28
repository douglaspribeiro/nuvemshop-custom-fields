package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.UserJourneyAuditReportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class BackofficeAuditController {
    private final UserJourneyAuditReportService reports;
    public BackofficeAuditController(UserJourneyAuditReportService reports) { this.reports = reports; }
    @GetMapping("/backoffice/audit")
    public String audit(@RequestParam(required=false) Long storeId, @RequestParam(required=false) String outcome,
            @RequestParam(required=false) String actor, @RequestParam(defaultValue="0") int page,
            Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        outcome = blankToNull(outcome); actor = blankToNull(actor);
        model.addAttribute("events", reports.report(storeId, outcome, actor, page));
        model.addAttribute("storeId", storeId); model.addAttribute("outcome", outcome); model.addAttribute("actor", actor);
        model.addAttribute("outcomes", reports.outcomes()); model.addAttribute("actors", reports.actors());
        return "backoffice/audit";
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
}
