package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.WebhookPayload;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookLifecycleServiceTest {

    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final NuvemshopBillingService billingService = mock(NuvemshopBillingService.class);
    private final IntegrationLogService integrationLogService = mock(IntegrationLogService.class);
    private final ScriptInstallService scriptInstallService = mock(ScriptInstallService.class);
    private final WebhookLifecycleService service = new WebhookLifecycleService(
            storeRepository,
            mock(PersonalizationRuleRepository.class),
            scriptInstallService,
            integrationLogService,
            billingService
    );

    @Test
    void marksStoreUninstalledEvenWhenRemoteScriptCleanupFails() {
        Store store = store();
        store.setPlan(br.com.nuvemcustomfields.entity.PlanType.PREMIUM);
        when(storeRepository.findByStoreId(123L)).thenReturn(Optional.of(store));
        doThrow(new IllegalStateException("Invalid access token"))
                .when(scriptInstallService)
                .removePersonalizerScripts(store);

        service.handle(new WebhookPayload(123L, "app/uninstalled", null));

        assertThat(store.getUninstalledAt()).isNotNull();
        verify(storeRepository).save(store);
        verify(integrationLogService).info(
                123L,
                "webhook.app_uninstalled",
                "Loja desinstalada; assinatura e script foram limpos."
        );
    }

    @Test
    void syncsSubscriptionUpdatedWebhook() {
        Store store = store();
        when(storeRepository.findByStoreId(123L)).thenReturn(Optional.of(store));

        service.handle(new WebhookPayload(123L, "subscription/updated", null));

        verify(billingService).syncSubscription(store);
        verify(integrationLogService).info(123L, "webhook.subscription_updated", "Assinatura sincronizada pela Nuvemshop.");
    }

    @Test
    void marksAppSuspendedWebhook() {
        service.handle(new WebhookPayload(123L, "app/suspended", null));

        verify(billingService).markSuspended(123L);
        verify(integrationLogService).warn(123L, "webhook.app_suspended", "App suspenso pela Nuvemshop; acesso premium bloqueado.");
    }

    @Test
    void marksAndSyncsAppResumedWebhook() {
        Store store = store();
        when(storeRepository.findByStoreId(123L)).thenReturn(Optional.of(store));

        service.handle(new WebhookPayload(123L, "app/resumed", null));

        verify(billingService).markResumed(123L);
        verify(billingService).syncSubscription(store);
        verify(integrationLogService).info(123L, "webhook.app_resumed", "App retomado pela Nuvemshop; acesso premium reativado.");
    }

    private Store store() {
        Store store = new Store();
        store.setStoreId(123L);
        return store;
    }
}
