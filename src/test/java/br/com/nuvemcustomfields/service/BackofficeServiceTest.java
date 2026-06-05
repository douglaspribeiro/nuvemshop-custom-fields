package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
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
