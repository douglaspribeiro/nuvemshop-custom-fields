package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.WebhookPayload;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class WebhookLifecycleService {

    private final StoreRepository storeRepository;
    private final PersonalizationRuleRepository ruleRepository;
    private final ScriptInstallService scriptInstallService;
    private final IntegrationLogService integrationLogService;

    public WebhookLifecycleService(
            StoreRepository storeRepository,
            PersonalizationRuleRepository ruleRepository,
            ScriptInstallService scriptInstallService,
            IntegrationLogService integrationLogService
    ) {
        this.storeRepository = storeRepository;
        this.ruleRepository = ruleRepository;
        this.scriptInstallService = scriptInstallService;
        this.integrationLogService = integrationLogService;
    }

    @Transactional
    public void handle(WebhookPayload payload) {
        if (payload == null || payload.event() == null) {
            return;
        }
        switch (payload.event()) {
            case "app/uninstalled" -> handleUninstalled(payload.storeId());
            case "product/deleted" -> handleProductDeleted(payload.storeId(), payload.id());
            default -> {
            }
        }
    }

    private void handleUninstalled(Long storeId) {
        storeRepository.findByStoreId(storeId).ifPresent(store -> {
            scriptInstallService.removePersonalizerScripts(store);
            store.setUninstalledAt(Instant.now());
            store.setSubscriptionId(null);
            store.setPlan(PlanType.FREE);
            storeRepository.save(store);
            integrationLogService.info(storeId, "webhook.app_uninstalled", "Loja desinstalada; assinatura e script foram limpos.");
        });
    }

    private void handleProductDeleted(Long storeId, Long productId) {
        if (storeId != null && productId != null) {
            ruleRepository.deleteByStoreIdAndProductId(storeId, productId);
            integrationLogService.info(storeId, "webhook.product_deleted", "Regras removidas para produto " + productId + ".");
        }
    }
}
