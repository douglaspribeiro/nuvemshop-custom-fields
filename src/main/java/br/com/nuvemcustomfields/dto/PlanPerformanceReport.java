package br.com.nuvemcustomfields.dto;

import br.com.nuvemcustomfields.entity.PlanType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public record PlanPerformanceReport(LocalDate from, LocalDate to, List<PlanRow> plans, List<StoreRow> freeDetails, List<StoreRow> syncIssues) {
    public long activeStores() { return plans.stream().mapToLong(PlanRow::stores).sum(); }
    public long sellingStores() { return plans.stream().mapToLong(PlanRow::selling).sum(); }
    public long freeStores() { return plans.stream().filter(p -> !p.plan().isBillable()).mapToLong(PlanRow::stores).sum(); }
    public long freeConfigured() { return plans.stream().filter(p -> !p.plan().isBillable()).mapToLong(PlanRow::configured).sum(); }
    public long freeSelling() { return plans.stream().filter(p -> !p.plan().isBillable()).mapToLong(PlanRow::selling).sum(); }
    public long freeNotConfigured() { return freeStores() - freeConfigured(); }
    public BigDecimal freeActivationRate() { return rate(freeConfigured(), freeStores()); }
    public long syncedStores() { return plans.stream().mapToLong(PlanRow::synced).sum(); }
    public long freeSyncedStores() { return plans.stream().filter(p -> !p.plan().isBillable()).mapToLong(PlanRow::synced).sum(); }
    public String freeSellingDisplay() { return salesDisplay(freeSelling(), freeSyncedStores() == freeStores()); }
    public BigDecimal freeSellingRate() { return freeSyncedStores() == freeStores() ? rate(freeSelling(), freeStores()) : null; }

    public record PlanRow(PlanType plan, long stores, long configured, long selling, long orders, long items, long synced, long blocked) {
        public BigDecimal activationRate() { return rate(configured, stores); }
        public boolean salesComplete() { return synced == stores; }
        public String sellingDisplay() { return salesDisplay(selling, salesComplete()); }
        public String ordersDisplay() { return salesDisplay(orders, salesComplete()); }
        public String itemsDisplay() { return salesDisplay(items, salesComplete()); }
        public BigDecimal sellingRate() { return salesComplete() ? rate(selling, stores) : null; }
    }
    public record StoreRow(Long id, String name, PlanType plan, long products, long orders, long items, boolean synced, String syncIssue) {
        public String ordersDisplay() { return salesDisplay(orders, synced); }
        public String itemsDisplay() { return salesDisplay(items, synced); }
        public String salesStatus() {
            if (syncIssue != null) return syncIssue;
            if (orders > 0) return synced ? "Com pedidos" : "Com pedidos · histórico incompleto";
            return synced ? "Sem pedidos no período" : "Histórico incompleto";
        }
    }
    private static String salesDisplay(long value, boolean complete) {
        return complete ? Long.toString(value) : value > 0 ? value + " (parcial)" : "—";
    }
    private static BigDecimal rate(long value, long total) {
        return total == 0 ? null : BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }
}
