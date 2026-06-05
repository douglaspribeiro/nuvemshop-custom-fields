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
    private final NuvemshopBillingService billingService;

    public WebhookLifecycleService(
            StoreRepository storeRepository,
            PersonalizationRuleRepository ruleRepository,
            ScriptInstallService scriptInstallService,
            IntegrationLogService integrationLogService,
            NuvemshopBillingService billingService
    ) {
        this.storeRepository = storeRepository;
        this.ruleRepository = ruleRepository;
        this.scriptInstallService = scriptInstallService;
        this.integrationLogService = integrationLogService;
        this.billingService = billingService;
    }

    @Transactional
    public void handle(WebhookPayload payload) {
        if (payload == null || payload.event() == null) {
            return;
        }
        switch (payload.event()) {
            case "app/uninstalled" -> handleUninstalled(payload.storeId());
            case "product/deleted" -> handleProductDeleted(payload.storeId(), payload.id());
            case "subscription/updated" -> handleSubscriptionUpdated(payload.storeId());
            case "app/suspended" -> handleAppSuspended(payload.storeId());
            case "app/resumed" -> handleAppResumed(payload.storeId());
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
            store.setBillingPlanExternalId(null);
            store.setBillingAmountCurrency(null);
            store.setBillingAmountValue(null);
            store.setBillingNextExecution(null);
            store.setBillingLastExecution(null);
            store.setBillingSuspended(false);
            store.setBillingLastSyncedAt(Instant.now());
            store.setBillingLastError(null);
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

    private void handleSubscriptionUpdated(Long storeId) {
        storeRepository.findByStoreId(storeId).ifPresent(store -> {
            billingService.syncSubscription(store);
            integrationLogService.info(storeId, "webhook.subscription_updated", "Assinatura sincronizada pela Nuvemshop.");
        });
    }

    private void handleAppSuspended(Long storeId) {
        billingService.markSuspended(storeId);
        integrationLogService.warn(storeId, "webhook.app_suspended", "App suspenso pela Nuvemshop; acesso premium bloqueado.");
    }

    private void handleAppResumed(Long storeId) {
        billingService.markResumed(storeId);
        storeRepository.findByStoreId(storeId).ifPresent(billingService::syncSubscription);
        integrationLogService.info(storeId, "webhook.app_resumed", "App retomado pela Nuvemshop; acesso premium reativado.");
    }
}
