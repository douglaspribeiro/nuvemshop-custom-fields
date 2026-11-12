package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.repository.StoreRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Reconciles references only from the authenticated platform API, never browser payloads. */
@Service
public class BuyerUploadOrders {
    private static final Pattern REFERENCE=Pattern.compile("NCF-FILE:([a-f0-9-]{36})");
    private final NuvemshopApiClient api;
    private final StoreRepository stores;
    private final BuyerUploadLedger ledger;
    private final JdbcTemplate jdbc;
    public BuyerUploadOrders(NuvemshopApiClient api,StoreRepository stores,BuyerUploadLedger ledger,JdbcTemplate jdbc) {
        this.api=api;this.stores=stores;this.ledger=ledger;this.jdbc=jdbc;
    }
    public void order(long storeId,long orderId) {
        var store=stores.findActiveByStoreId(storeId).orElse(null);
        if(store!=null) process(storeId,api.getOrder(store,orderId));
    }
    void process(long storeId,JsonNode order) {
        if(order==null || order.path("id").asLong()<=0) throw new IllegalArgumentException("upload.order.invalid");
        long orderId=order.path("id").asLong();
        String timestamp=order.path("created_at").asText().replaceFirst("([+-]\\d{2})(\\d{2})$","$1:$2");
        Instant created=java.time.OffsetDateTime.parse(timestamp).toInstant();
        for(var item:order.path("products")) {
            long product=item.path("product_id").asLong(),itemId=item.path("id").asLong();
            List<String> values=new ArrayList<>();
            JsonNode properties=item.path("properties");
            if(properties.isObject()) properties.elements().forEachRemaining(value -> values.add(value.asText()));
            else if(properties.isArray()) properties.forEach(value -> values.add(value.path("value").asText()));
            for(String value:values) {
                var match=REFERENCE.matcher(value);
                if(!match.find()) continue;
                String id=match.group(1);
                var fields=jdbc.queryForList("select field_id from buyer_uploads where id=? and store_id=? and product_id=?",Long.class,id,storeId,product);
                if(fields.isEmpty()) continue;
                // Conflicts are preserved for operator investigation; never reassign art.
                ledger.confirmOrder(storeId,id,product,fields.getFirst(),orderId,itemId,created);
            }
        }
    }
    /** Returns false on incomplete synchronization, so cleanup cannot delete a late order's art. */
    public boolean reconcile(long storeId) {
        var store=stores.findActiveByStoreId(storeId).orElse(null);
        if(store==null) return true; // Uninstalled stores retain known ORDERED files only.
        Timestamp earliest=jdbc.queryForObject("select min(created_at) from buyer_uploads where store_id=? and state='CART'",Timestamp.class,storeId);
        if(earliest==null) return true;
        try {
            Instant max=Instant.now();
            for(int page=1;page<=100;page++) {
                var orders=api.listOrdersForSales(store,page,200,earliest.toInstant().minusSeconds(300),max,null,null);
                if(orders==null || !orders.isArray()) return false;
                for(var order:orders) process(storeId,order);
                if(orders.size()<200) return true;
            }
            return false;
        } catch(RuntimeException e) { return false; }
    }
    @Scheduled(fixedDelayString="${buyer-uploads.reconcile-delay-ms:300000}")
    public void reconcilePending() {
        for(Long store:jdbc.queryForList("select distinct store_id from buyer_uploads where state='CART'",Long.class)) reconcile(store);
    }
}
