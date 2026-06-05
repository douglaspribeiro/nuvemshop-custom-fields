package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.properties.BackofficeProperties;
import br.com.nuvemcustomfields.repository.FeatureFlagRepository;
import br.com.nuvemcustomfields.repository.IntegrationLogRepository;
import br.com.nuvemcustomfields.repository.PlanEventRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.BackofficeService;
import br.com.nuvemcustomfields.service.ManagementReportService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BackofficeController {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackofficeController.class);

    private final BackofficeProperties properties;
    private final StoreRepository storeRepository;
    private final PlanEventRepository planEventRepository;
    private final FeatureFlagRepository featureFlagRepository;
    private final IntegrationLogRepository integrationLogRepository;
    private final BackofficeService backofficeService;
    private final ManagementReportService managementReportService;
    private final String appVersion;

    public BackofficeController(
            BackofficeProperties properties,
            StoreRepository storeRepository,
            PlanEventRepository planEventRepository,
            FeatureFlagRepository featureFlagRepository,
            IntegrationLogRepository integrationLogRepository,
            BackofficeService backofficeService,
            ManagementReportService managementReportService,
            @Value("${APP_VERSION:dev}") String appVersion
    ) {
        this.properties = properties;
        this.storeRepository = storeRepository;
        this.planEventRepository = planEventRepository;
        this.featureFlagRepository = featureFlagRepository;
        this.integrationLogRepository = integrationLogRepository;
        this.backofficeService = backofficeService;
        this.managementReportService = managementReportService;
        this.appVersion = appVersion;
    }

    @GetMapping("/backoffice/login")
    public String login(Model model) {
        LOGGER.info("backoffice.login.open");
        model.addAttribute("appVersion", appVersion);
        return "backoffice/login";
    }

    @PostMapping("/backoffice/login")
    public String login(@RequestParam String username, @RequestParam String password, HttpSession session, RedirectAttributes redirectAttributes) {
        LOGGER.info("backoffice.login.submit session_id={} username={}", session.getId(), username);
        if (properties.username().equals(username) && properties.password().equals(password)) {
            session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
            LOGGER.info("backoffice.login.success session_id={} username={}", session.getId(), username);
            return "redirect:/backoffice";
        }
        LOGGER.warn("backoffice.login.failed session_id={} username={}", session.getId(), username);
        redirectAttributes.addFlashAttribute("error", "Credenciais invalidas.");
        return "redirect:/backoffice/login";
    }

    @GetMapping("/backoffice")
    public String index(Model model) {
        LOGGER.info("backoffice.index.open");
        long stores = storeRepository.count();
        long activeStores = backofficeService.activeStores();
        long rules = backofficeService.fields();
        long flags = featureFlagRepository.count();
        model.addAttribute("stores", stores);
        model.addAttribute("activeStores", activeStores);
        model.addAttribute("rules", rules);
        model.addAttribute("flags", flags);
        LOGGER.info("backoffice.index.loaded stores={} active_stores={} rules={} flags={}", stores, activeStores, rules, flags);
        return "backoffice/index";
    }

    @GetMapping("/backoffice/stores")
    public String stores(Model model) {
        LOGGER.info("backoffice.stores.open");
        var stores = storeRepository.findAll();
        model.addAttribute("stores", stores);
        LOGGER.info("backoffice.stores.loaded stores_count={}", stores.size());
        return "backoffice/stores";
    }

    @GetMapping("/backoffice/stores/{storeId}")
    public String store(@PathVariable Long storeId, Model model) {
        LOGGER.info("backoffice.store.open store_id={}", storeId);
        model.addAttribute("store", storeRepository.findByStoreId(storeId).orElseThrow());
        model.addAttribute("planTypes", PlanType.values());
        model.addAttribute("events", planEventRepository.findTop20ByStoreIdOrderByCreatedAtDesc(storeId));
        model.addAttribute("logs", integrationLogRepository.findTop20ByStoreIdOrderByCreatedAtDesc(storeId));
        LOGGER.info("backoffice.store.loaded store_id={}", storeId);
        return "backoffice/store";
    }

    @PostMapping("/backoffice/stores/{storeId}/plan")
    public String plan(@PathVariable Long storeId, @RequestParam PlanType plan, RedirectAttributes redirectAttributes) {
        LOGGER.info("backoffice.plan.override store_id={} plan={}", storeId, plan);
        backofficeService.overridePlan(storeId, plan);
        redirectAttributes.addFlashAttribute("message", "Plano atualizado.");
        return "redirect:/backoffice/stores/{storeId}";
    }

    @PostMapping("/backoffice/stores/{storeId}/courtesy-premium")
    public String courtesyPremium(
            @PathVariable Long storeId,
            @RequestParam(defaultValue = "false") boolean courtesyPremium,
            @RequestParam(required = false) String courtesyPremiumReason,
            RedirectAttributes redirectAttributes
    ) {
        LOGGER.info("backoffice.courtesy_premium.update store_id={} courtesy_premium={}", storeId, courtesyPremium);
        backofficeService.updateCourtesyPremium(storeId, courtesyPremium, courtesyPremiumReason);
        redirectAttributes.addFlashAttribute("message", courtesyPremium ? "Premium Cortesia ativado." : "Premium Cortesia removido.");
        return "redirect:/backoffice/stores/{storeId}";
    }

    @PostMapping("/backoffice/stores/{storeId}/enter")
    public String enterStoreMode(@PathVariable Long storeId, HttpSession session, RedirectAttributes redirectAttributes) {
        LOGGER.info("backoffice.store_mode.enter store_id={} session_id={}", storeId, session.getId());
        var store = storeRepository.findActiveByStoreId(storeId);
        if (store.isEmpty()) {
            LOGGER.warn("backoffice.store_mode.enter.inactive_or_missing store_id={}", storeId);
            redirectAttributes.addFlashAttribute("error", "Loja ativa nao encontrada para entrar no modo loja.");
            return "redirect:/backoffice/stores/{storeId}";
        }
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, storeId);
        session.setAttribute(BackofficeSessionInterceptor.STORE_MODE_SESSION_KEY, true);
        session.setAttribute(BackofficeSessionInterceptor.STORE_MODE_STORE_ID_SESSION_KEY, storeId);
        redirectAttributes.addFlashAttribute("message", "Voce esta editando a loja pelo backoffice.");
        return "redirect:/admin";
    }

    @PostMapping("/backoffice/store-mode/exit")
    public String exitStoreMode(HttpSession session) {
        Object storeId = session.getAttribute(BackofficeSessionInterceptor.STORE_MODE_STORE_ID_SESSION_KEY);
        LOGGER.info("backoffice.store_mode.exit store_id={} session_id={}", storeId, session.getId());
        session.removeAttribute(AdminSessionInterceptor.STORE_SESSION_KEY);
        session.removeAttribute(BackofficeSessionInterceptor.STORE_MODE_SESSION_KEY);
        session.removeAttribute(BackofficeSessionInterceptor.STORE_MODE_STORE_ID_SESSION_KEY);
        if (storeId instanceof Long id) {
            return "redirect:/backoffice/stores/" + id;
        }
        return "redirect:/backoffice/stores";
    }

    @GetMapping("/backoffice/flags")
    public String flags(Model model) {
        LOGGER.info("backoffice.flags.open");
        var flags = featureFlagRepository.findAll();
        model.addAttribute("flags", flags);
        LOGGER.info("backoffice.flags.loaded flags_count={}", flags.size());
        return "backoffice/flags";
    }

    @GetMapping("/backoffice/reports")
    public String reports(Model model) {
        LOGGER.info("backoffice.reports.open");
        model.addAttribute("report", managementReportService.report());
        return "backoffice/reports";
    }

    @PostMapping("/backoffice/flags")
    public String saveFlag(@RequestParam String key, @RequestParam(defaultValue = "false") boolean enabled, @RequestParam(required = false) String description) {
        LOGGER.info("backoffice.flags.save key={} enabled={}", key, enabled);
        backofficeService.saveFlag(key, enabled, description);
        return "redirect:/backoffice/flags";
    }
}
