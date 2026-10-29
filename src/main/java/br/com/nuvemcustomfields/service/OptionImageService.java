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
        return uploadInternal(storeId,productId,file,false);
    }
    public Uploaded uploadForTemplate(Long storeId,MultipartFile file) {return uploadInternal(storeId,0L,file,true);}
    private Uploaded uploadInternal(Long storeId,Long productId,MultipartFile file,boolean template) {
        if (!enabled()) throw new IllegalArgumentException("image.storage.unavailable");
        var images=processor.process(file);
        String id=UUID.randomUUID().toString(), prefix=storage.prefix()+"/"+storeId+"/"+productId+"/"+id;
        String preview=prefix+"/preview.jpg", thumbnail=prefix+"/thumbnail.jpg", bucket=storage.bucket();
        isolated.executeWithoutResult(tx -> {
            var stores=jdbc.queryForList("select store_id from stores where store_id=? and uninstalled_at is null and erasure_requested_at is null for update", storeId);
            if (stores.isEmpty() || !template && rules.findByStoreIdAndProductId(storeId, productId).isEmpty()) throw new IllegalArgumentException("image.options.invalid");
            var store = this.stores.findActiveByStoreId(storeId).orElseThrow(() -> new IllegalArgumentException("image.options.invalid"));
            if(template) {
                if(limits.imageOptionLimit(store.getEffectivePlan())<=0 || limits.imageProductLimit(store.getEffectivePlan())==0)
                    throw new ImagePlanLimitException("image.plan.unavailable");
            }else limits.requireImageConfiguration(store, productId, 0);
            long count=jdbc.queryForObject("select count(*) from personalization_images where store_id=? and field_id is null and state in ('PENDING','READY') and not exists (select 1 from personalization_image_references x where x.image_id=personalization_images.id)", Long.class, storeId);
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
    /** Joins the owner's transaction. Image content is immutable; owners have independent option definitions. */
    public void bind(PersonalizationField field, List<ImageOption> options) { bind(field,options,false); }
    public void bindShared(PersonalizationField field,List<ImageOption> options) { bind(field,options,true); }
    private void bind(PersonalizationField field,List<ImageOption> options,boolean shared) {
        Long store=field.getRule().getStoreId(), product=field.getRule().getProductId();
        Set<String> previous=new HashSet<>(jdbc.queryForList("select image_id from personalization_image_references where store_id=? and owner_type='FIELD' and owner_id=?",String.class,store,field.getId()));
        for(var option:options) {
            var rows=jdbc.queryForList("select field_id,product_id from personalization_images where id=? and store_id=? and state='READY' for update",option.id(),store);
            if(rows.isEmpty())throw new IllegalArgumentException("image.options.invalid");
            var row=rows.getFirst();Object current=row.get("field_id");
            if(!shared && !previous.contains(option.id()) && (((Number)row.get("product_id")).longValue()!=product
                    || current!=null && ((Number)current).longValue()!=field.getId()))throw new IllegalArgumentException("image.options.invalid");
            reference(store,option.id(),"FIELD",field.getId(),product);
            if(current==null && ((Number)row.get("product_id")).longValue()==product)
                jdbc.update("update personalization_images set field_id=?,updated_at=? where id=?",field.getId(),ts(Instant.now()),option.id());
        }
        Set<String> keep=new HashSet<>();options.forEach(o->keep.add(o.id()));
        for(String id:previous)if(!keep.contains(id)) {
            jdbc.update("delete from personalization_image_references where image_id=? and owner_type='FIELD' and owner_id=?",id,field.getId());
            jdbc.update("update personalization_images set field_id=null where id=? and field_id=?",id,field.getId());
            queueIfUnused(id);
        }
    }
    public void validateShared(Long store,List<ImageOption> options) {
        for(var option:options)if(jdbc.queryForList("select id from personalization_images where store_id=? and id=? and state='READY' for update",store,option.id()).isEmpty())
            throw new IllegalArgumentException("image.options.invalid");
    }
    public void pinTemplate(Long store,Long template,List<ImageOption> options) {
        validateShared(store,options);
        var previous=jdbc.queryForList("select image_id from personalization_image_references where store_id=? and owner_type='TEMPLATE' and owner_id=?",String.class,store,template);
        jdbc.update("delete from personalization_image_references where store_id=? and owner_type='TEMPLATE' and owner_id=?",store,template);
        for(var option:options)reference(store,option.id(),"TEMPLATE",template,0L);
        previous.forEach(this::queueIfUnused);
    }
    public void unpinTemplate(Long store,Long template) { pinTemplate(store,template,List.of()); }
    private void reference(Long store,String image,String type,Long owner,Long product) {
        if(jdbc.queryForList("select id from personalization_image_references where image_id=? and owner_type=? and owner_id=?",image,type,owner).isEmpty())
            jdbc.update("insert into personalization_image_references(image_id,store_id,owner_type,owner_id,product_id) values (?,?,?,?,?)",image,store,type,owner,product);
    }
    public void deleteField(Long store,Long product,Long field) {
        var ids=jdbc.queryForList("select image_id from personalization_image_references where store_id=? and owner_type='FIELD' and owner_id=?",String.class,store,field);
        jdbc.update("delete from personalization_image_references where store_id=? and owner_type='FIELD' and owner_id=?",store,field);
        jdbc.update("update personalization_images set field_id=null where store_id=? and field_id=?",store,field);
        ids.forEach(this::queueIfUnused);
    }
    public void deleteProduct(Long store,Long product) {
        lockStore(store);
        Set<String> ids=new HashSet<>(jdbc.queryForList("select image_id from personalization_image_references where store_id=? and owner_type='FIELD' and product_id=?",String.class,store,product));
        ids.addAll(jdbc.queryForList("select id from personalization_images where store_id=? and product_id=?",String.class,store,product));
        jdbc.update("delete from personalization_image_references where store_id=? and owner_type='FIELD' and product_id=?",store,product);
        jdbc.update("update personalization_images set field_id=null where store_id=? and product_id=?",store,product);
        ids.forEach(this::queueIfUnused);
    }
    private void queueIfUnused(String id) {
        jdbc.update("update personalization_images set state='DELETING',field_id=null,retry_at=?,updated_at=? where id=? "
                +"and not exists (select 1 from personalization_image_references x where x.image_id=personalization_images.id)",ts(Instant.now().plusSeconds(300)),ts(Instant.now()),id);
    }
    public String readUrl(Long store,String id,boolean thumbnail) {
        if(!enabled())return null;
        var rows=jdbc.queryForList("select i.bucket,i.preview_key,i.thumbnail_key,x.owner_id as reference_field_id,x.product_id as reference_product_id "
                +"from personalization_images i join personalization_image_references x on x.image_id=i.id and x.owner_type='FIELD' and x.store_id=i.store_id "
                +"join personalization_fields f on f.id=x.owner_id join personalization_rules r on r.id=f.rule_id "
                +"join stores s on s.store_id=i.store_id where i.id=? and i.store_id=? and i.state='READY' and r.store_id=i.store_id "
                +"and r.product_id=x.product_id and r.enabled=true and s.uninstalled_at is null and s.erasure_requested_at is null",id,store);
        var storeEntity=stores.findActiveByStoreId(store).orElse(null);
        if(storeEntity==null)return null;
        for(var row:rows) {
            if(!limits.imageProductVisible(storeEntity,((Number)row.get("reference_product_id")).longValue()))continue;
            var field=fields.findById(((Number)row.get("reference_field_id")).longValue()).orElse(null);
            if(field!=null && field.imageOptions().stream().limit(limits.imageOptionLimit(storeEntity.getEffectivePlan())).anyMatch(o->id.equals(o.id())))
                return storage.readUrl((String)row.get("bucket"),(String)row.get(thumbnail?"thumbnail_key":"preview_key"));
        }
        return null;
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
        // Reconcile detached owners before collecting assets. Template pins survive product removal.
        jdbc.update("delete from personalization_image_references where (owner_type='FIELD' and not exists (select 1 from personalization_fields f where f.id=owner_id)) or (owner_type='TEMPLATE' and not exists (select 1 from store_templates t where t.id=owner_id))");
        jdbc.update("update personalization_images set state='DELETING',field_id=null,retry_at=?,updated_at=? where state in ('PENDING','READY') and not exists (select 1 from personalization_image_references x where x.image_id=personalization_images.id) and ((field_id is null and retry_at<=?) or (field_id is not null and not exists (select 1 from personalization_fields f where f.id=personalization_images.field_id)))",
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
