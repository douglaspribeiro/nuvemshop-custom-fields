package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.ManagementReport;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.PlanEventRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ManagementReportService {

    private static final BigDecimal PREMIUM_PRICE = new BigDecimal("9.99");
    private static final BigDecimal PREMIUM_PLUS_PRICE = new BigDecimal("19.99");

    private final StoreRepository storeRepository;
    private final PersonalizationRuleRepository ruleRepository;
    private final PersonalizationFieldRepository fieldRepository;
    private final PlanEventRepository planEventRepository;

    public ManagementReportService(
            StoreRepository storeRepository,
            PersonalizationRuleRepository ruleRepository,
            PersonalizationFieldRepository fieldRepository,
            PlanEventRepository planEventRepository
    ) {
        this.storeRepository = storeRepository;
        this.ruleRepository = ruleRepository;
        this.fieldRepository = fieldRepository;
        this.planEventRepository = planEventRepository;
    }

    public ManagementReport report() {
        var stores = storeRepository.findAll();
        long free = stores.stream().filter(store -> store.getPlan() == PlanType.FREE).count();
        long premium = stores.stream().filter(store -> store.getPlan() == PlanType.PREMIUM).count();
        long premiumPlus = stores.stream().filter(store -> store.getPlan() == PlanType.PREMIUM_PLUS).count();
        long billablePremium = stores.stream().filter(store -> store.getPlan() == PlanType.PREMIUM && !store.isCourtesyPremium()).count();
        long billablePremiumPlus = stores.stream().filter(store -> store.getPlan() == PlanType.PREMIUM_PLUS && !store.isCourtesyPremium()).count();
        long fields = ruleRepository.findAll().stream().mapToLong(rule -> fieldRepository.countByRuleId(rule.getId())).sum();
        return new ManagementReport(
                free,
                premium,
                premiumPlus,
                PREMIUM_PRICE.multiply(BigDecimal.valueOf(billablePremium)).add(PREMIUM_PLUS_PRICE.multiply(BigDecimal.valueOf(billablePremiumPlus))),
                planEventRepository.count(),
                ruleRepository.count(),
                fields
        );
    }
}
