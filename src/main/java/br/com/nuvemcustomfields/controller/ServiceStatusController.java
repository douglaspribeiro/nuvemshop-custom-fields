package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.ServiceStatusService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ServiceStatusController {
    private final ServiceStatusService service;

    public ServiceStatusController(ServiceStatusService service) { this.service = service; }

    @GetMapping("/status")
    public String alias() { return "redirect:/status/"; }

    @GetMapping("/status/")
    public String status(Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("serviceStatus", service.current());
        return "public/status";
    }
}
