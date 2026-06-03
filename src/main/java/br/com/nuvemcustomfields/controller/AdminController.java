package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.dto.FieldForm;
import br.com.nuvemcustomfields.entity.FieldType;
import br.com.nuvemcustomfields.entity.PersonalizationRule;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.service.AdminStoreService;
import br.com.nuvemcustomfields.service.NuvemshopApiClient;
import br.com.nuvemcustomfields.service.PersonalizationAdminService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminController {

    private final AdminStoreService adminStoreService;
    private final NuvemshopApiClient apiClient;
    private final PersonalizationAdminService personalizationAdminService;

    public AdminController(
            AdminStoreService adminStoreService,
            NuvemshopApiClient apiClient,
            PersonalizationAdminService personalizationAdminService
    ) {
        this.adminStoreService = adminStoreService;
        this.apiClient = apiClient;
        this.personalizationAdminService = personalizationAdminService;
    }

    @GetMapping("/admin")
    public String index(HttpSession session, Model model) {
        Store store = adminStoreService.requireCurrentStore(session);
        model.addAttribute("store", store);
        model.addAttribute("rules", personalizationAdminService.listRules(store.getStoreId()));
        return "admin/index";
    }

    @GetMapping("/admin/products")
    public String products(HttpSession session, Model model) {
        Store store = adminStoreService.requireCurrentStore(session);
        model.addAttribute("store", store);
        model.addAttribute("products", apiClient.listProducts(store));
        model.addAttribute("rules", personalizationAdminService.listRules(store.getStoreId()));
        return "admin/products";
    }

    @GetMapping("/admin/products/{productId}/fields")
    public String fields(
            @PathVariable Long productId,
            @RequestParam(required = false) String productName,
            HttpSession session,
            Model model
    ) {
        Store store = adminStoreService.requireCurrentStore(session);
        personalizationAdminService.ensureRule(store.getStoreId(), productId, productName);
        populateFieldsModel(store, productId, model, new FieldForm());
        return "admin/fields";
    }

    @PostMapping("/admin/products/{productId}/fields")
    public String addField(
            @PathVariable Long productId,
            @Valid @ModelAttribute("fieldForm") FieldForm fieldForm,
            BindingResult bindingResult,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Store store = adminStoreService.requireCurrentStore(session);
        if (bindingResult.hasErrors()) {
            populateFieldsModel(store, productId, model, fieldForm);
            return "admin/fields";
        }
        personalizationAdminService.addField(store.getStoreId(), productId, fieldForm);
        redirectAttributes.addFlashAttribute("message", "Campo criado.");
        return "redirect:/admin/products/{productId}/fields";
    }

    @PostMapping("/admin/products/{productId}/fields/{fieldId}")
    public String updateField(
            @PathVariable Long productId,
            @PathVariable Long fieldId,
            @Valid @ModelAttribute FieldForm fieldForm,
            BindingResult bindingResult,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        Store store = adminStoreService.requireCurrentStore(session);
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Revise label, tipo e limites antes de salvar.");
            return "redirect:/admin/products/{productId}/fields";
        }
        personalizationAdminService.updateField(store.getStoreId(), productId, fieldId, fieldForm);
        redirectAttributes.addFlashAttribute("message", "Campo atualizado.");
        return "redirect:/admin/products/{productId}/fields";
    }

    @PostMapping("/admin/products/{productId}/fields/{fieldId}/delete")
    public String deleteField(
            @PathVariable Long productId,
            @PathVariable Long fieldId,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        Store store = adminStoreService.requireCurrentStore(session);
        personalizationAdminService.deleteField(store.getStoreId(), productId, fieldId);
        redirectAttributes.addFlashAttribute("message", "Campo removido.");
        return "redirect:/admin/products/{productId}/fields";
    }

    @PostMapping("/admin/products/{productId}/delete")
    public String deleteRule(@PathVariable Long productId, HttpSession session, RedirectAttributes redirectAttributes) {
        Store store = adminStoreService.requireCurrentStore(session);
        personalizationAdminService.deleteRule(store.getStoreId(), productId);
        redirectAttributes.addFlashAttribute("message", "Personalizacao removida do produto.");
        return "redirect:/admin/products";
    }

    private void populateFieldsModel(Store store, Long productId, Model model, FieldForm fieldForm) {
        PersonalizationRule rule = personalizationAdminService.requireRuleWithFields(store.getStoreId(), productId);
        model.addAttribute("store", store);
        model.addAttribute("rule", rule);
        model.addAttribute("fieldTypes", FieldType.values());
        model.addAttribute("fieldForm", fieldForm);
    }
}
