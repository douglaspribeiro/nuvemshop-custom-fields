package br.com.nuvemcustomfields.finance;

import br.com.nuvemcustomfields.entity.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static br.com.nuvemcustomfields.finance.FinancialEntry.*;

@Service
public class FinancialAgenda {
    private final FinancialLedger ledger;
    public FinancialAgenda(FinancialLedger ledger){this.ledger=ledger;}
    public record Filter(LocalDate from,LocalDate until,Stage stage,String provider,String account,String currency,String method,String status,PaymentEnvironment environment,String view){
        public Filter {
            if(from==null)from=LocalDate.now().withDayOfMonth(1);if(until==null)until=from.withDayOfMonth(from.lengthOfMonth());
            if(until.isBefore(from) || from.plusDays(366).isBefore(until))throw new IllegalArgumentException("Selecione um período de até 366 dias.");
            if(stage==null)stage=Stage.RELEASE;if(environment==null)environment=PaymentEnvironment.PRODUCTION;
            if(view==null)view="month";if("week".equals(view) && from.plusDays(6).isBefore(until))until=from.plusDays(6);if(!Set.of("month","week","list").contains(view))throw new IllegalArgumentException("Visualização inválida.");
        }
        public boolean matches(FinancialEntry e){return e.stage()==stage && e.environment()==environment && match(provider,e.provider().name())&&match(account,e.account())&&match(currency,e.currency())&&match(method,e.method())&&match(status,e.status());}
        private static boolean match(String wanted,String actual){return wanted==null||wanted.isBlank()||wanted.equalsIgnoreCase(actual);}
    }
    public record Total(String currency,Certainty certainty,String status,BigDecimal gross,BigDecimal fee,BigDecimal net,int unknownGross,int unknownFee,int unknownNet,int count){}
    public record Day(LocalDate date,List<FinancialEntry> entries,List<Total> totals){}
    public record Result(List<FinancialEntry> entries,List<FinancialEntry> undated,List<Total> totals,List<Day> days){}
    public Result query(Filter filter){
        var all=ledger.entries();var selected=all.stream().filter(e->!supersededPlaceholder(e,all)).filter(filter::matches).map(e->project(e,all)).toList();
        var dated=selected.stream().filter(e->e.date()!=null&&!e.date().isBefore(filter.from())&&!e.date().isAfter(filter.until())).toList();
        var undated=selected.stream().filter(e->e.date()==null).toList();var days=new ArrayList<Day>();
        LocalDate from=filter.from(),until=filter.until();
        if("week".equals(filter.view()))until=from.plusDays(6).isAfter(until)?until:from.plusDays(6);
        for(LocalDate date=from;!date.isAfter(until);date=date.plusDays(1)){LocalDate day=date;var entries=dated.stream().filter(e->day.equals(e.date())).toList();days.add(new Day(day,entries,totals(entries)));}
        return new Result(dated,undated,totals(dated),days);
    }
    public record Summary(String provider,String account,String currency,Stage stage,String status,Certainty certainty,BigDecimal net,int unknown,int count){}
    public List<Summary> summaries(Filter filter){
        var all=ledger.entries();Map<String,List<FinancialEntry>> groups=new TreeMap<>();
        for(var e:all){
            if(!Set.of(Stage.RELEASE,Stage.PAYOUT,Stage.BANK).contains(e.stage()) || supersededPlaceholder(e,all))continue;
            var stageFilter=new Filter(filter.from(),filter.until(),e.stage(),filter.provider(),filter.account(),filter.currency(),filter.method(),filter.status(),filter.environment(),filter.view());
            if(!stageFilter.matches(e))continue;
            String key=e.provider()+"/"+e.account()+"/"+e.currency()+"/"+e.stage()+"/"+e.status()+"/"+e.certainty();groups.computeIfAbsent(key,k->new ArrayList<>()).add(project(e,all));
        }
        var result=new ArrayList<Summary>();for(var group:groups.values()){
            FinancialEntry first=group.getFirst();BigDecimal amount=BigDecimal.ZERO;int unknown=0;
            for(var e:group){if(e.net()==null)unknown++;else amount=amount.add(e.net());}
            result.add(new Summary(first.provider().name(),first.account(),first.currency(),first.stage(),first.status(),first.certainty(),unknown==group.size()?null:amount,unknown,group.size()));
        }return result;
    }
    private static boolean supersededPlaceholder(FinancialEntry entry,List<FinancialEntry> all){
        return entry.stage()==Stage.RELEASE && "unreported".equals(entry.status()) && all.stream().anyMatch(other->other.stage()==Stage.RELEASE
                && !other.key().equals(entry.key()) && Objects.equals(entry.parentKey(),other.parentKey()) && !"unreported".equals(other.status()) && !"CALCULATION".equals(other.source()));
    }
    public static FinancialEntry project(FinancialEntry original,List<FinancialEntry> all) {
        if(original.stage()==Stage.ADJUSTMENT || original.stage()==Stage.BANK)return original;
        var related=all.stream().filter(a->a.stage()==Stage.ADJUSTMENT && a.environment()==original.environment()
                && a.provider()==original.provider() && a.account().equals(original.account())
                && (original.key().equals(a.parentKey()) || original.stage()==Stage.RELEASE && original.parentKey()!=null && original.parentKey().equals(a.parentKey())
                    && all.stream().filter(r->r.stage()==Stage.RELEASE && original.parentKey().equals(r.parentKey())).count()==1)).toList();
        FinancialEntry result=original;BigDecimal net=original.net();
        for(var adjustment:related) {
            if("date_override".equals(adjustment.status()) && adjustment.date()!=null && Set.of("MANUAL","REPORT").contains(adjustment.source()))result=original.stage()==Stage.PAYMENT?result.withDocumentedPaymentDate(adjustment.date()):result.withManualDate(adjustment.date());
            else if(original.stage()!=Stage.PAYMENT && Objects.equals(adjustment.currency(),original.currency())) {
                if(adjustment.net()==null || net==null)net=null;else net=net.add(adjustment.net());
            }
        }
        return result.withAdjustedNet(net);
    }
    public static List<Total> totals(List<FinancialEntry> entries){
        Map<String,List<FinancialEntry>> groups=new TreeMap<>();for(var e:entries)groups.computeIfAbsent((e.currency()==null?"Não informada":e.currency())+"/"+e.certainty()+"/"+e.status(),k->new ArrayList<>()).add(e);
        var result=new ArrayList<Total>();for(var group:groups.values()){
            var first=group.getFirst();int ug=0,uf=0,un=0;BigDecimal gross=BigDecimal.ZERO,fee=BigDecimal.ZERO,net=BigDecimal.ZERO;
            for(var e:group){if(e.gross()==null)ug++;else gross=gross.add(e.gross());if(e.fee()==null)uf++;else fee=fee.add(e.fee());if(e.net()==null)un++;else net=net.add(e.net());}
            result.add(new Total(first.currency(),first.certainty(),first.status(),ug==group.size()?null:gross,uf==group.size()?null:fee,un==group.size()?null:net,ug,uf,un,group.size()));
        }return result;
    }
    private static String cell(Object value){
        String text=value==null?"":value.toString(),trimmed=text.stripLeading();
        if(value instanceof String && !trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0))>=0)text="'"+text;
        return "\""+text.replace("\"","\"\"")+"\"";
    }

    public String csv(Filter filter){var query=query(filter);var rows=new ArrayList<>(query.entries());rows.addAll(query.undated());StringBuilder out=new StringBuilder("key,provider,account,environment,stage,resourceId,installment,parentKey,storeId,subscriptionId,country,method,currency,gross,fee,tax,net,date,paidDate,paidAt,status,certainty,source,evidence,observedAt,effectiveAt,ruleId,capturedAt,postedDate,exchangeRate,dateCertainty,amountCertainty\r\n");
        for(var e:rows){Object[] fields={e.key(),e.provider(),e.account(),e.environment(),e.stage(),e.resourceId(),e.installment(),e.parentKey(),e.storeId(),e.subscriptionId(),e.country(),e.method(),e.currency(),e.gross(),e.fee(),e.tax(),e.net(),e.date(),e.paidDate(),e.paidAt(),e.status(),e.certainty(),e.source(),e.evidence(),e.observedAt(),e.effectiveAt(),e.ruleId(),e.capturedAt(),e.postedDate(),e.exchangeRate(),e.dateCertainty(),e.amountCertainty()};
            out.append(Arrays.stream(fields).map(FinancialAgenda::cell).collect(java.util.stream.Collectors.joining(","))).append("\r\n");}return out.toString();}
}
