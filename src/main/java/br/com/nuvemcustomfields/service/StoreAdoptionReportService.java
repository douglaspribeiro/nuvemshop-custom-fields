package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.StoreAdoptionReport;
import br.com.nuvemcustomfields.dto.StoreAdoptionReport.*;
import br.com.nuvemcustomfields.dto.StoreMetricCount;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StoreAdoptionReportService {
    private static final ZoneId ZONE=ZoneId.of("America/Sao_Paulo");
    private final StoreRepository stores;
    private final PersonalizationFieldRepository fields;
    private final PaymentSubscriptionRepository subscriptions;
    private final StoreOrderSalesRepository sales;
    private final StoreSalesSyncRepository syncs;
    private final WinbackCampaignRepository campaigns;
    private final StoreDepartureService departures;
    private final IntegrationLogRepository logs;

    public StoreAdoptionReportService(StoreRepository stores,PersonalizationFieldRepository fields,
            PaymentSubscriptionRepository subscriptions,StoreOrderSalesRepository sales,
            StoreSalesSyncRepository syncs,WinbackCampaignRepository campaigns,StoreDepartureService departures,
            IntegrationLogRepository logs) {
        this.stores=stores;this.fields=fields;this.subscriptions=subscriptions;this.sales=sales;
        this.syncs=syncs;this.campaigns=campaigns;this.departures=departures;this.logs=logs;
    }

    @Transactional(readOnly=true)
    public StoreAdoptionReport report(LocalDate from,LocalDate to,String query,String status) {
        if(from!=null && to!=null && to.isBefore(from)) throw new IllegalArgumentException("A data final deve ser igual ou posterior à inicial.");
        String term=query==null?"":query.strip().toLowerCase(Locale.ROOT);
        var base=stores.findAll().stream().filter(s -> matches(s,from,to,term)).toList();
        var ids=base.stream().map(Store::getStoreId).toList();
        var bySubscription=ids.isEmpty()?Map.<Long,PaymentSubscription>of():subscriptions.findByStoreIdIn(ids).stream()
                .collect(Collectors.toMap(PaymentSubscription::getStoreId,Function.identity()));
        var fieldCounts=counts(fields.countFieldsByStore());
        var productCounts=counts(fields.countProductsWithFieldsByStore());
        var enabledCounts=counts(fields.countEnabledProductsWithFieldsByStore());
        var orderCounts=counts(sales.countPersonalizedOrdersByStore());
        var renderCounts=counts(logs.countStorefrontRenderReportsByStore());
        var complete=syncs.findAllById(ids).stream().filter(StoreSalesSync::isComplete)
                .map(StoreSalesSync::getStoreId).collect(Collectors.toSet());
        var feedback=ids.isEmpty()?List.<WinbackCampaign>of():campaigns.findByStoreIdIn(ids);
        var feedbackByStore=feedback.stream().collect(Collectors.groupingBy(WinbackCampaign::getStoreId));
        var rows=base.stream().map(store -> row(store,bySubscription.get(store.getStoreId()),fieldCounts,productCounts,
                enabledCounts,orderCounts,renderCounts,complete,feedbackByStore.getOrDefault(store.getStoreId(),List.of())))
                .sorted(Comparator.comparing((Row r)->r.store().getInstalledAt(),Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(r->r.store().getStoreId())).toList();
        long active=rows.stream().filter(r->r.store().isActive()).count();
        long paid=rows.stream().filter(Row::paidActive).count();
        long configured=rows.stream().filter(r->r.store().isActive() && r.enabledProducts()>0).count();
        long paidConfigured=rows.stream().filter(r->r.paidActive() && r.enabledProducts()>0).count();
        var stages=List.of("Sem campos cadastrados","Campos sem produto habilitado","Produto configurado","Exibição reportada pela vitrine","Venda personalizada registrada").stream()
                .map(stage->new Stage(stage,rows.stream().filter(r->r.store().isActive() && r.stage().equals(stage)).count(),
                        rows.stream().filter(r->!r.store().isActive() && r.stage().equals(stage)).count())).toList();
        var reasons=rows.stream().filter(r->!r.store().isActive())
                .collect(Collectors.groupingBy(r->new ReasonKey(r.reason().strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT),r.reasonSource())))
                .values().stream().map(group->new Reason(group.getFirst().reason(),group.getFirst().reasonSource(),group.size()))
                .sorted(Comparator.comparingLong(Reason::stores).reversed().thenComparing(Reason::label)).toList();
        var durations=new ArrayList<DurationBucket>();
        String[] labels={"Menos de 1 dia","1 a 7 dias","8 a 30 dias","31 a 90 dias","Mais de 90 dias","Data desconhecida ou inconsistente"};
        for(int index=0;index<labels.length;index++) {
            int bucket=index;
            durations.add(new DurationBucket(labels[index],rows.stream().filter(r->r.store().getUninstalledAt()!=null)
                    .filter(r->durationBucket(r.daysToDeparture())==bucket).count()));
        }
        var cohorts=rows.stream().filter(r->r.store().getInstalledAt()!=null)
                .collect(Collectors.groupingBy(r->YearMonth.from(r.store().getInstalledAt().atZone(ZONE)))).entrySet().stream()
                .sorted(Map.Entry.<YearMonth,List<Row>>comparingByKey().reversed()).map(entry->{
                    var group=entry.getValue();long paidCount=group.stream().filter(Row::paidActive).count();
                    return new Cohort(entry.getKey(),group.size(),group.stream().filter(r->r.store().isActive()).count(),
                            group.stream().filter(r->r.store().getUninstalledAt()!=null).count(),
                            group.stream().filter(r->r.enabledProducts()>0).count(),paidCount,rate(paidCount,group.size()));
                }).toList();
        var displayed=rows.stream().filter(r->switch(String.valueOf(status)) {
            case "active" -> r.store().isActive();
            case "uninstalled" -> r.store().getUninstalledAt()!=null;
            case "erasure" -> r.store().isErasurePending();
            default -> true;
        }).toList();
        return new StoreAdoptionReport(rows.size(),active,rows.stream().filter(r->r.store().getUninstalledAt()!=null).count(),
                rows.stream().filter(r->r.store().isErasurePending()).count(),paid,configured,paidConfigured,
                rate(paid,active),rate(paid,rows.size()),rate(paidConfigured,configured),stages,reasons,List.copyOf(durations),
                cohorts,displayed,departures.summary());
    }

    private Row row(Store store,PaymentSubscription sub,Map<Long,Long> fieldCounts,Map<Long,Long> products,
            Map<Long,Long> enabled,Map<Long,Long> orders,Map<Long,Long> renders,Set<Long> complete,List<WinbackCampaign> feedback) {
        Long id=store.getStoreId();long fieldCount=fieldCounts.getOrDefault(id,0L),enabledCount=enabled.getOrDefault(id,0L);
        long orderCount=orders.getOrDefault(id,0L);
        long renderCount=renders.getOrDefault(id,0L);
        boolean paid=store.isActive() && !store.isCourtesyPremium() && !store.isBillingSuspended()
                && sub!=null && sub.getProviderEnvironment()==PaymentEnvironment.PRODUCTION
                && sub.getStatus()==PaymentSubscriptionStatus.ACTIVE && sub.isAccessActive()
                && !sub.isCancellationPending() && sub.getPlan()!=null && sub.getPlan().isBillable();
        String subscription=sub==null?"Sem assinatura externa registrada":sub.getPlan().getDisplayName()+" · "+sub.getStatus()
                +(sub.getProviderEnvironment()==PaymentEnvironment.SANDBOX?" · Teste":"");
        var response=feedback.stream().filter(c->Objects.equals(c.getUninstalledAt(),store.getUninstalledAt()))
                .filter(c->c.getReason()!=null && !c.getReason().isBlank()).findFirst().orElse(null);
        String reason="Ainda não informado",source="Sem motivo",justification=null;
        if(store.getDepartureReason()!=null && !store.getDepartureReason().isBlank()) {
            reason=store.getDepartureReason().strip();source="Nuvemshop · registro manual";justification=store.getDepartureJustification();
        } else if(response!=null) {reason=response.getReasonLabel();source="Resposta na reconquista";justification=response.getResponse();}
        Instant departure=store.getUninstalledAt()!=null?store.getUninstalledAt():store.getErasureRequestedAt();
        Long days=departure!=null && store.getInstalledAt()!=null && !departure.isBefore(store.getInstalledAt())
                ? ChronoUnit.DAYS.between(store.getInstalledAt(),departure):null;
        String stage=orderCount>0?"Venda personalizada registrada":renderCount>0?"Exibição reportada pela vitrine":enabledCount>0?"Produto configurado":
                fieldCount>0?"Campos sem produto habilitado":"Sem campos cadastrados";
        return new Row(store,fieldCount,products.getOrDefault(id,0L),enabledCount,orderCount,complete.contains(id),renderCount,paid,
                subscription,reason,source,justification,departure,days,stage);
    }
    private boolean matches(Store store,LocalDate from,LocalDate to,String query) {
        Instant at=store.getInstalledAt();
        if(from!=null && (at==null || at.isBefore(from.atStartOfDay(ZONE).toInstant()))) return false;
        if(to!=null && (at==null || !at.isBefore(to.plusDays(1).atStartOfDay(ZONE).toInstant()))) return false;
        return query.isEmpty() || String.valueOf(store.getStoreId()).contains(query)
                || (store.getStoreName()!=null && store.getStoreName().toLowerCase(Locale.ROOT).contains(query));
    }
    private static Map<Long,Long> counts(List<StoreMetricCount> values) {
        return values.stream().collect(Collectors.toMap(StoreMetricCount::storeId,StoreMetricCount::count));
    }
    static BigDecimal rate(long numerator,long denominator) {
        return denominator==0?null:BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator),1,RoundingMode.HALF_UP);
    }
    private static int durationBucket(Long days) {
        return days==null?5:days==0?0:days<=7?1:days<=30?2:days<=90?3:4;
    }
    private record ReasonKey(String reason,String source) { }
}
