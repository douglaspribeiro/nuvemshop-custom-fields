package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PaymentSubscription;
import br.com.nuvemcustomfields.entity.PaymentSubscriptionStatus;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.i18n.Messages;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.service.AdminStoreService;
import br.com.nuvemcustomfields.service.IntegrationLogService;
import br.com.nuvemcustomfields.service.NicheTemplateService;
import br.com.nuvemcustomfields.service.NuvemshopApiClient;
import br.com.nuvemcustomfields.service.PaymentSubscriptionService;
import br.com.nuvemcustomfields.service.PersonalizationAdminService;
import br.com.nuvemcustomfields.service.PlanLimitService;
import br.com.nuvemcustomfields.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.*;

class AdminPendingEfiReconcileTest {
    @Test
    void checksPendingEfiWhenOpeningHome() {
        Fixture fixture = new Fixture();
        PaymentSubscription pending = fixture.pending();
        when(fixture.payments.find(42L)).thenReturn(Optional.of(pending));

        fixture.controller.index(new MockHttpSession(), new ExtendedModelMap());

        verify(fixture.payments).reconcile(42L);
    }

    @Test
    void doesNotRepeatRecentCheckOrCheckOtherProviders() {
        Fixture fixture = new Fixture();
        PaymentSubscription pending = fixture.pending();
        pending.setLastSyncedAt(Instant.now());
        when(fixture.payments.find(42L)).thenReturn(Optional.of(pending));
        fixture.controller.index(new MockHttpSession(), new ExtendedModelMap());
        pending.setProvider(PaymentProviderType.MERCADO_PAGO);
        pending.setLastSyncedAt(null);
        fixture.controller.index(new MockHttpSession(), new ExtendedModelMap());

        verify(fixture.payments, never()).reconcile(42L);
    }

    private static class Fixture {
        final AdminStoreService stores = mock(AdminStoreService.class);
        final PaymentSubscriptionService payments = mock(PaymentSubscriptionService.class);
        final PersonalizationAdminService personalization = mock(PersonalizationAdminService.class);
        final AdminController controller = new AdminController(stores, mock(IntegrationLogService.class),
                mock(NuvemshopApiClient.class), mock(NicheTemplateService.class), mock(PlanLimitService.class),
                personalization, mock(ReportService.class),
                mock(NuvemshopProperties.class), payments, mock(Messages.class));

        Fixture() {
            Store store = new Store();
            store.setStoreId(42L);
            when(stores.requireCurrentStore(any())).thenReturn(store);
            when(personalization.listRules(42L)).thenReturn(java.util.List.of());
        }

        PaymentSubscription pending() {
            PaymentSubscription subscription = new PaymentSubscription();
            subscription.setProvider(PaymentProviderType.EFI);
            subscription.setStatus(PaymentSubscriptionStatus.PENDING);
            subscription.setProviderSubscriptionId("efi-sub-42");
            return subscription;
        }
    }
}
