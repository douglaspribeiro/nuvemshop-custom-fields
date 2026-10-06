package br.com.nuvemcustomfields.controller;
import java.math.BigDecimal;

import br.com.nuvemcustomfields.service.ImagePlanLimitException;
import br.com.nuvemcustomfields.dto.FieldForm;
import br.com.nuvemcustomfields.dto.ProductPage;
import br.com.nuvemcustomfields.dto.ProductSummary;
import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.FieldType;
import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PaymentSubscriptionStatus;
import br.com.nuvemcustomfields.entity.PersonalizationRule;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.payment.EfiGateway;
import br.com.nuvemcustomfields.payment.PaymentGatewayException;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.service.AdminStoreService;
import br.com.nuvemcustomfields.service.IntegrationLogService;
import br.com.nuvemcustomfields.service.PaymentSubscriptionService;
import br.com.nuvemcustomfields.service.NicheTemplateService;
import br.com.nuvemcustomfields.service.NuvemshopApiClient;
import br.com.nuvemcustomfields.service.PlanLimitService;
import br.com.nuvemcustomfields.service.PersonalizationAdminService;
import br.com.nuvemcustomfields.i18n.Messages;
import br.com.nuvemcustomfields.service.ReportService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.validation.BindingResult;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.client.HttpClientErrorException;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.text.Collator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final PaymentSubscriptionService paymentSubscriptionService;
    private final Messages messages;

    public AdminController(
            AdminStoreService adminStoreService,
            IntegrationLogService integrationLogService,
            NuvemshopApiClient apiClient,
            NicheTemplateService nicheTemplateService,
            PlanLimitService planLimitService,
            PersonalizationAdminService personalizationAdminService,
            ReportService reportService,
            NuvemshopProperties nuvemshopProperties,
            PaymentSubscriptionService paymentSubscriptionService,
            Messages messages
    ) {
        this.adminStoreService = adminStoreService;
        this.integrationLogService = integrationLogService;
        this.apiClient = apiClient;
        this.nicheTemplateService = nicheTemplateService;
        this.planLimitService = planLimitService;
        this.personalizationAdminService = personalizationAdminService;
        this.reportService = reportService;
        this.nuvemshopProperties = nuvemshopProperties;
        this.paymentSubscriptionService = paymentSubscriptionService;
        this.messages = messages;
    }

    @ModelAttribute("nuvemshopClientId")
    public String nuvemshopClientId() {
        return nuvemshopProperties.clientId();
    }

    @ExceptionHandler(HttpClientErrorException.Unauthorized.class)
    public String invalidAccessToken(HttpClientErrorException.Unauthorized ex, HttpSession session) {
        Object storeId = session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY);
        LOGGER.warn(
                "admin.api.unauthorized store_id={} action=disconnect_and_reinstall message={}",
                storeId,
                ex.getMessage()
        );
        adminStoreService.markCurrentStoreDisconnected(session);
        return "redirect:/admin/embedded";
    }

    @GetMapping("/admin")
    public String index(HttpSession session, Model model) {
        Store store = adminStoreService.requireCurrentStore(session);
        if (reconcilePendingEfi(store.getStoreId(), true)) {
            store = adminStoreService.requireCurrentStore(session);
        }
        LOGGER.info("admin.index.open store_id={}", store.getStoreId());
        var rules = personalizationAdminService.listRules(store.getStoreId());
        model.addAttribute("store", store);
        model.addAttribute("rules", rules);
        model.addAttribute("configuredFields", personalizationAdminService.countFields(store.getStoreId()));
        model.addAttribute("setupRule", rules.isEmpty() ? null : rules.get(0));
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
            redirectAttributes.addFlashAttribute("message", messages.get("flash.style.saved"));
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
        boolean available = paymentSubscriptionService.available(store);
        boolean expired = expirePendingEfiIfDue(store.getStoreId());
        boolean reconciled = reconcilePendingEfi(store.getStoreId(), false);
        if (expired || reconciled) {
            store = adminStoreService.requireCurrentStore(session);
        }
        var subscription = paymentSubscriptionService.find(store.getStoreId()).orElse(null);
        if (subscription != null && subscription.getUpgradePlan() != null) {
            try {
                paymentSubscriptionService.reconcile(store.getStoreId());
                store = adminStoreService.requireCurrentStore(session);
                subscription = paymentSubscriptionService.find(store.getStoreId()).orElse(null);
            } catch (RuntimeException ex) {
                LOGGER.warn("payments.upgrade.reconcile_pending store_id={} type={}", store.getStoreId(), ex.getClass().getSimpleName());
            }
        }
        model.addAttribute("store", store);
        model.addAttribute("usage", planLimitService.usage(store, 0));
        model.addAttribute("planDefinitions", planLimitService.planDefinitions());
        model.addAttribute("billingEnabled", paymentSubscriptionService.anyGatewayEnabled());
        model.addAttribute("billingAvailable", available);
        String billingCurrency = available ? paymentSubscriptionService.currency(store) : "";
        model.addAttribute("premiumPrice", formatBillingPrice(billingCurrency,
                paymentSubscriptionService.amount(store, PlanType.PREMIUM)));
        model.addAttribute("premiumPlusPrice", formatBillingPrice(billingCurrency,
                paymentSubscriptionService.amount(store, PlanType.PREMIUM_PLUS)));
        boolean ultraAvailable = paymentSubscriptionService.planAvailable(store, PlanType.PREMIUM_ULTRA);
        model.addAttribute("ultraAvailable", ultraAvailable);
        model.addAttribute("premiumUltraPrice", ultraAvailable ? formatBillingPrice(billingCurrency,
                paymentSubscriptionService.amount(store, PlanType.PREMIUM_ULTRA)) : "");
        model.addAttribute("canUpgrade", subscription != null && subscription.isAccessActive()
                && subscription.getStatus() == PaymentSubscriptionStatus.ACTIVE
                && !subscription.isCancellationPending() && subscription.getCancellationEffectiveAt() == null
                && !subscription.isWinbackRestorePending() && subscription.getUpgradePlan() == null
                && !subscription.isUpgradePaymentPending() && !store.isCourtesyPremium());
        model.addAttribute("paymentSubscription", subscription);
        model.addAttribute("analyticsCurrency", billingCurrency);
        model.addAttribute("analyticsProvider", subscription != null && subscription.isAccessActive()
                ? subscription.getProvider().name() : paymentSubscriptionService.provider(store).map(Enum::name).orElse(null));
        model.addAttribute("analyticsSandbox", paymentSubscriptionService.analyticsSandbox(store));
        model.addAttribute("billingError", subscription == null || subscription.getLastError() == null ? null
                : paymentSubscriptionService.customerFacingError(subscription.getTechnicalError() == null
                ? subscription.getLastError() : subscription.getTechnicalError()));
        return "admin/billing";
    }

    @PostMapping({"/admin/billing/subscribe", "/admin/billing/checkout"})
    public String subscribe(@RequestParam PlanType plan,
                            HttpSession session, RedirectAttributes redirectAttributes) {
        Store store = adminStoreService.requireCurrentStore(session);
        try {
            if (!paymentSubscriptionService.anyGatewayEnabled()) {
                throw new IllegalStateException(messages.get("admin.billing.paused"));
            }
            if (!paymentSubscriptionService.available(store)) {
                throw new IllegalStateException(messages.get("admin.billing.unavailable"));
            }
            var activeSubscription = paymentSubscriptionService.find(store.getStoreId()).orElse(null);
            if (activeSubscription != null && activeSubscription.isAccessActive()) {
                if (!plan.isUpgradeFrom(store.getPlan())) {
                    throw new IllegalArgumentException("Selecione um plano superior ao plano atual.");
                }
                return "redirect:/admin/billing/upgrade?plan=" + plan.name();
            }
            if (paymentSubscriptionService.provider(store).orElse(null) == PaymentProviderType.EFI) {
                return "redirect:/admin/billing/pay?plan=" + plan.name();
            }
            return "redirect:" + paymentSubscriptionService.startCheckout(store.getStoreId(), plan);
        } catch (RuntimeException ex) {
            LOGGER.warn("payments.checkout.failed store_id={} plan={} message={}", store.getStoreId(), plan, ex.getMessage());
            redirectAttributes.addFlashAttribute("error", merchantError(ex));
            return "redirect:/admin/billing";
        }
    }

    @GetMapping("/admin/billing/upgrade")
    public String upgradePage(@RequestParam PlanType plan, HttpSession session, Model model, HttpServletResponse response) {
        Store store = adminStoreService.requireCurrentStore(session);
        var subscription = paymentSubscriptionService.find(store.getStoreId()).orElse(null);
        if (subscription == null || !subscription.isAccessActive() || !plan.isUpgradeFrom(subscription.getPlan())
                || !paymentSubscriptionService.planAvailable(store, plan)) {
            return "redirect:/admin/billing";
        }
        if (subscription.getProvider() == PaymentProviderType.EFI)
            return "redirect:/admin/billing/upgrade/efi?plan=" + plan.name();
        model.addAttribute("store", store);
        model.addAttribute("targetPlan", plan);
        model.addAttribute("analyticsCurrency", subscription.getCurrency());
        model.addAttribute("analyticsProvider", subscription.getProvider().name());
        model.addAttribute("analyticsSandbox", subscription.getProviderEnvironment() == br.com.nuvemcustomfields.entity.PaymentEnvironment.SANDBOX);
        model.addAttribute("analyticsRecurringAmount", paymentSubscriptionService.upgradeAmount(store, plan));
        response.setHeader("Cache-Control", "no-store");
        BigDecimal amount = paymentSubscriptionService.upgradeAmount(store, plan);
        model.addAttribute("amount", amount);
        model.addAttribute("targetPrice", formatBillingPrice(subscription.getCurrency(), amount));
        model.addAttribute("currentPrice", formatBillingPrice(subscription.getCurrency(), subscription.getAmountValue()));
        model.addAttribute("nextPaymentAt", subscription.getNextPaymentAt());
        model.addAttribute("creemUpgrade", subscription.getProvider() == PaymentProviderType.CREEM);
        return "admin/billing-upgrade";
    }

    @PostMapping("/admin/billing/upgrade")
    public String upgrade(@RequestParam PlanType plan, @RequestParam BigDecimal amount, HttpSession session, RedirectAttributes redirectAttributes) {
        Store store = adminStoreService.requireCurrentStore(session);
        try {
            var subscription = paymentSubscriptionService.find(store.getStoreId()).orElseThrow();
            if (subscription.getProvider() == PaymentProviderType.EFI)
                return "redirect:/admin/billing/upgrade/efi?plan=" + plan.name();
            paymentSubscriptionService.upgrade(store.getStoreId(), plan, amount);
            redirectAttributes.addFlashAttribute("message", messages.get("admin.billing.upgrade.success"));
            return "redirect:/admin/billing";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", merchantError(ex));
            return "redirect:/admin/billing";
        }
    }

    @GetMapping("/admin/billing/pay")
    public String efiPaymentPage(@RequestParam PlanType plan, HttpSession session, Model model,
                                 HttpServletResponse response) {
        Store store = adminStoreService.requireCurrentStore(session);
        if (paymentSubscriptionService.provider(store).orElse(null) != PaymentProviderType.EFI
                || !paymentSubscriptionService.available(store)
                || plan == null || !plan.isBillable()) return "redirect:/admin/billing";
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("store", store);
        model.addAttribute("plan", plan);
        var quote = paymentSubscriptionService.efiQuote(store, plan);
        model.addAttribute("couponCode", quote.code());
        model.addAttribute("analyticsProvider", "EFI");
        model.addAttribute("analyticsCurrency", "BRL");
        model.addAttribute("analyticsRecurringAmount", quote.regularAmount());
        model.addAttribute("discounted", quote.discounted());
        model.addAttribute("amount", quote.firstAmount());
        model.addAttribute("formattedAmount", NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
                .format(quote.firstAmount()));
        model.addAttribute("formattedRegularAmount", NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
                .format(quote.regularAmount()));
        model.addAttribute("payeeCode", paymentSubscriptionService.efiPayeeCode());
        model.addAttribute("efiEnvironment", paymentSubscriptionService.efiSandbox() ? "sandbox" : "production");
        return "admin/billing-payment";
    }

    @PostMapping("/admin/billing/pay")
    public String payWithEfi(@RequestParam PlanType plan,
                             @RequestParam String payerName, @RequestParam String cpf,
                             @RequestParam String payerEmail, @RequestParam String phone,
                             @RequestParam String birth, @RequestParam String paymentToken,
                             @RequestParam(required = false) String couponCode,
                             HttpSession session, RedirectAttributes redirectAttributes) {
        Store store = adminStoreService.requireCurrentStore(session);
        try {
            paymentSubscriptionService.payWithEfi(store.getStoreId(), plan,
                    new EfiGateway.EfiPayer(payerName, cpf.replaceAll("\\D", ""), payerEmail,
                            normalizeEfiPhone(phone), birth), paymentToken, couponCode);
            return "redirect:/admin/billing/processing";
        } catch (RuntimeException ex) {
            LOGGER.warn("payments.efi.failed store_id={} plan={} type={}", store.getStoreId(), plan,
                    ex.getClass().getSimpleName());
            redirectAttributes.addFlashAttribute("error", merchantError(ex));
            return "redirect:/admin/billing/pay?plan=" + plan.name();
        }
    }

    @GetMapping("/admin/billing/processing")
    public String paymentProcessing(HttpSession session, Model model, HttpServletResponse response) {
        Store store = adminStoreService.requireCurrentStore(session);
        response.setHeader("Cache-Control", "no-store");
        if (paymentSubscriptionService.find(store.getStoreId()).isEmpty()) return "redirect:/admin/billing";
        model.addAttribute("store", store);
        return "admin/billing-processing";
    }

    @GetMapping("/admin/billing/status")
    @ResponseBody
    public Map<String, String> paymentStatus(HttpSession session, HttpServletResponse response) {
        Store store = adminStoreService.requireCurrentStore(session);
        response.setHeader("Cache-Control", "no-store");
        expirePendingEfiIfDue(store.getStoreId());
        reconcilePendingEfi(store.getStoreId(), false);
        var subscription = paymentSubscriptionService.find(store.getStoreId()).orElse(null);
        if (subscription == null) return Map.of("state", "failed");
        if (subscription.getStatus() == PaymentSubscriptionStatus.ACTIVE && subscription.isAccessActive()) {
            return Map.of("state", "active");
        }
        if (subscription.getStatus() == PaymentSubscriptionStatus.ERROR
                || subscription.getStatus() == PaymentSubscriptionStatus.CANCELED
                || subscription.getStatus() == PaymentSubscriptionStatus.PAUSED
                || "unpaid".equalsIgnoreCase(subscription.getLastPaymentStatus())) {
            return Map.of("state", "failed");
        }
        return Map.of("state", "pending");
    }

    static String normalizeEfiPhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("55") && digits.length() >= 12 && digits.length() <= 13) {
            return digits.substring(2);
        }
        return digits;
    }

    @GetMapping("/admin/billing/return")
    public String billingReturn(HttpSession session, RedirectAttributes redirectAttributes) {
        Store store = adminStoreService.requireCurrentStore(session);
        try {
            if (paymentSubscriptionService.find(store.getStoreId()).isPresent()) {
                paymentSubscriptionService.reconcile(store.getStoreId());
            }
        } catch (RuntimeException ex) {
            LOGGER.info("payments.return.awaiting_webhook store_id={} message={}", store.getStoreId(), ex.getMessage());
        }
        redirectAttributes.addFlashAttribute("message", messages.get("admin.billing.processing"));
        return "redirect:/admin/billing";
    }

    @GetMapping("/admin/billing/cancel")
    public String cancellationPage(HttpSession session, Model model) {
        Store store = adminStoreService.requireCurrentStore(session);
        var subscription = paymentSubscriptionService.find(store.getStoreId()).orElse(null);
        if (subscription == null || !subscription.isAccessActive()
                || subscription.getStatus() == PaymentSubscriptionStatus.CANCELED
                || subscription.isCancellationPending()) return "redirect:/admin/billing";
        model.addAttribute("store", store);
        model.addAttribute("paymentSubscription", subscription);
        return "admin/billing-cancel";
    }

    @PostMapping("/admin/billing/cancel")
    public String cancelSubscription(@RequestParam(defaultValue = "false") boolean confirm,
                                     HttpSession session, RedirectAttributes redirectAttributes) {
        Store store = adminStoreService.requireCurrentStore(session);
        var subscription = paymentSubscriptionService.find(store.getStoreId()).orElse(null);
        if (!confirm) {
            redirectAttributes.addFlashAttribute("error", messages.get("admin.billing.cancel.confirm.required"));
            return "redirect:/admin/billing";
        }
        if (subscription == null || !subscription.isAccessActive()
                || subscription.getStatus() == PaymentSubscriptionStatus.CANCELED
                || subscription.isCancellationPending()) {
            redirectAttributes.addFlashAttribute("error", messages.get("admin.billing.cancel.unavailable"));
            return "redirect:/admin/billing";
        }
        try {
            paymentSubscriptionService.cancel(store.getStoreId());
            redirectAttributes.addFlashAttribute("message", messages.get("admin.billing.cancelled"));
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/billing";
    }

    @GetMapping("/admin/products")
    public String products(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String q,
            HttpSession session,
            Model model
    ) {
        Store store = adminStoreService.requireCurrentStore(session);
        LOGGER.info("admin.products.open store_id={} page={}", store.getStoreId(), page);
        var productPage = apiClient.listProducts(store, page, NuvemshopApiClient.DEFAULT_PER_PAGE, q);
        var rules = personalizationAdminService.listRules(store.getStoreId());
        var configuredFieldProductIds = personalizationAdminService.configuredFieldProductIds(store.getStoreId());
        model.addAttribute("store", store);
        model.addAttribute("productPage", productPage);
        model.addAttribute("products", prioritizedProducts(productPage, rules, configuredFieldProductIds));
        model.addAttribute("rules", rules);
        model.addAttribute("configuredProductIds", configuredProductIds(rules));
        model.addAttribute("configuredFieldProductIds", configuredFieldProductIds);
        model.addAttribute("configuredFields", personalizationAdminService.countFields(store.getStoreId()));
        model.addAttribute("usage", planLimitService.usage(store, 0));
        LOGGER.info(
                "admin.products.loaded store_id={} page={} products_count={} total_count={} rules_count={}",
                store.getStoreId(),
                productPage.page(),
                productPage.items().size(),
                productPage.totalCount(),
                rules.size()
        );
        return "admin/products";
    }

    @GetMapping("/admin/onboarding")
    public String onboarding(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String q,
            HttpSession session,
            Model model
    ) {
        Store store = adminStoreService.requireCurrentStore(session);
        LOGGER.info("admin.onboarding.open store_id={} page={}", store.getStoreId(), page);
        var productPage = apiClient.listProducts(store, page, NuvemshopApiClient.DEFAULT_PER_PAGE, q);
        var templates = nicheTemplateService.listTemplates();
        model.addAttribute("store", store);
        model.addAttribute("productPage", productPage);
        model.addAttribute("products", productPage.items());
        model.addAttribute("templates", templates);
        model.addAttribute("usage", planLimitService.usage(store, 0));
        LOGGER.info(
                "admin.onboarding.loaded store_id={} page={} products_count={} templates_count={}",
                store.getStoreId(),
                productPage.page(),
                productPage.items().size(),
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
            redirectAttributes.addFlashAttribute("error", messages.get("flash.product.limit"));
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
        redirectAttributes.addFlashAttribute("message", messages.get("flash.template.applied", created));
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
            var productPage = apiClient.listProducts(store, 1, NuvemshopApiClient.DEFAULT_PER_PAGE, null);
            var configuredFieldProductIds = personalizationAdminService.configuredFieldProductIds(store.getStoreId());
            model.addAttribute("store", store);
            model.addAttribute("productPage", productPage);
            model.addAttribute("products", prioritizedProducts(productPage, rules, configuredFieldProductIds));
            model.addAttribute("rules", rules);
            model.addAttribute("configuredProductIds", configuredProductIds(rules));
            model.addAttribute("configuredFieldProductIds", configuredFieldProductIds);
            model.addAttribute("configuredFields", personalizationAdminService.countFields(store.getStoreId()));
            model.addAttribute("usage", planLimitService.usage(store, 0));
            model.addAttribute("error", messages.get("flash.product.limit"));
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
            model.addAttribute("error", messages.get("flash.field.invalid"));
            populateFieldsModel(store, productId, model, fieldForm);
            return "admin/fields";
        }
        PersonalizationRule rule = personalizationAdminService.requireRuleWithFields(store.getStoreId(), productId);
        if (!planLimitService.canAddField(store, rule.getId())) {
            LOGGER.warn("admin.fields.add.limit_reached store_id={} product_id={} rule_id={}", store.getStoreId(), productId, rule.getId());
            model.addAttribute("error", messages.get("flash.field.limit"));
            populateFieldsModel(store, productId, model, fieldForm);
            return "admin/fields";
        }
        try { personalizationAdminService.addField(store.getStoreId(), productId, fieldForm); }
        catch (IllegalArgumentException exception) {
            model.addAttribute("error", imageConfigurationError(exception));
            populateFieldsModel(store, productId, model, fieldForm);
            return "admin/fields";
        }
        LOGGER.info("admin.fields.add.done store_id={} product_id={}", store.getStoreId(), productId);
        redirectAttributes.addFlashAttribute("message", messages.get("flash.field.created"));
        return "redirect:/admin/products/{productId}/fields";
    }

    @PostMapping("/admin/products/{productId}/fields/{fieldId}")
    public String updateField(
            @PathVariable Long productId,
            @PathVariable Long fieldId,
            @Valid @ModelAttribute FieldForm fieldForm,
            BindingResult bindingResult,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Store store = adminStoreService.requireCurrentStore(session);
        LOGGER.info("admin.fields.update store_id={} product_id={} field_id={}", store.getStoreId(), productId, fieldId);
        if (bindingResult.hasErrors()) {
            LOGGER.warn("admin.fields.update.validation_error store_id={} product_id={} field_id={}", store.getStoreId(), productId, fieldId);
            populateFieldsModel(store, productId, model, new FieldForm());
            model.addAttribute("editedFieldId", fieldId);
            model.addAttribute("editedFieldForm", fieldForm);
            model.addAttribute("error", messages.get("flash.field.invalid"));
            return "admin/fields";
        }
        try { personalizationAdminService.updateField(store.getStoreId(), productId, fieldId, fieldForm); }
        catch (IllegalArgumentException exception) {
            populateFieldsModel(store, productId, model, new FieldForm());
            model.addAttribute("editedFieldId", fieldId);
            model.addAttribute("editedFieldForm", fieldForm);
            model.addAttribute("error", imageConfigurationError(exception));
            return "admin/fields";
        }
        LOGGER.info("admin.fields.update.done store_id={} product_id={} field_id={}", store.getStoreId(), productId, fieldId);
        redirectAttributes.addFlashAttribute("message", messages.get("flash.field.updated"));
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
        redirectAttributes.addFlashAttribute("message", messages.get("flash.field.deleted"));
        return "redirect:/admin/products/{productId}/fields";
    }

    @PostMapping("/admin/products/{productId}/delete")
    public String deleteRule(@PathVariable Long productId, HttpSession session, RedirectAttributes redirectAttributes) {
        Store store = adminStoreService.requireCurrentStore(session);
        LOGGER.info("admin.rule.delete store_id={} product_id={}", store.getStoreId(), productId);
        personalizationAdminService.deleteRule(store.getStoreId(), productId);
        redirectAttributes.addFlashAttribute("message", messages.get("flash.product.removed"));
        return "redirect:/admin/products";
    }

    private String imageConfigurationError(IllegalArgumentException error) {
        if (error instanceof ImagePlanLimitException limit) return messages.get(limit.getMessage(), limit.arguments());
        return messages.get("image.options.invalid");
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
        model.addAttribute("imagesEnabled", personalizationAdminService.imagesEnabled());
        model.addAttribute("canUseImages", planLimitService.canConfigureImageProduct(store, productId));
        model.addAttribute("imageProductLimit", planLimitService.imageProductLimit(store.getEffectivePlan()));
        model.addAttribute("imageProductsUsed", planLimitService.imageProductIds(store.getStoreId()).size());
        model.addAttribute("imageOptionLimit", planLimitService.imageOptionLimit(store.getEffectivePlan()));
        model.addAttribute("fieldForm", fieldForm);
        model.addAttribute("usage", planLimitService.usage(store, rule.getFields().size()));
        model.addAttribute("canAddField", planLimitService.canAddField(store, rule.getId()));
    }

    private Set<Long> configuredProductIds(Iterable<PersonalizationRule> rules) {
        return java.util.stream.StreamSupport.stream(rules.spliterator(), false)
                .map(PersonalizationRule::getProductId)
                .collect(Collectors.toSet());
    }

    static List<ProductSummary> prioritizedProducts(ProductPage page, List<PersonalizationRule> rules,
                                                     List<Long> configuredFieldProductIds) {
        Set<Long> configured = Set.copyOf(configuredFieldProductIds);
        Collator names = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
        names.setStrength(Collator.PRIMARY);
        Comparator<ProductSummary> alphabetical = Comparator
                .comparing(ProductSummary::name, names)
                .thenComparing(ProductSummary::id);
        if (page.query() != null) {
            // A busca pertence a API: apenas reorganiza seus resultados, sem exibir produtos fora da busca.
            return page.items().stream()
                    .sorted(Comparator.comparing((ProductSummary product) -> !configured.contains(product.id()))
                            .thenComparing(alphabetical))
                    .toList();
        }
        List<ProductSummary> products = new ArrayList<>();
        if (page.page() == 1) {
            // Regras locais permitem mostrar todos os configurados antes da primeira pagina da API.
            for (PersonalizationRule rule : rules) {
                if (!configured.contains(rule.getProductId())) {
                    continue;
                }
                String name = rule.getProductName();
                if (name == null || name.isBlank()) {
                    name = page.items().stream()
                            .filter(product -> product.id().equals(rule.getProductId()))
                            .map(ProductSummary::name)
                            .findFirst().orElse(Long.toString(rule.getProductId()));
                }
                products.add(new ProductSummary(rule.getProductId(), name));
            }
        }
        page.items().stream().filter(product -> !configured.contains(product.id())).forEach(products::add);
        return products.stream()
                .sorted(Comparator.comparing((ProductSummary product) -> !configured.contains(product.id()))
                        .thenComparing(alphabetical))
                .toList();
    }

    private boolean reconcilePendingEfi(Long storeId, boolean respectCooldown) {
        var subscription = paymentSubscriptionService.find(storeId).orElse(null);
        if (subscription == null || subscription.getProvider() != PaymentProviderType.EFI
                || subscription.getStatus() != PaymentSubscriptionStatus.PENDING
                || subscription.getProviderSubscriptionId() == null) return false;
        if (respectCooldown && subscription.getLastSyncedAt() != null
                && Duration.between(subscription.getLastSyncedAt(), Instant.now()).compareTo(Duration.ofSeconds(30)) < 0) {
            return false;
        }
        try {
            paymentSubscriptionService.reconcile(storeId);
            return true;
        } catch (RuntimeException ex) {
            LOGGER.warn("payments.efi.reconcile_failed store_id={} type={}", storeId, ex.getClass().getSimpleName());
            return false;
        }
    }

    private boolean expirePendingEfiIfDue(Long storeId) {
        try {
            return paymentSubscriptionService.expirePendingEfi(storeId);
        } catch (RuntimeException ex) {
            LOGGER.warn("payments.efi.pending_expiration_deferred store_id={} type={}",
                    storeId, ex.getClass().getSimpleName());
            return false;
        }
    }

    private String merchantError(RuntimeException exception) {
        return exception instanceof PaymentGatewayException
                ? paymentSubscriptionService.customerFacingError(exception.getMessage())
                : exception.getMessage();
    }

    static String formatBillingPrice(String currency, java.math.BigDecimal amount) {
        if ("BRL".equals(currency)) {
            return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(amount);
        }
        return currency + " " + amount;
    }
}
