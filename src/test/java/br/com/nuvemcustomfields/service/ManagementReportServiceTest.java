package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.PlanEventRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagementReportServiceTest {

    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final PersonalizationRuleRepository ruleRepository = mock(PersonalizationRuleRepository.class);
    private final PersonalizationFieldRepository fieldRepository = mock(PersonalizationFieldRepository.class);
    private final PlanEventRepository planEventRepository = mock(PlanEventRepository.class);
    private final ManagementReportService service = new ManagementReportService(
            storeRepository,
            ruleRepository,
            fieldRepository,
            planEventRepository
    );

    @Test
    void excludesCourtesyPremiumStoresFromEstimatedMrr() {
        Store billablePremium = store(PlanType.PREMIUM, false);
        Store courtesyPremium = store(PlanType.PREMIUM, true);
        Store billablePremiumPlus = store(PlanType.PREMIUM_PLUS, false);
        Store internalFree = store(PlanType.FREE_GRATIS, false);
        when(storeRepository.findAll()).thenReturn(List.of(billablePremium, courtesyPremium, billablePremiumPlus, internalFree));
        when(ruleRepository.findAll()).thenReturn(List.of());

        var report = service.report();

        assertThat(report.freeStores()).isEqualTo(1);
        assertThat(report.premiumStores()).isEqualTo(2);
        assertThat(report.premiumPlusStores()).isEqualTo(1);
        assertThat(report.estimatedMrr()).isEqualByComparingTo(new BigDecimal("29.98"));
    }

    private Store store(PlanType plan, boolean courtesyPremium) {
        Store store = new Store();
        store.setPlan(plan);
        store.setCourtesyPremium(courtesyPremium);
        return store;
    }
}
