package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.BuyerStorageProvider;
import br.com.nuvemcustomfields.service.BuyerStorageConfigurationService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BackofficeBuyerStorageController {
    private final BuyerStorageConfigurationService configuration;
    public BackofficeBuyerStorageController(BuyerStorageConfigurationService configuration) { this.configuration = configuration; }
    @PostMapping("/backoffice/stores/{storeId}/buyer-upload-storage")
    public String update(@PathVariable long storeId, @RequestParam(defaultValue = "") String provider,
            RedirectAttributes redirect) {
        try {
            configuration.update(storeId, provider.isBlank() ? null : BuyerStorageProvider.valueOf(provider));
            redirect.addFlashAttribute("message", "Destino dos novos uploads atualizado. Arquivos existentes mantêm seu armazenamento.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", "Destino inválido ou armazenamento não configurado no servidor.");
        }
        return "redirect:/backoffice/stores/" + storeId;
    }
}
