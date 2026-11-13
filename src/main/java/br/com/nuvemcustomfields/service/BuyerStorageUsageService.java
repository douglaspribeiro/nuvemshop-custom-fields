package br.com.nuvemcustomfields.service;

import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuyerStorageUsageService {
    private final JdbcTemplate jdbc;
    private final BuyerUploadLedger ledger;
    public BuyerStorageUsageService(JdbcTemplate jdbc,BuyerUploadLedger ledger){this.jdbc=jdbc;this.ledger=ledger;}
    public record StateUsage(String state,long files,long bytes) {
        public String label(){return switch(state){
            case "RESERVED" -> "Uploads em andamento (reservado)";
            case "TEMPORARY" -> "Temporários";
            case "CART" -> "No carrinho";
            case "ORDERED" -> "Vinculados a pedidos";
            case "DELETING" -> "Aguardando exclusão";
            default -> state;
        };}
        public String formattedBytes(){return format(bytes);}
    }
    public record Summary(long occupiedBytes,long quotaBytes,long availableBytes,long files,long pendingBytes,List<StateUsage> states) {
        public boolean hasQuota(){return quotaBytes>0;}
        public double percent(){return hasQuota()?occupiedBytes*100.0/quotaBytes:0;}
        public double progressPercent(){return Math.min(100,percent());}
        public String formattedPercent(){return String.format(Locale.forLanguageTag("pt-BR"),"%.1f%%",percent());}
        public String occupied(){return format(occupiedBytes);}
        public String quota(){return format(quotaBytes);}
        public String available(){return format(availableBytes);}
        public String pending(){return format(pendingBytes);}
    }
    @Transactional(readOnly=true)
    public Summary summary(long storeId){
        var usage=ledger.usage(storeId);
        var states=jdbc.query("select state,count(*) as files,coalesce(sum(size_bytes),0) as bytes from buyer_uploads "
                +"where store_id=? and state not in ('DELETED','EXPIRED') group by state order by state",
                (row,index)->new StateUsage(row.getString("state"),row.getLong("files"),row.getLong("bytes")),storeId);
        return new Summary(usage.occupiedBytes(),usage.quotaBytes(),Math.max(0,usage.quotaBytes()-usage.occupiedBytes()),
                states.stream().mapToLong(StateUsage::files).sum(),states.stream().filter(s->s.state().equals("DELETING")).mapToLong(StateUsage::bytes).sum(),states);
    }
    private static String format(long bytes){
        String[] units={"B","KB","MB","GB","TB"};double amount=bytes;int unit=0;
        while(amount>=1000 && unit<units.length-1){amount/=1000;unit++;}
        return String.format(Locale.forLanguageTag("pt-BR"),unit==0?"%.0f %s":"%.2f %s",amount,units[unit]);
    }
}
