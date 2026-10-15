package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.ManagementReportService;
import br.com.nuvemcustomfields.service.PlanPerformanceReportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.time.*;

@Controller
public class ManagementReportController {
    private final ManagementReportService management;
    private final PlanPerformanceReportService performance;
    public ManagementReportController(ManagementReportService management, PlanPerformanceReportService performance) {
        this.management = management; this.performance = performance;
    }

    @GetMapping("/backoffice/reports")
    public String reports(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model, HttpServletResponse response) {
        var range = range(from, to);
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("report", management.report());
        model.addAttribute("performance", performance.report(range[0], range[1]));
        return "backoffice/reports";
    }

    @GetMapping("/backoffice/reports/free.csv")
    public void export(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletResponse response) throws IOException {
        var range = range(from, to);
        var report = performance.report(range[0], range[1]);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=lojas-gratuitas.csv");
        var writer = response.getWriter();
        writer.write("\uFEFFID;Loja;Plano atual;Produtos habilitados;Pedidos personalizados;Itens personalizados;Histórico;Início;Fim\r\n");
        for (var row : report.freeDetails()) {
            writer.write(row.id() + ";" + cell(row.name()) + ";" + cell(row.plan().getDisplayName()) + ";"
                    + row.products() + ";" + row.orders() + ";" + row.items() + ";" + cell(row.salesStatus())
                    + ";" + report.from() + ";" + report.to() + "\r\n");
        }
    }

    private static String cell(String value) {
        String text = value == null ? "" : value;
        String leading = text.stripLeading();
        if (!leading.isEmpty() && "=+@-".indexOf(leading.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static LocalDate[] range(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(ZoneId.of("America/Sao_Paulo")) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (end.isBefore(start) || end.equals(LocalDate.MAX))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A data final deve ser igual ou posterior à inicial.");
        return new LocalDate[]{start, end};
    }
}
