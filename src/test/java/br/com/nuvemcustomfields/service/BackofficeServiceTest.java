package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.entity.PlanType;
import java.time.Instant;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import br.com.nuvemcustomfields.repository.FeatureFlagRepository;
import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.PlanEventRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackofficeServiceTest {

    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final BackofficeService service = new BackofficeService(
            storeRepository,
            mock(PersonalizationRuleRepository.class),
            mock(PersonalizationFieldRepository.class),
            mock(PlanEventRepository.class),
            mock(FeatureFlagRepository.class)
    );

    @Test
    void grantsThirtyDaysAndExpiresWithoutChangingTheFreeBasePlan() {
        Store store = new Store();
        store.setStoreId(123L);
        when(storeRepository.findByStoreId(123L)).thenReturn(Optional.of(store));

        service.grantOrChangePlanBonus(123L, PlanType.PREMIUM);

        assertThat(store.getPlan()).isEqualTo(PlanType.FREE);
        assertThat(store.getEffectivePlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(store.isCourtesyPremium()).isTrue();
        assertThat(Duration.between(store.getPremiumBonusStartedAt(), store.getPremiumBonusExpiresAt()))
                .isEqualTo(Duration.ofDays(30));
        assertThatThrownBy(() -> service.grantOrChangePlanBonus(123L, PlanType.PREMIUM))
                .isInstanceOf(IllegalArgumentException.class);

        store.setPremiumBonusExpiresAt(Instant.now().minusSeconds(1));
        assertThat(store.getEffectivePlan()).isEqualTo(PlanType.FREE);
        assertThat(store.isCourtesyPremium()).isFalse();
    }

    @Test
    void rejectsPaidOrInactiveStores() {
        Store store = new Store();
        store.setStoreId(123L);
        when(storeRepository.findByStoreId(123L)).thenReturn(Optional.of(store));
        store.setPlan(PlanType.PREMIUM);
        assertThatThrownBy(() -> service.grantOrChangePlanBonus(123L, PlanType.PREMIUM_PLUS))
                .isInstanceOf(IllegalArgumentException.class);
        store.setPlan(PlanType.FREE);
        store.setUninstalledAt(Instant.now());
        assertThatThrownBy(() -> service.grantOrChangePlanBonus(123L, PlanType.PREMIUM_PLUS))
                .isInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.verify(storeRepository, org.mockito.Mockito.never()).save(store);
    }

    @Test
    void changesTemporaryPlanWithoutRestartingThirtyDays() {
        Store store = new Store();
        store.setStoreId(123L);
        when(storeRepository.findByStoreId(123L)).thenReturn(Optional.of(store));

        assertThat(service.grantOrChangePlanBonus(123L, PlanType.PREMIUM)).isFalse();
        Instant startedAt = store.getPremiumBonusStartedAt();
        Instant expiresAt = store.getPremiumBonusExpiresAt();

        assertThat(service.grantOrChangePlanBonus(123L, PlanType.PREMIUM_PLUS)).isTrue();

        assertThat(store.getEffectivePlan()).isEqualTo(PlanType.PREMIUM_PLUS);
        assertThat(store.getPremiumBonusStartedAt()).isEqualTo(startedAt);
        assertThat(store.getPremiumBonusExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void rejectsAFreePlanAsTemporaryBonus() {
        assertThatThrownBy(() -> service.grantOrChangePlanBonus(123L, PlanType.FREE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Essencial ou Pro");
    }

    @Test
    void manualPlanChangeEndsTheBonus() {
        Store store = new Store();
        store.setStoreId(123L);
        store.setPremiumBonusExpiresAt(Instant.now().plusSeconds(3600));
        when(storeRepository.findByStoreId(123L)).thenReturn(Optional.of(store));
        service.overridePlan(123L, PlanType.FREE);
        assertThat(store.isPremiumBonusActive()).isFalse();
        assertThat(store.getEffectivePlan()).isEqualTo(PlanType.FREE);
    }

    @Test
    void updatesCourtesyPremiumFlagAndReason() {
        Store store = new Store();
        store.setStoreId(123L);
        when(storeRepository.findByStoreId(123L)).thenReturn(Optional.of(store));

        service.updateCourtesyPremium(123L, true, " Loja interna ");

        assertThat(store.isCourtesyPremium()).isTrue();
        assertThat(store.getCourtesyPremiumReason()).isEqualTo("Loja interna");
        verify(storeRepository).save(store);
    }

    @Test
    void clearsReasonWhenCourtesyPremiumIsDisabled() {
        Store store = new Store();
        store.setStoreId(123L);
        store.setCourtesyPremium(true);
        store.setCourtesyPremiumReason("Teste");
        when(storeRepository.findByStoreId(123L)).thenReturn(Optional.of(store));

        service.updateCourtesyPremium(123L, false, "Teste");

        assertThat(store.isCourtesyPremium()).isFalse();
        assertThat(store.getCourtesyPremiumReason()).isNull();
        verify(storeRepository).save(store);
    }
}
