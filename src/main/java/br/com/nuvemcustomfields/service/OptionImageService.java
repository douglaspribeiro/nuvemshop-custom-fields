package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.dto.ImageOption;
import br.com.nuvemcustomfields.entity.PersonalizationField;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;

@Service
public class OptionImageService {
    private static final Logger LOG = LoggerFactory.getLogger(OptionImageService.class);
    private final JdbcTemplate jdbc;
    private final OptionImageStorage storage;
    private final OptionImageProcessor processor;
    private final TransactionTemplate isolated;
    private final PersonalizationRuleRepository rules;
    private final PlanLimitService limits;
    private final StoreRepository stores;
    private final PersonalizationFieldRepository fields;
    public OptionImageService(JdbcTemplate jdbc, OptionImageStorage storage, OptionImageProcessor processor,
            PlatformTransactionManager transactions, PersonalizationRuleRepository rules, PlanLimitService limits,
            StoreRepository stores, PersonalizationFieldRepository fields) {
        this.jdbc=jdbc; this.storage=storage; this.processor=processor; this.rules=rules;
        this.limits=limits; this.stores=stores; this.fields=fields;
        isolated=new TransactionTemplate(transactions);
        isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public boolean enabled() { return storage.enabled(); }
    public void lockStore(Long store) {
        jdbc.queryForList("select store_id from stores where store_id=? for update", store);
    }
    public record Uploaded(String id, String thumbnailUrl, String imageUrl) {}
    public Uploaded upload(Long storeId, Long productId, MultipartFile file) {
        if (!enabled()) throw new IllegalArgumentException("image.storage.unavailable");
        var images=processor.process(file);
        String id=UUID.randomUUID().toString(), prefix=storage.prefix()+"/"+storeId+"/"+productId+"/"+id;
        String preview=prefix+"/preview.jpg", thumbnail=prefix+"/thumbnail.jpg", bucket=storage.bucket();
        isolated.executeWithoutResult(tx -> {
            var stores=jdbc.queryForList("select store_id from stores where store_id=? and uninstalled_at is null and erasure_requested_at is null for update", storeId);
            if (stores.isEmpty() || rules.findByStoreIdAndProductId(storeId, productId).isEmpty()) throw new IllegalArgumentException("image.options.invalid");
            var store = this.stores.findActiveByStoreId(storeId).orElseThrow(() -> new IllegalArgumentException("image.options.invalid"));
            limits.requireImageConfiguration(store, productId, 0);
            long count=jdbc.queryForObject("select count(*) from personalization_images where store_id=? and field_id is null and state in ('PENDING','READY')", Long.class, storeId);
            if (count >= 100) throw new IllegalArgumentException("image.upload.limit");
            jdbc.update("insert into personalization_images (id,store_id,product_id,bucket,preview_key,thumbnail_key,state,created_at,updated_at,retry_at) values (?,?,?,?,?,?,'PENDING',?,?,?)",
                    id,storeId,productId,bucket,preview,thumbnail,ts(Instant.now()),ts(Instant.now()),ts(Instant.now().plusSeconds(3600)));
        });
        try {
            storage.put(bucket,preview,images.preview()); storage.put(bucket,thumbnail,images.thumbnail());
            int updated=isolated.execute(tx -> jdbc.update("update personalization_images set state='READY',updated_at=?,retry_at=? where id=? and state='PENDING'",
                    ts(Instant.now()),ts(Instant.now().plusSeconds(86400)),id));
            if (updated != 1) throw new IllegalArgumentException("image.options.invalid");
        } catch (RuntimeException e) {
            isolated.executeWithoutResult(tx -> jdbc.update("update personalization_images set state='DELETING',field_id=null,retry_at=?,updated_at=? where id=?",
                    ts(Instant.now().plusSeconds(300)),ts(Instant.now()),id));
            LOG.warn("images.upload.failed image_id={}",id);
            throw new IllegalArgumentException("image.upload.failed");
        }
        String url="/admin/images/"+id;
        return new Uploaded(id,url+"?size=thumbnail",url);
    }
    /** Joins the field transaction: ownership and deletion intents commit with the configuration. */
    public void bind(PersonalizationField field, List<ImageOption> options) {
        Long store=field.getRule().getStoreId(), product=field.getRule().getProductId();
        for (var option:options) {
            var images=jdbc.queryForList("select field_id from personalization_images where id=? and store_id=? and product_id=? and state='READY' for update", option.id(),store,product);
            if (images.isEmpty()) throw new IllegalArgumentException("image.options.invalid");
            Object current=images.getFirst().get("field_id");
            if (current != null && ((Number)current).longValue()!=field.getId()) throw new IllegalArgumentException("image.options.invalid");
            jdbc.update("update personalization_images set field_id=?,updated_at=? where id=?",field.getId(),ts(Instant.now()),option.id());
        }
        Set<String> keep=new HashSet<>(); options.forEach(o -> keep.add(o.id()));
        for (String id:jdbc.queryForList("select id from personalization_images where store_id=? and product_id=? and field_id=? and state='READY'",String.class,store,product,field.getId()))
            if (!keep.contains(id)) queueId(id);
    }
    public void deleteField(Long store, Long product, Long field) {
        jdbc.update("update personalization_images set state='DELETING',field_id=null,retry_at=?,updated_at=? where store_id=? and product_id=? and field_id=?",
                ts(Instant.now().plusSeconds(300)),ts(Instant.now()),store,product,field);
    }
    public void deleteProduct(Long store, Long product) {
        lockStore(store);
        jdbc.update("update personalization_images set state='DELETING',field_id=null,retry_at=?,updated_at=? where store_id=? and product_id=?",
                ts(Instant.now().plusSeconds(300)),ts(Instant.now()),store,product);
    }
    private void queueId(String id) {
        jdbc.update("update personalization_images set state='DELETING',field_id=null,retry_at=?,updated_at=? where id=?",
                ts(Instant.now().plusSeconds(300)),ts(Instant.now()),id);
    }
    public String readUrl(Long store, String id, boolean thumbnail) {
        if (!enabled()) return null;
        var rows=jdbc.queryForList("select i.* from personalization_images i join stores s on s.store_id=i.store_id join personalization_fields f on f.id=i.field_id join personalization_rules r on r.id=f.rule_id where i.id=? and i.store_id=? and i.state='READY' and s.uninstalled_at is null and s.erasure_requested_at is null and r.enabled=true and r.store_id=i.store_id and r.product_id=i.product_id",id,store);
        if (rows.isEmpty()) return null;
        var row=rows.getFirst();
        // Unbound uploads can be previewed only in the authenticated admin endpoint.
        if (row.get("field_id")==null) return null;
        var storeEntity=stores.findActiveByStoreId(store).orElse(null);
        if (storeEntity==null || !limits.imageProductVisible(storeEntity, ((Number)row.get("product_id")).longValue())) return null;
        var field=fields.findById(((Number)row.get("field_id")).longValue()).orElse(null);
        if (field==null || field.imageOptions().stream().limit(limits.imageOptionLimit(storeEntity.getEffectivePlan())).noneMatch(option -> id.equals(option.id()))) return null;
        return storage.readUrl((String)row.get("bucket"),(String)row.get(thumbnail?"thumbnail_key":"preview_key"));
    }
    public String adminReadUrl(Long store, String id, boolean thumbnail) {
        if (!enabled()) return null;
        var rows=jdbc.queryForList("select bucket,preview_key,thumbnail_key from personalization_images where id=? and store_id=? and state='READY'",id,store);
        if (rows.isEmpty()) return null;
        return storage.readUrl((String)rows.getFirst().get("bucket"),(String)rows.getFirst().get(thumbnail?"thumbnail_key":"preview_key"));
    }
    @Scheduled(fixedDelayString="${images.cleanup-delay-ms:60000}", initialDelayString="${images.cleanup-delay-ms:60000}")
    public void cleanup() {
        if (!enabled()) return;
        // Reconcile detached field rows (including SQL removals) and abandoned uploads.
        jdbc.update("update personalization_images set state='DELETING',field_id=null,retry_at=?,updated_at=? where state in ('PENDING','READY') and ((field_id is null and retry_at<=?) or (field_id is not null and not exists (select 1 from personalization_fields f where f.id=personalization_images.field_id)))",
                ts(Instant.now().plusSeconds(300)),ts(Instant.now()),ts(Instant.now()));
        var rows=jdbc.queryForList("select id,bucket,preview_key,thumbnail_key from personalization_images where state='DELETING' and retry_at<=? order by retry_at limit 50",ts(Instant.now()));
        for(var row:rows) {
            String id=(String)row.get("id");
            try {
                storage.delete((String)row.get("bucket"),(String)row.get("preview_key"));
                storage.delete((String)row.get("bucket"),(String)row.get("thumbnail_key"));
                jdbc.update("delete from personalization_images where id=? and state='DELETING'",id);
                LOG.info("images.cleanup.deleted image_id={}",id);
            } catch(RuntimeException e) {
                jdbc.update("update personalization_images set retry_at=?,last_error=? where id=? and state='DELETING'",ts(Instant.now().plusSeconds(300)),"S3 deletion failed",id);
                LOG.warn("images.cleanup.retry image_id={}",id);
            }
        }
    }
    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
}
