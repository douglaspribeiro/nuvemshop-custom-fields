package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.StoreAdoptionReportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.Set;

@Controller
public class StoreAdoptionReportController {
    private final StoreAdoptionReportService reports;
    public StoreAdoptionReportController(StoreAdoptionReportService reports) { this.reports=reports; }

    @GetMapping("/backoffice/reports/adoption")
    public String adoption(@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="") String status,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            Model model,HttpServletResponse response) {
        if(!Set.of("","active","uninstalled","erasure").contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Situação inválida.");
        if(from!=null && to!=null && to.isBefore(from))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A data final deve ser igual ou posterior à inicial.");
        response.setHeader("Cache-Control","no-store");
        model.addAttribute("adoption",reports.report(from,to,q,status));
        model.addAttribute("query",q);model.addAttribute("selectedStatus",status);
        model.addAttribute("from",from);model.addAttribute("to",to);
        return "backoffice/adoption-report";
    }
}
