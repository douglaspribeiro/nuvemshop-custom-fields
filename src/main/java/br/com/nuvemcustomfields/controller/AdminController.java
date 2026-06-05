package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.dto.FieldForm;
import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.FieldType;
import br.com.nuvemcustomfields.entity.PersonalizationRule;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.service.AdminStoreService;
import br.com.nuvemcustomfields.service.IntegrationLogService;
import br.com.nuvemcustomfields.service.NicheTemplateService;
import br.com.nuvemcustomfields.service.NuvemshopApiClient;
import br.com.nuvemcustomfields.service.PlanLimitService;
import br.com.nuvemcustomfields.service.PersonalizationAdminService;
import br.com.nuvemcustomfields.service.ReportService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class AdminController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminController.class);

    private final AdminStoreService adminStoreService;
    private final IntegrationLogService integrationLogService;
    private final NuvemshopApiClient apiClient;
    private final NicheTemplateService nicheTemplateService;
    private final PlanLimitService planLimitService;
    private final PersonalizationAdminService personalizationAdminService;
    private final ReportService reportService;
    private final NuvemshopProperties nuvemshopProperties;

    public AdminController(
            AdminStoreService adminStoreService,
            IntegrationLogService integrationLogService,
            NuvemshopApiClient apiClient,
            NicheTemplateService nicheTemplateService,
            PlanLimitService planLimitService,
            PersonalizationAdminService personalizationAdminService,
            ReportService reportService,
            NuvemshopProperties nuvemshopProperties
    ) {
        this.adminStoreService = adminStoreService;
        this.integrationLogService = integrationLogService;
        this.apiClient = apiClient;
        this.nicheTemplateService = nicheTemplateService;
        this.planLimitService = planLimitService;
        this.personalizationAdminService = personalizationAdminService;
        this.reportService = reportService;
        this.nuvemshopProperties = nuvemshopProperties;
    }

    @ModelAttribute("nuvemshopClientId")
    public String nuvemshopClientId() {
        return nuvemshopProperties.clientId();
    }

    @GetMapping("/admin")
    public String index(HttpSession session, Model model) {
        Store store = adminStoreService.requireCurrentStore(session);
        LOGGER.info("admin.index.open store_id={}", store.getStoreId());
        var rules = personalizationAdminService.listRules(store.getStoreId());
        model.addAttribute("store", store);
        model.addAttribute("rules", rules);
        model.addAttribute("usage", planLimitService.usage(store, 0));
        model.addAttribute("backofficeStoreMode", Boolean.TRUE.equals(session.getAttribute(BackofficeSessionInterceptor.STORE_MODE_SESSION_KEY)));
        LOGGER.info("admin.index.loaded store_id={} rules_count={}", store.getStoreId(), rules.size());
        return "admin/index";
    }

    @GetMapping("/admin/settings/style")
    public String styleSettings(HttpSession session, Model model) {
        Store store = adminStoreService.requireCurrentStore(session);
        LOGGER.info("admin.settings.style.open store_id={}", store.getStoreId());
        model.addAttribute("store", store);
        return "admin/style-settings";
    }

    @PostMapping("/admin/settings/style")
    public String updateStyleSettings(
            @RequestParam(required = false) String productTextColor,
            @RequestParam(defaultValue = "false") boolean clearProductTextColor,
            @RequestParam(required = false) String checkoutTextColor,
            @RequestParam(defaultValue = "false") boolean clearCheckoutTextColor,
            @RequestParam(required = false) String cartTextColor,
            @RequestParam(defaultValue = "false") boolean clearCartTextColor,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        Store store = adminStoreService.requireCurrentStore(session);
        LOGGER.info("admin.settings.style.update store_id={}", store.getStoreId());
        try {
            adminStoreService.updateStyleSettings(
                    store,
                    productTextColor,
                    clearProductTextColor,
                    checkoutTextColor,
                    clearCheckoutTextColor,
                    cartTextColor,
                    clearCartTextColor
            );
            redirectAttributes.addFlashAttribute("message", "Cores salvas.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/settings/style";
    }

    @GetMapping("/admin/help")
    public String help(HttpSession session, Model model) {
        Store store = adminStoreService.requireCurrentStore(session);
        LOGGER.info("admin.help.open store_id={}", store.getStoreId());
        model.addAttribute("store", store);
        model.addAttribute("logs", integrationLogService.recent(store.getStoreId()));
        return "admin/help";
    }

    @GetMapping("/admin/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Store store = adminStoreService.requireCurrentStore(session);
        LOGGER.info("admin.dashboard.open store_id={}", store.getStoreId());
        model.addAttribute("store", store);
        model.addAttribute("summary", reportService.dashboard(store));
        return "admin/dashboard";
    }

    @GetMapping("/admin/billing")
    public String billing(HttpSession session, Model model) {
        Store store = adminStoreService.requireCurrentStore(session);
        LOGGER.info("admin.billing.open store_id={}", store.getStoreId());
        model.addAttribute("store", store);
        model.addAttribute("usage", planLimitService.usage(store, 0));
        return "admin/billing";
    }

    @GetMapping("/admin/products")
    public String products(HttpSession session, Model model) {
        Store store = adminStoreService.requireCurrentStore(session);
        LOGGER.info("admin.products.open store_id={}", store.getStoreId());
        var products = apiClient.listProducts(store);
        var rules = personalizationAdminService.listRules(store.getStoreId());
        model.addAttribute("store", store);
        model.addAttribute("products", products);
        model.addAttribute("rules", rules);
        model.addAttribute("configuredProductIds", configuredProductIds(rules));
        model.addAttribute("usage", planLimitService.usage(store, 0));
        LOGGER.info(
                "admin.products.loaded store_id={} products_count={} rules_count={}",
                store.getStoreId(),
                products.size(),
                rules.size()
        );
        return "admin/products";
    }

    @GetMapping("/admin/onboarding")
    public String onboarding(HttpSession session, Model model) {
        Store store = adminStoreService.requireCurrentStore(session);
        LOGGER.info("admin.onboarding.open store_id={}", store.getStoreId());
        var products = apiClient.listProducts(store);
        var templates = nicheTemplateService.listTemplates();
        model.addAttribute("store", store);
        model.addAttribute("products", products);
        model.addAttribute("templates", templates);
        model.addAttribute("usage", planLimitService.usage(store, 0));
        LOGGER.info(
                "admin.onboarding.loaded store_id={} products_count={} templates_count={}",
                store.getStoreId(),
                products.size(),
                templates.size()
        );
        return "admin/onboarding";
    }

    @PostMapping("/admin/onboarding/apply")
    public String applyTemplate(
            @RequestParam Long productId,
            @RequestParam String productName,
            @RequestParam String templateId,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        Store store = adminStoreService.requireCurrentStore(session);
        LOGGER.info(
                "admin.onboarding.apply store_id={} product_id={} template_id={}",
                store.getStoreId(),
                productId,
                templateId
        );
        if (!personalizationAdminService.hasRule(store.getStoreId(), productId) && !planLimitService.canAddProduct(store)) {
            LOGGER.warn("admin.onboarding.apply.limit_reached store_id={} product_id={}", store.getStoreId(), productId);
            redirectAttributes.addFlashAttribute("error", "Seu plano atual atingiu o limite de produtos personalizados.");
            return "redirect:/admin/onboarding";
        }
        int created = personalizationAdminService.applyTemplate(
                store,
                productId,
                productName,
                nicheTemplateService.requireTemplate(templateId),
                planLimitService
        );
        LOGGER.info(
                "admin.onboarding.apply.done store_id={} product_id={} created_fields={}",
                store.getStoreId(),
                productId,
                created
        );
        redirectAttributes.addFlashAttribute("message", created + " campos do template foram aplicados.");
        return "redirect:/admin/products/" + productId + "/fields";
    }

    @GetMapping("/admin/products/{productId}/fields")
    public String fields(
            @PathVariable Long productId,
            @RequestParam(required = false) String productName,
            HttpSession session,
            Model model
    ) {
        Store store = adminStoreService.requireCurrentStore(session);
        LOGGER.info("admin.fields.open store_id={} product_id={} product_name={}", store.getStoreId(), productId, productName);
        if (!personalizationAdminService.hasRule(store.getStoreId(), productId) && !planLimitService.canAddProduct(store)) {
            LOGGER.warn("admin.fields.open.limit_reached store_id={} product_id={}", store.getStoreId(), productId);
            var rules = personalizationAdminService.listRules(store.getStoreId());
            model.addAttribute("store", store);
            model.addAttribute("products", apiClient.listProducts(store));
            model.addAttribute("rules", rules);
            model.addAttribute("configuredProductIds", configuredProductIds(rules));
            model.addAttribute("usage", planLimitService.usage(store, 0));
            model.addAttribute("error", "Seu plano atual atingiu o limite de produtos personalizados.");
            return "admin/products";
        }
        PersonalizationRule rule = personalizationAdminService.ensureRule(store.getStoreId(), productId, productName);
        LOGGER.info("admin.fields.rule_ready store_id={} product_id={} rule_id={}", store.getStoreId(), productId, rule.getId());
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
        LOGGER.info("admin.fields.add store_id={} product_id={} label={}", store.getStoreId(), productId, fieldForm.getLabel());
        if (bindingResult.hasErrors()) {
            LOGGER.warn("admin.fields.add.validation_error store_id={} product_id={}", store.getStoreId(), productId);
            populateFieldsModel(store, productId, model, fieldForm);
            return "admin/fields";
        }
        PersonalizationRule rule = personalizationAdminService.requireRuleWithFields(store.getStoreId(), productId);
        if (!planLimitService.canAddField(store, rule.getId())) {
            LOGGER.warn("admin.fields.add.limit_reached store_id={} product_id={} rule_id={}", store.getStoreId(), productId, rule.getId());
            model.addAttribute("error", "Seu plano atual atingiu o limite de campos por produto.");
            populateFieldsModel(store, productId, model, fieldForm);
            return "admin/fields";
        }
        personalizationAdminService.addField(store.getStoreId(), productId, fieldForm);
        LOGGER.info("admin.fields.add.done store_id={} product_id={}", store.getStoreId(), productId);
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
        LOGGER.info("admin.fields.update store_id={} product_id={} field_id={}", store.getStoreId(), productId, fieldId);
        if (bindingResult.hasErrors()) {
            LOGGER.warn("admin.fields.update.validation_error store_id={} product_id={} field_id={}", store.getStoreId(), productId, fieldId);
            redirectAttributes.addFlashAttribute("error", "Revise label, tipo e limites antes de salvar.");
            return "redirect:/admin/products/{productId}/fields";
        }
        personalizationAdminService.updateField(store.getStoreId(), productId, fieldId, fieldForm);
        LOGGER.info("admin.fields.update.done store_id={} product_id={} field_id={}", store.getStoreId(), productId, fieldId);
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
        LOGGER.info("admin.fields.delete store_id={} product_id={} field_id={}", store.getStoreId(), productId, fieldId);
        personalizationAdminService.deleteField(store.getStoreId(), productId, fieldId);
        redirectAttributes.addFlashAttribute("message", "Campo removido.");
        return "redirect:/admin/products/{productId}/fields";
    }

    @PostMapping("/admin/products/{productId}/delete")
    public String deleteRule(@PathVariable Long productId, HttpSession session, RedirectAttributes redirectAttributes) {
        Store store = adminStoreService.requireCurrentStore(session);
        LOGGER.info("admin.rule.delete store_id={} product_id={}", store.getStoreId(), productId);
        personalizationAdminService.deleteRule(store.getStoreId(), productId);
        redirectAttributes.addFlashAttribute("message", "Personalizacao removida do produto.");
        return "redirect:/admin/products";
    }

    private void populateFieldsModel(Store store, Long productId, Model model, FieldForm fieldForm) {
        PersonalizationRule rule = personalizationAdminService.requireRuleWithFields(store.getStoreId(), productId);
        LOGGER.info(
                "admin.fields.model store_id={} product_id={} rule_id={} fields_count={}",
                store.getStoreId(),
                productId,
                rule.getId(),
                rule.getFields().size()
        );
        model.addAttribute("store", store);
        model.addAttribute("rule", rule);
        model.addAttribute("fieldTypes", FieldType.values());
        model.addAttribute("fieldForm", fieldForm);
        model.addAttribute("usage", planLimitService.usage(store, rule.getFields().size()));
        model.addAttribute("canAddField", planLimitService.canAddField(store, rule.getId()));
    }

    private Set<Long> configuredProductIds(Iterable<PersonalizationRule> rules) {
        return java.util.stream.StreamSupport.stream(rules.spliterator(), false)
                .map(PersonalizationRule::getProductId)
                .collect(Collectors.toSet());
    }
}
