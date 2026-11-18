package br.com.nuvemcustomfields.finance;

import br.com.nuvemcustomfields.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static br.com.nuvemcustomfields.finance.FinancialEntry.*;

@Service
public class FinancialImport {
    private final FinancialLedger ledger;
    public FinancialImport(FinancialLedger ledger){this.ledger=ledger;}
    public static final String HEADER="provider,account,environment,stage,resourceId,installment,parentKey,storeId,subscriptionId,country,method,currency,gross,fee,tax,net,date,paidDate,paidAt,status,certainty,evidence,effectiveAt";
    public static final String EXTENDED_HEADER=HEADER+",capturedAt,postedDate,exchangeRate";
    public List<FinancialEntry> preview(String csv,String evidence){
        if(csv==null || csv.length()>2_000_000 || evidence==null||evidence.isBlank())throw new IllegalArgumentException("Informe relatório de até 2 MB e sua origem.");
        var rows=parse(csv);if(rows.isEmpty() || !Set.of(HEADER,EXTENDED_HEADER).contains(String.join(",",rows.getFirst())))throw new IllegalArgumentException("Cabeçalho inválido. Use o modelo CSV.");
        if(rows.size()>1001)throw new IllegalArgumentException("Importe até 1.000 lançamentos por vez.");
        var result=new ArrayList<FinancialEntry>();var keys=new HashSet<String>();
        for(int i=1;i<rows.size();i++){var c=rows.get(i);if(c.size()!=rows.getFirst().size())throw new IllegalArgumentException("Número de colunas inválido na linha "+(i+1));
            try{
                var e=new FinancialEntry(null,PaymentProviderType.valueOf(c.get(0)),c.get(1),PaymentEnvironment.valueOf(c.get(2)),c.get(4),c.get(5),Stage.valueOf(c.get(3)),nil(c.get(6)),c.get(7).isBlank()?null:Long.valueOf(c.get(7)),nil(c.get(8)),nil(c.get(9)),nil(c.get(10)),nil(c.get(11)),money(c.get(12)),money(c.get(13)),money(c.get(14)),money(c.get(15)),day(c.get(16)),day(c.get(17)),time(c.get(18)),c.get(19),Certainty.valueOf(c.get(20)),"REPORT",evidence+"; "+c.get(21),Instant.now(),time(c.get(22)),null);
                if(c.size()==26)e=e.withFinancialDates(time(c.get(23)),day(c.get(24)),money(c.get(25)));
                if(e.parentKey()!=null && e.parentKey().contains(":"))e=new FinancialEntry(e.key(),e.provider(),e.account(),e.environment(),e.resourceId(),e.installment(),e.stage(),FinancialLedger.hash(e.provider()+":"+e.account()+":"+e.environment()+":"+e.parentKey()),e.storeId(),e.subscriptionId(),e.country(),e.method(),e.currency(),e.gross(),e.fee(),e.tax(),e.net(),e.date(),e.paidDate(),e.paidAt(),e.status(),e.certainty(),e.source(),e.evidence(),e.observedAt(),e.effectiveAt(),e.ruleId(),e.capturedAt(),e.postedDate(),e.exchangeRate(),e.dateCertainty(),e.amountCertainty());
                FinancialLedger.validate(e);if(!keys.add(e.identity()))throw new IllegalArgumentException("Identidade duplicada no relatório.");result.add(e);
            }catch(RuntimeException ex){throw new IllegalArgumentException("Linha "+(i+1)+": "+ex.getMessage(),ex);}
        }return List.copyOf(result);
    }
    @Transactional public int commit(List<FinancialEntry> entries,String operator){for(var e:entries.stream().sorted(Comparator.comparing(FinancialEntry::stage)).toList())ledger.record(e,operator);return entries.size();}
    private static String nil(String s){return s.isBlank()?null:s;}
    private static BigDecimal money(String s){return s.isBlank()?null:new BigDecimal(s);}
    private static LocalDate day(String s){return s.isBlank()?null:LocalDate.parse(s);}
    private static Instant time(String s){return s.isBlank()?null:Instant.parse(s);}
    /** Strict RFC 4180 parser, bounded by the upload limit; embedded line breaks are preserved. */
    public static List<List<String>> parse(String input){
        var rows=new ArrayList<List<String>>();var row=new ArrayList<String>();var cell=new StringBuilder();boolean quoted=false,closed=false;
        if(input.startsWith("\uFEFF"))input=input.substring(1);
        for(int i=0;i<input.length();i++){char c=input.charAt(i);
            if(quoted){if(c=='"'){if(i+1<input.length()&&input.charAt(i+1)=='"'){cell.append('"');i++;}else{quoted=false;closed=true;}}else cell.append(c);continue;}
            if(c=='"'){if(cell.length()!=0||closed)throw new IllegalArgumentException("Aspas inválidas no CSV.");quoted=true;continue;}
            if(c==','||c=='\n'||c=='\r'){row.add(cell.toString());cell.setLength(0);closed=false;if(c!=','){if(c=='\r'&&i+1<input.length()&&input.charAt(i+1)=='\n')i++;rows.add(List.copyOf(row));row.clear();}continue;}
            if(closed)throw new IllegalArgumentException("Texto após fechamento de aspas.");cell.append(c);
        }
        if(quoted)throw new IllegalArgumentException("Aspas não fechadas no CSV.");
        if(!row.isEmpty()||cell.length()>0||closed){row.add(cell.toString());rows.add(List.copyOf(row));}return rows;
    }
}
