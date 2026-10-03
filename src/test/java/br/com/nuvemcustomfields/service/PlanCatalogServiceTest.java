package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.repository.PlanAssetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(PlanCatalogService.class)
class PlanCatalogServiceTest {
    @Autowired PlanCatalogService catalog;
    @Autowired PlanAssetRepository repository;

    @Test void initialCatalogIncludesAllCurrentPlansAndLimits() {
        assertThat(catalog.activePlansByType()).hasSize(5);
        assertThat(catalog.activePlan(PlanType.PREMIUM_PLUS).getProductLimit()).isEqualTo(50);
        assertThat(catalog.activePlan(PlanType.PREMIUM_ULTRA).getAmount()).isEqualByComparingTo("59.90");
        assertThat(catalog.activePlan(PlanType.FREE_GRATIS).getFieldLimit()).isEqualTo(3);
    }

    @Test void scheduledVersionClosesPreviousPeriodWithoutChangingCurrentLimits() {
        var today = LocalDate.now();
        var previous = catalog.activePlan(PlanType.PREMIUM);
        var next = create(today.plusDays(10), 25);
        assertThat(previous.getEffectiveUntil()).isEqualTo(today.plusDays(9));
        assertThat(catalog.activePlan(PlanType.PREMIUM).getProductLimit()).isEqualTo(10);
        assertThat(repository.findActiveByPlanTypeOnDate(PlanType.PREMIUM, today.plusDays(10))).containsExactly(next);
    }

    @Test void supportsImmediateAndSameDayReplacementPreservingHistory() {
        var first = create(LocalDate.now(), 20);
        var second = create(LocalDate.now(), 30);
        assertThat(first.isActive()).isFalse();
        assertThat(catalog.activePlan(PlanType.PREMIUM)).isEqualTo(second);
        assertThat(catalog.allVersions()).contains(first, second);
        assertThat(repository.findActiveByPlanTypeOnDate(PlanType.PREMIUM, LocalDate.now())).hasSize(1);
    }

    @Test void rejectsSchedulingBeforeExistingFutureVersionAndInvalidLimits() {
        create(LocalDate.now().plusDays(20), 20);
        assertThatThrownBy(() -> create(LocalDate.now().plusDays(10), 30)).isInstanceOf(IllegalArgumentException.class);
        assertThat(catalog.activePlan(PlanType.PREMIUM).getProductLimit()).isEqualTo(10);
        assertThatThrownBy(() -> create(LocalDate.now().plusDays(30), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private br.com.nuvemcustomfields.entity.PlanAsset create(LocalDate date, long products) {
        return catalog.createVersion(PlanType.PREMIUM, "Essencial", "Descrição", "PREMIUM", "BRL",
                new BigDecimal("19.99"), products, 3, date, null);
    }
}
