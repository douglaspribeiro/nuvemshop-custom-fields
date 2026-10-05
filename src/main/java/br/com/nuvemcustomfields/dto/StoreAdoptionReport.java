package br.com.nuvemcustomfields.dto;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.service.StoreDepartureService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

public record StoreAdoptionReport(
        long total, long active, long uninstalled, long erasurePending, long paidActive,
        long configuredActive, long paidConfiguredActive, BigDecimal paidActiveRate, BigDecimal paidBaseRate,
        BigDecimal configuredPaidRate, List<Stage> stages, List<Reason> reasons,
        List<DurationBucket> durations, List<Cohort> cohorts, List<Row> rows,
        StoreDepartureService.Summary historicalDepartures) {
    public record Stage(String label, long active, long departed) { }
    public record Reason(String label, String source, long stores) { }
    public record DurationBucket(String label, long stores) { }
    public record Cohort(YearMonth month, long stores, long active, long uninstalled, long configured,
                         long paidActive, BigDecimal paidRate) { }
    public record Row(Store store, long fields, long products, long enabledProducts, long personalizedOrders,
                      boolean salesComplete, long renderReports, boolean paidActive, String subscriptionLabel,
                      String reason, String reasonSource, String justification, Instant departureAt,
                      Long daysToDeparture, String stage) {
        public String getStatusLabel() {
            if (store.isErasurePending()) return "Exclusão pendente";
            return store.isActive() ? "Ativa" : "Desinstalada";
        }
        public String getSalesLabel() {
            if (personalizedOrders > 0) return personalizedOrders + " pedido(s) com personalização";
            return salesComplete ? "Nenhuma venda personalizada no histórico sincronizado" : "Histórico de vendas incompleto";
        }
    }
}
