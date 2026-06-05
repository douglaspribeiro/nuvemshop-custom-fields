package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.ManagementReport;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.PlanEventRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;

@Service
public class ManagementReportService {

    private final StoreRepository storeRepository;
    private final PersonalizationRuleRepository ruleRepository;
    private final PersonalizationFieldRepository fieldRepository;
    private final PlanEventRepository planEventRepository;
    private final PlanCatalogService planCatalogService;

    public ManagementReportService(
            StoreRepository storeRepository,
            PersonalizationRuleRepository ruleRepository,
            PersonalizationFieldRepository fieldRepository,
            PlanEventRepository planEventRepository,
            PlanCatalogService planCatalogService
    ) {
        this.storeRepository = storeRepository;
        this.ruleRepository = ruleRepository;
        this.fieldRepository = fieldRepository;
        this.planEventRepository = planEventRepository;
        this.planCatalogService = planCatalogService;
    }

    public ManagementReport report() {
        var stores = storeRepository.findAll();
        long free = stores.stream().filter(store -> store.getPlan() == PlanType.FREE).count();
        long premium = stores.stream().filter(store -> store.getPlan() == PlanType.PREMIUM).count();
        long premiumPlus = stores.stream().filter(store -> store.getPlan() == PlanType.PREMIUM_PLUS).count();
        var currentPlans = planCatalogService.activePlansByType();
        BigDecimal estimatedMrr = stores.stream()
                .filter(store -> store.isActive() && !store.isCourtesyPremium() && !store.isBillingSuspended() && store.getPlan() != PlanType.FREE)
                .map(store -> currentPlans.get(store.getPlan()).getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        YearMonth projectedMonth = YearMonth.now();
        var projectedStores = stores.stream()
                .filter(store -> store.isActive() && !store.isCourtesyPremium() && !store.isBillingSuspended() && store.getPlan() != PlanType.FREE)
                .filter(store -> store.getBillingNextExecution() != null)
                .filter(store -> YearMonth.from(store.getBillingNextExecution()).equals(projectedMonth))
                .toList();
        BigDecimal projectedMonthPayments = projectedStores.stream()
                .map(store -> store.getBillingAmountValue() == null ? currentPlans.get(store.getPlan()).getAmount() : store.getBillingAmountValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long fields = ruleRepository.findAll().stream().mapToLong(rule -> fieldRepository.countByRuleId(rule.getId())).sum();
        return new ManagementReport(
                free,
                premium,
                premiumPlus,
                estimatedMrr,
                projectedMonthPayments,
                projectedStores.size(),
                projectedMonth,
                planEventRepository.count(),
                ruleRepository.count(),
                fields
        );
    }
}
