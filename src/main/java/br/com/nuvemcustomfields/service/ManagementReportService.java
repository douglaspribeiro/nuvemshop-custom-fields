package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.ManagementReport;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ManagementReportService {
    private final StoreRepository stores;
    private final PersonalizationRuleRepository rules;
    private final PersonalizationFieldRepository fields;
    private final PlanEventRepository events;
    private final PaymentSubscriptionRepository subscriptions;
    private final Clock clock;

    @Autowired
    public ManagementReportService(StoreRepository stores, PersonalizationRuleRepository rules,
            PersonalizationFieldRepository fields, PlanEventRepository events,
            PaymentSubscriptionRepository subscriptions) {
        this(stores, rules, fields, events, subscriptions, Clock.system(ZoneId.of("America/Sao_Paulo")));
    }

    ManagementReportService(StoreRepository stores, PersonalizationRuleRepository rules,
            PersonalizationFieldRepository fields, PlanEventRepository events,
            PaymentSubscriptionRepository subscriptions, Clock clock) {
        this.stores = stores; this.rules = rules; this.fields = fields; this.events = events;
        this.subscriptions = subscriptions; this.clock = clock;
    }

    public ManagementReport report() {
        var all = stores.findAll();
        var ids = all.stream().map(Store::getStoreId).filter(Objects::nonNull).toList();
        var byStore = ids.isEmpty() ? Map.<Long, PaymentSubscription>of() : subscriptions.findByStoreIdIn(ids).stream()
                .collect(Collectors.toMap(PaymentSubscription::getStoreId, Function.identity()));
        var mrr = new TreeMap<String, BigDecimal>();
        var projected = new TreeMap<String, BigDecimal>();
        var projectedCounts = new TreeMap<String, Long>();
        LocalDate today = LocalDate.now(clock);
        YearMonth month = YearMonth.from(today);
        for (Store store : all) {
            if (!store.isActive() || store.isCourtesyPremium() || store.isBillingSuspended() || !store.getPlan().isBillable()) continue;
            PaymentSubscription subscription = store.getStoreId() == null ? null : byStore.get(store.getStoreId());
            BigDecimal monthlyAmount;
            BigDecimal nextAmount;
            String currency;
            LocalDate next;
            if (subscription != null) {
                if (subscription.getStatus() != PaymentSubscriptionStatus.ACTIVE || !subscription.isAccessActive()
                        || subscription.getProviderEnvironment() != PaymentEnvironment.PRODUCTION || subscription.isCancellationPending()) continue;
                monthlyAmount = subscription.getWinbackRegularAmount() != null && subscription.isWinbackRestorePending()
                        ? subscription.getWinbackRegularAmount() : subscription.getAmountValue();
                nextAmount = subscription.getAmountValue();
                currency = subscription.getCurrency();
                next = subscription.getNextPaymentAt() == null ? null : subscription.getNextPaymentAt().atZone(clock.getZone()).toLocalDate();
            } else {
                // A locally assigned paid plan is not proof of a recurring contract.
                if (store.getSubscriptionId() == null || store.getSubscriptionId().isBlank()) continue;
                monthlyAmount = store.getBillingAmountValue();
                currency = store.getBillingAmountCurrency();
                // Missing contract prices must not be replaced by today's catalog price.
                if (monthlyAmount == null) continue;
                nextAmount = monthlyAmount;
                next = store.getBillingNextExecution();
            }
            if (currency == null || currency.isBlank() || monthlyAmount == null) continue;
            currency = currency.strip().toUpperCase(Locale.ROOT);
            mrr.merge(currency, monthlyAmount, BigDecimal::add);
            if (next != null && !next.isBefore(today) && YearMonth.from(next).equals(month) && nextAmount != null) {
                projected.merge(currency, nextAmount, BigDecimal::add);
                projectedCounts.merge(currency, 1L, Long::sum);
            }
        }
        long fieldCount = fields.count();
        return new ManagementReport(
                all.stream().filter(s -> !s.getPlan().isBillable()).count(),
                all.stream().filter(s -> s.getPlan() == PlanType.PREMIUM).count(),
                all.stream().filter(s -> s.getPlan() == PlanType.PREMIUM_PLUS).count(),
                all.stream().filter(s -> s.getPlan() == PlanType.PREMIUM_ULTRA).count(),
                mrr.getOrDefault("BRL", BigDecimal.ZERO), events.count(), rules.count(), fieldCount,
                month, Map.copyOf(mrr), Map.copyOf(projected), Map.copyOf(projectedCounts));
    }
}
