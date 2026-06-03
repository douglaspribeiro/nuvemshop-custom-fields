package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PlanLimitServiceTest {

    private final PlanLimitService service = new PlanLimitService(
            mock(PersonalizationRuleRepository.class),
            mock(PersonalizationFieldRepository.class)
    );

    @Test
    void exposesCommercialLimitsByPlan() {
        assertThat(service.productLimit(PlanType.FREE)).isEqualTo(1);
        assertThat(service.fieldLimit(PlanType.FREE)).isEqualTo(1);
        assertThat(service.productLimit(PlanType.PREMIUM)).isEqualTo(10);
        assertThat(service.fieldLimit(PlanType.PREMIUM)).isEqualTo(3);
        assertThat(service.productLimit(PlanType.PREMIUM_PLUS)).isEqualTo(-1);
        assertThat(service.fieldLimit(PlanType.PREMIUM_PLUS)).isEqualTo(-1);
    }
}
