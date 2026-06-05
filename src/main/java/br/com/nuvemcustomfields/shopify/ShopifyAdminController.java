package br.com.nuvemcustomfields.shopify;

import br.com.nuvemcustomfields.config.ShopifySessionInterceptor;
import br.com.nuvemcustomfields.dto.FieldForm;
import br.com.nuvemcustomfields.entity.CommercePlatform;
import br.com.nuvemcustomfields.entity.FieldType;
import br.com.nuvemcustomfields.entity.PersonalizationRule;
import br.com.nuvemcustomfields.entity.ShopifyShop;
import br.com.nuvemcustomfields.repository.ShopifyShopRepository;
import br.com.nuvemcustomfields.service.PersonalizationAdminService;
import br.com.nuvemcustomfields.service.PlanLimitService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class ShopifyAdminController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShopifyAdminController.class);

    private final ShopifyShopRepository shopRepository;
    private final ShopifyApiClient apiClient;
    private final PersonalizationAdminService personalizationAdminService;
    private final PlanLimitService planLimitService;

    public ShopifyAdminController(
            ShopifyShopRepository shopRepository,
            ShopifyApiClient apiClient,
            PersonalizationAdminService personalizationAdminService,
            PlanLimitService planLimitService
    ) {
        this.shopRepository = shopRepository;
        this.apiClient = apiClient;
        this.personalizationAdminService = personalizationAdminService;
        this.planLimitService = planLimitService;
    }

    @GetMapping("/shopify/admin")
    public String index(HttpSession session, Model model) {
        ShopifyShop shop = requireCurrentShop(session);
        model.addAttribute("shop", shop);
        model.addAttribute("rules", personalizationAdminService.listRules(CommercePlatform.SHOPIFY, shop.getId()));
        model.addAttribute("usage", planLimitService.usage(CommercePlatform.SHOPIFY, shop.getId(), shop.getPlan(), 0));
        return "shopify/index";
    }

    @GetMapping("/shopify/admin/products")
    public String products(HttpSession session, Model model) {
        ShopifyShop shop = requireCurrentShop(session);
        var products = apiClient.listProducts(shop);
        var rules = personalizationAdminService.listRules(CommercePlatform.SHOPIFY, shop.getId());
        model.addAttribute("shop", shop);
        model.addAttribute("products", products);
        model.addAttribute("rules", rules);
        model.addAttribute("configuredProductIds", configuredProductIds(rules));
        model.addAttribute("usage", planLimitService.usage(CommercePlatform.SHOPIFY, shop.getId(), shop.getPlan(), 0));
        return "shopify/products";
    }

    @GetMapping("/shopify/admin/products/{productId}/fields")
    public String fields(
            @PathVariable Long productId,
            @RequestParam(required = false) String productName,
            HttpSession session,
            Model model
    ) {
        ShopifyShop shop = requireCurrentShop(session);
        LOGGER.info("shopify.admin.fields.open shop_id={} product_id={}", shop.getId(), productId);
        if (!personalizationAdminService.hasRule(CommercePlatform.SHOPIFY, shop.getId(), productId)
                && !planLimitService.canAddProduct(CommercePlatform.SHOPIFY, shop.getId(), shop.getPlan())) {
            var rules = personalizationAdminService.listRules(CommercePlatform.SHOPIFY, shop.getId());
            model.addAttribute("shop", shop);
            model.addAttribute("products", apiClient.listProducts(shop));
            model.addAttribute("rules", rules);
            model.addAttribute("configuredProductIds", configuredProductIds(rules));
            model.addAttribute("usage", planLimitService.usage(CommercePlatform.SHOPIFY, shop.getId(), shop.getPlan(), 0));
            model.addAttribute("error", "Seu plano atual atingiu o limite de produtos personalizados.");
            return "shopify/products";
        }
        personalizationAdminService.ensureRule(CommercePlatform.SHOPIFY, shop.getId(), productId, productName);
        populateFieldsModel(shop, productId, model, new FieldForm());
        return "shopify/fields";
    }

    @PostMapping("/shopify/admin/products/{productId}/fields")
    public String addField(
            @PathVariable Long productId,
            @Valid @ModelAttribute("fieldForm") FieldForm fieldForm,
            BindingResult bindingResult,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        ShopifyShop shop = requireCurrentShop(session);
        if (bindingResult.hasErrors()) {
            populateFieldsModel(shop, productId, model, fieldForm);
            return "shopify/fields";
        }
        PersonalizationRule rule = personalizationAdminService.requireRuleWithFields(CommercePlatform.SHOPIFY, shop.getId(), productId);
        if (!planLimitService.canAddField(shop.getPlan(), rule.getId())) {
            model.addAttribute("error", "Seu plano atual atingiu o limite de campos por produto.");
            populateFieldsModel(shop, productId, model, fieldForm);
            return "shopify/fields";
        }
        personalizationAdminService.addField(CommercePlatform.SHOPIFY, shop.getId(), productId, fieldForm);
        redirectAttributes.addFlashAttribute("message", "Campo criado.");
        return "redirect:/shopify/admin/products/{productId}/fields";
    }

    @PostMapping("/shopify/admin/products/{productId}/fields/{fieldId}")
    public String updateField(
            @PathVariable Long productId,
            @PathVariable Long fieldId,
            @Valid @ModelAttribute FieldForm fieldForm,
            BindingResult bindingResult,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        ShopifyShop shop = requireCurrentShop(session);
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Revise label, tipo e limites antes de salvar.");
            return "redirect:/shopify/admin/products/{productId}/fields";
        }
        personalizationAdminService.updateField(CommercePlatform.SHOPIFY, shop.getId(), productId, fieldId, fieldForm);
        redirectAttributes.addFlashAttribute("message", "Campo atualizado.");
        return "redirect:/shopify/admin/products/{productId}/fields";
    }

    @PostMapping("/shopify/admin/products/{productId}/fields/{fieldId}/delete")
    public String deleteField(
            @PathVariable Long productId,
            @PathVariable Long fieldId,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        ShopifyShop shop = requireCurrentShop(session);
        personalizationAdminService.deleteField(CommercePlatform.SHOPIFY, shop.getId(), productId, fieldId);
        redirectAttributes.addFlashAttribute("message", "Campo removido.");
        return "redirect:/shopify/admin/products/{productId}/fields";
    }

    @PostMapping("/shopify/admin/products/{productId}/delete")
    public String deleteRule(@PathVariable Long productId, HttpSession session, RedirectAttributes redirectAttributes) {
        ShopifyShop shop = requireCurrentShop(session);
        personalizationAdminService.deleteRule(CommercePlatform.SHOPIFY, shop.getId(), productId);
        redirectAttributes.addFlashAttribute("message", "Personalizacao removida do produto.");
        return "redirect:/shopify/admin/products";
    }

    private void populateFieldsModel(ShopifyShop shop, Long productId, Model model, FieldForm fieldForm) {
        PersonalizationRule rule = personalizationAdminService.requireRuleWithFields(CommercePlatform.SHOPIFY, shop.getId(), productId);
        model.addAttribute("shop", shop);
        model.addAttribute("rule", rule);
        model.addAttribute("fieldTypes", FieldType.values());
        model.addAttribute("fieldForm", fieldForm);
        model.addAttribute("usage", planLimitService.usage(CommercePlatform.SHOPIFY, shop.getId(), shop.getPlan(), rule.getFields().size()));
        model.addAttribute("canAddField", planLimitService.canAddField(shop.getPlan(), rule.getId()));
    }

    private ShopifyShop requireCurrentShop(HttpSession session) {
        Object shopId = session.getAttribute(ShopifySessionInterceptor.SHOP_SESSION_KEY);
        if (shopId instanceof Long id) {
            return shopRepository.findActiveById(id).orElseThrow(() -> new IllegalArgumentException("Loja Shopify ativa nao encontrada."));
        }
        throw new IllegalArgumentException("Sessao Shopify nao encontrada.");
    }

    private Set<Long> configuredProductIds(Iterable<PersonalizationRule> rules) {
        return java.util.stream.StreamSupport.stream(rules.spliterator(), false)
                .map(PersonalizationRule::getProductId)
                .collect(Collectors.toSet());
    }
}
