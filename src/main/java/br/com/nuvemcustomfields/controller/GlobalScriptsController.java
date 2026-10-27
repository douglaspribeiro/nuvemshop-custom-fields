package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.GlobalScriptInstallService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class GlobalScriptsController {
    private final GlobalScriptInstallService batch;
    public GlobalScriptsController(GlobalScriptInstallService batch) { this.batch = batch; }

    @GetMapping("/backoffice/scripts")
    public String index(Model model) {
        model.addAttribute("batch", batch.snapshot());
        return "backoffice/global-scripts";
    }

    @PostMapping("/backoffice/scripts/reinstall")
    public String start(RedirectAttributes flash) {
        flash.addFlashAttribute("message", batch.start()
                ? "Verificação global iniciada." : "Já existe uma execução em andamento.");
        return "redirect:/backoffice/scripts";
    }
}
