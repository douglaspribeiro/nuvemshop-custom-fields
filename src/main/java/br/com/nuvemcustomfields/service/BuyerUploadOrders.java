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
        if(store!=null) {
            var order=api.getOrder(store,orderId);
            if(order==null || order.path("id").asLong()!=orderId) throw new IllegalArgumentException("upload.order.invalid");
            process(storeId,order);
        }
    }
    /** Refresh at most the 20 visible orders; also throttle failed API attempts. */
    public void refreshVisible(long storeId,List<Long> orderIds) {
        var now=Timestamp.from(Instant.now());
        var stale=Timestamp.from(Instant.now().minusSeconds(300));
        for(Long orderId:orderIds) {
            int claimed=jdbc.update("update buyer_uploads set metadata_checked_at=? where store_id=? and order_id=? and (metadata_checked_at is null or metadata_checked_at<?)",now,storeId,orderId,stale);
            if(claimed==0) continue;
            try { order(storeId,orderId); }
            catch(RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(BuyerUploadOrders.class).warn("buyer_upload.order_refresh.failed store_id={} order_id={} error={}",storeId,orderId,e.getClass().getSimpleName());
            }
        }
    }
    void process(long storeId,JsonNode order) {
        if(order==null || order.path("id").asLong()<=0) throw new IllegalArgumentException("upload.order.invalid");
        long orderId=order.path("id").asLong();
        String timestamp=order.path("created_at").asText().replaceFirst("([+-]\\d{2})(\\d{2})$","$1:$2");
        Instant created=java.time.OffsetDateTime.parse(timestamp).toInstant();
        for(var item:order.path("products")) {
            long product=item.path("product_id").asLong(),itemId=item.path("id").asLong();
            for(String id:fileIds(storeId,item)) {
                var fields=jdbc.queryForList("select field_id from buyer_uploads where id=? and store_id=? and product_id=?",Long.class,id,storeId,product);
                if(fields.isEmpty()) continue;
                ledger.confirmOrder(storeId,id,product,fields.getFirst(),orderId,itemId,created);
            }
            String image=item.path("image").path("src").asText("").strip();
            if(image.startsWith("//")) image="https:"+image;
            if(!image.startsWith("https://") && !image.startsWith("http://")) image="";
            jdbc.update("update buyer_uploads set product_name=?,product_image_url=? where store_id=? and order_id=? and order_item_id=? and product_id=?",
                    text(item,"name",512),image.length()<=1024?image:"",storeId,orderId,itemId,product);
        }
        jdbc.update("update buyer_uploads set order_number=?,order_status=?,payment_status=?,shipping_status=?,metadata_checked_at=? where store_id=? and order_id=?",
                text(order,"number",64),text(order,"status",40),text(order,"payment_status",40),text(order,"shipping_status",40),Timestamp.from(Instant.now()),storeId,orderId);
    }
    private static String text(JsonNode node,String key,int max) {
        String value=node.path(key).asText("").strip();
        return value.substring(0,Math.min(value.length(),max));
    }
    /** Only authenticated platform order data may be passed here. Never guess by name or time. */
    public List<String> fileIds(long storeId,JsonNode item) {
        var ids=new java.util.LinkedHashSet<String>();
        var properties=item.path("properties");
        List<String> values=new ArrayList<>();
        if(properties.isObject())properties.elements().forEachRemaining(v->values.add(v.asText()));
        else if(properties.isArray())properties.forEach(v->values.add(v.path("value").asText()));
        for(String value:values) {
            var full=REFERENCE.matcher(value);
            while(full.find()) ids.addAll(jdbc.queryForList("select id from buyer_uploads where id=? and store_id=? and product_id=?",String.class,full.group(1),storeId,item.path("product_id").asLong()));
            // Older 500-character properties can end mid-URL. Recover only a unique,
            // sufficiently long UUID prefix belonging to this exact store/product.
            var partial=Pattern.compile("/buyer-uploads/([a-f0-9-]{24,36})(?:/download|$)").matcher(value);
            while(partial.find()) {
                var candidates=jdbc.queryForList("select id from buyer_uploads where store_id=? and product_id=? and id like ?",String.class,storeId,item.path("product_id").asLong(),partial.group(1)+"%");
                if(candidates.size()==1)ids.add(candidates.getFirst());
            }
        }
        return List.copyOf(ids);
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
