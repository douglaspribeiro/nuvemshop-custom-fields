package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.StoreConfigurationHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class BackofficeStoreConfigurationController {
    private final StoreConfigurationHistoryService history;
    public BackofficeStoreConfigurationController(StoreConfigurationHistoryService history) { this.history = history; }

    @GetMapping("/backoffice/stores/{storeId}/configuration")
    public String configuration(@PathVariable Long storeId, @RequestParam(required = false) Long snapshotId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.Instant departure,
            Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("inspection", history.inspect(storeId, snapshotId, departure));
        return "backoffice/store-configuration";
    }
}
