package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.properties.BackofficeProperties;
import br.com.nuvemcustomfields.repository.FeatureFlagRepository;
import br.com.nuvemcustomfields.repository.IntegrationLogRepository;
import br.com.nuvemcustomfields.repository.PlanEventRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.BackofficeService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BackofficeController {

    private final BackofficeProperties properties;
    private final StoreRepository storeRepository;
    private final PlanEventRepository planEventRepository;
    private final FeatureFlagRepository featureFlagRepository;
    private final IntegrationLogRepository integrationLogRepository;
    private final BackofficeService backofficeService;

    public BackofficeController(
            BackofficeProperties properties,
            StoreRepository storeRepository,
            PlanEventRepository planEventRepository,
            FeatureFlagRepository featureFlagRepository,
            IntegrationLogRepository integrationLogRepository,
            BackofficeService backofficeService
    ) {
        this.properties = properties;
        this.storeRepository = storeRepository;
        this.planEventRepository = planEventRepository;
        this.featureFlagRepository = featureFlagRepository;
        this.integrationLogRepository = integrationLogRepository;
        this.backofficeService = backofficeService;
    }

    @GetMapping("/backoffice/login")
    public String login() {
        return "backoffice/login";
    }

    @PostMapping("/backoffice/login")
    public String login(@RequestParam String username, @RequestParam String password, HttpSession session, RedirectAttributes redirectAttributes) {
        if (properties.username().equals(username) && properties.password().equals(password)) {
            session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
            return "redirect:/backoffice";
        }
        redirectAttributes.addFlashAttribute("error", "Credenciais invalidas.");
        return "redirect:/backoffice/login";
    }

    @GetMapping("/backoffice")
    public String index(Model model) {
        model.addAttribute("stores", storeRepository.count());
        model.addAttribute("activeStores", backofficeService.activeStores());
        model.addAttribute("rules", backofficeService.fields());
        model.addAttribute("flags", featureFlagRepository.count());
        return "backoffice/index";
    }

    @GetMapping("/backoffice/stores")
    public String stores(Model model) {
        model.addAttribute("stores", storeRepository.findAll());
        return "backoffice/stores";
    }

    @GetMapping("/backoffice/stores/{storeId}")
    public String store(@PathVariable Long storeId, Model model) {
        model.addAttribute("store", storeRepository.findByStoreId(storeId).orElseThrow());
        model.addAttribute("planTypes", PlanType.values());
        model.addAttribute("events", planEventRepository.findTop20ByStoreIdOrderByCreatedAtDesc(storeId));
        model.addAttribute("logs", integrationLogRepository.findTop20ByStoreIdOrderByCreatedAtDesc(storeId));
        return "backoffice/store";
    }

    @PostMapping("/backoffice/stores/{storeId}/plan")
    public String plan(@PathVariable Long storeId, @RequestParam PlanType plan, RedirectAttributes redirectAttributes) {
        backofficeService.overridePlan(storeId, plan);
        redirectAttributes.addFlashAttribute("message", "Plano atualizado.");
        return "redirect:/backoffice/stores/{storeId}";
    }

    @GetMapping("/backoffice/flags")
    public String flags(Model model) {
        model.addAttribute("flags", featureFlagRepository.findAll());
        return "backoffice/flags";
    }

    @PostMapping("/backoffice/flags")
    public String saveFlag(@RequestParam String key, @RequestParam(defaultValue = "false") boolean enabled, @RequestParam(required = false) String description) {
        backofficeService.saveFlag(key, enabled, description);
        return "redirect:/backoffice/flags";
    }
}
