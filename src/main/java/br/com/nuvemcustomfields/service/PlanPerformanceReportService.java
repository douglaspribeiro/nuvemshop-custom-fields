package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.*;
import br.com.nuvemcustomfields.dto.PlanPerformanceReport.*;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlanPerformanceReportService {
    private final StoreRepository stores;
    private final PersonalizationFieldRepository fields;
    private final StoreOrderSalesRepository sales;
    private final StoreSalesSyncRepository syncs;
    public PlanPerformanceReportService(StoreRepository stores, PersonalizationFieldRepository fields,
            StoreOrderSalesRepository sales, StoreSalesSyncRepository syncs) {
        this.stores = stores; this.fields = fields; this.sales = sales; this.syncs = syncs;
    }

    @Transactional(readOnly = true)
    public PlanPerformanceReport report(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) throw new IllegalArgumentException("Período inválido.");
        var active = stores.findAll().stream().filter(Store::isActive).toList();
        var products = fields.countEnabledProductsWithFieldsByStore().stream()
                .collect(Collectors.toMap(StoreMetricCount::storeId, StoreMetricCount::count));
        var zone = ZoneId.of("America/Sao_Paulo");
        var counts = sales.summarizeByStore(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant())
                .stream().collect(Collectors.toMap(StoreSalesCount::storeId, Function.identity()));
        var states = syncs.findAllById(active.stream().map(Store::getStoreId).toList()).stream()
                .collect(Collectors.toMap(StoreSalesSync::getStoreId, Function.identity()));
        var complete = states.values().stream().filter(state -> state.isComplete() && state.getLastError() == null)
                .map(StoreSalesSync::getStoreId).collect(Collectors.toSet());
        var rows = active.stream().map(s -> {
            String issue=syncIssue(s,states.get(s.getStoreId()));
            boolean synced=complete.contains(s.getStoreId()) && issue == null;
            var count = synced ? counts.getOrDefault(s.getStoreId(), new StoreSalesCount(s.getStoreId(), 0, 0))
                    : new StoreSalesCount(s.getStoreId(), 0, 0);
            return new StoreRow(s.getStoreId(), s.getStoreName(), s.getPlan(), products.getOrDefault(s.getStoreId(), 0L),
                    count.orders(), count.items(), synced, issue);
        }).toList();
        var plans = Arrays.stream(PlanType.values()).map(plan -> {
            var group = rows.stream().filter(r -> r.plan() == plan).toList();
            return new PlanRow(plan, group.size(), group.stream().filter(r -> r.products() > 0).count(),
                    group.stream().filter(r -> r.orders() > 0).count(), group.stream().mapToLong(StoreRow::orders).sum(),
                    group.stream().mapToLong(StoreRow::items).sum(), group.stream().filter(StoreRow::synced).count(), group.stream().filter(r -> r.syncIssue() != null).count());
        }).toList();
        var free = rows.stream().filter(r -> !r.plan().isBillable())
                .sorted(Comparator.comparingLong(StoreRow::orders).reversed().thenComparing(StoreRow::id)).toList();
        return new PlanPerformanceReport(from, to, plans, free, rows.stream().filter(r -> r.syncIssue() != null).toList());
    }
    private static String syncIssue(Store store, StoreSalesSync state) {
        if (store.getAccessToken() == null || store.getAccessToken().isBlank()) return "Reconectar loja: token ausente";
        boolean permission = java.util.Arrays.stream(java.util.Objects.toString(store.getScope(), "").split(","))
                .anyMatch(scope -> scope.strip().equals("read_orders"));
        if (!permission) return "Permissão de leitura de pedidos ausente";
        return state == null ? null : state.issueLabel();
    }

}
