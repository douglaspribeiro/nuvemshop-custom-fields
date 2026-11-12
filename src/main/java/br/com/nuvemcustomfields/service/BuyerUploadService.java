package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.FieldType;
import br.com.nuvemcustomfields.entity.BuyerStorageProvider;
import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.multipart.MultipartFile;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class BuyerUploadService {
    @org.springframework.beans.factory.annotation.Autowired
    private BuyerUploadOrders orders;
    @org.springframework.beans.factory.annotation.Autowired
    private BuyerUploadMetrics uploadMetrics;
    public record Uploaded(String id, String name, long size, String reference) {}
    public record Download(String name, long size, java.io.InputStream stream) {}
    private final JdbcTemplate jdbc;
    private final BuyerUploadLedger ledger;
    private final BuyerFileStorage storage;
    private final BuyerFileValidator validator;
    private final PersonalizationFieldRepository fields;
    private final StoreRepository stores;
    private final PlanLimitService limits;
    private final TransactionTemplate tx;
    private final MeterRegistry metrics;
    private static final SecureRandom RANDOM=new SecureRandom();
    private final java.util.concurrent.Semaphore uploadSlots=new java.util.concurrent.Semaphore(2);
    private final com.github.benmanes.caffeine.cache.Cache<Long,java.util.concurrent.atomic.AtomicLong> downloadTraffic=
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(10000)
                    .expireAfterWrite(java.time.Duration.ofMinutes(5)).build();
    public BuyerUploadService(JdbcTemplate jdbc, BuyerUploadLedger ledger, BuyerFileStorage storage,
            BuyerFileValidator validator, PersonalizationFieldRepository fields, StoreRepository stores,
            PlanLimitService limits, PlatformTransactionManager transactions, MeterRegistry metrics) {
        this.jdbc=jdbc;this.ledger=ledger;this.storage=storage;this.validator=validator;this.fields=fields;
        this.stores=stores;this.limits=limits;this.tx=new TransactionTemplate(transactions);this.metrics=metrics;
        metrics.gauge("ncf.buyer.cleanup.pending",jdbc,j -> {
            try{return j.queryForObject("select count(*) from buyer_uploads where state='DELETING'",Double.class);}
            catch(RuntimeException e){return Double.NaN;}
        });
    }
    public static Set<String> formats(String value) {
        if(value==null || value.isBlank()) return Set.of("JPEG","PNG","PDF");
        var result=new TreeSet<String>();
        for(String part:value.toUpperCase(Locale.ROOT).split("[,\\s]+")) {
            String format=part.equals("JPG")?"JPEG":part;
            if(!Set.of("JPEG","PNG","PDF").contains(format)) throw new BuyerUploadLedger.Rejected("upload.formats.invalid");
            result.add(format);
        }
        if(result.isEmpty()) throw new BuyerUploadLedger.Rejected("upload.formats.invalid");
        return result;
    }
    public boolean enabled() { return storage.enabled(); }
    public boolean enabled(long storeId) { return storage.forStore(storeId).enabled(); }
    public String session(long storeId) {
        return tx.execute(status -> {
            lock(storeId);
            if(!enabled(storeId) || !ledger.usage(storeId).acceptsUploads()) throw rejected("upload.unavailable");
            Instant now=Instant.now();
            Long count=jdbc.queryForObject("select count(*) from buyer_upload_sessions where store_id=? and created_at>?",
                    Long.class,storeId,ts(now.minusSeconds(3600)));
            if(count!=null && count>=1000) throw rejected("upload.rate.limit");
            byte[] random=new byte[32];RANDOM.nextBytes(random);
            String token=HexFormat.of().formatHex(random);
            jdbc.update("insert into buyer_upload_sessions(session_hash,store_id,created_at,expires_at) values (?,?,?,?)",
                    hash(token),storeId,ts(now),ts(now.plusSeconds(86400)));
            return token;
        });
    }
    private void authenticate(long storeId,String token) {
        if(token==null || !token.matches("[a-f0-9]{64}")) throw rejected("upload.session.invalid");
        Long count=jdbc.queryForObject("select count(*) from buyer_upload_sessions where store_id=? and session_hash=? and expires_at>?",
                Long.class,storeId,hash(token),ts(Instant.now()));
        if(count==null || count!=1) throw rejected("upload.session.invalid");
        jdbc.update("update buyer_upload_sessions set expires_at=? where store_id=? and session_hash=?",
                ts(Instant.now().plusSeconds(86400)),storeId,hash(token));
    }
    public Uploaded upload(long storeId,long productId,long fieldId,String token,String requestId,MultipartFile file) {
        if(!uploadSlots.tryAcquire())throw rejected("upload.rate.limit");
        try{return doUpload(storeId,productId,fieldId,token,requestId,file);}
        finally{uploadSlots.release();}
    }
    private Uploaded doUpload(long storeId,long productId,long fieldId,String token,String requestId,MultipartFile file) {
        authenticate(storeId,token);
        var destination = storage.forStore(storeId);
        if(!destination.enabled()) throw rejected("upload.unavailable");
        Set<String> allowed=tx.execute(status -> {
        var store=stores.findActiveByStoreId(storeId).orElseThrow(() -> rejected("upload.unavailable"));
        var field=fields.findById(fieldId).orElseThrow(() -> rejected("upload.field.invalid"));
        if(field.getRule().getStoreId()!=storeId || field.getRule().getProductId()!=productId
                || !field.getRule().isEnabled() || field.getFieldType()!=FieldType.FILE_UPLOAD
                || limits.storefrontFields(store,field.getRule().getFields()).stream().noneMatch(f -> f.getId().equals(fieldId)))
            throw rejected("upload.field.invalid");
        return formats(field.getOptionsText());
        });
        // Commit reservation before reading content or starting any remote object write.
        var entry=ledger.reserve(storeId,productId,fieldId,token,requestId,file.getSize());
        if(entry.state()!=BuyerUploadLedger.State.RESERVED) {
            String name=jdbc.queryForObject("select original_name from buyer_uploads where id=?",String.class,entry.id());
            return new Uploaded(entry.id(),name,entry.sizeBytes(),"NCF-FILE:"+entry.id());
        }
        boolean[] claimed={false};
        try {
            byte[] bytes;
            try(var input=file.getInputStream()) { bytes=input.readNBytes((int)BuyerUploadPolicy.MAX_FILE_BYTES+1); }
            if(bytes.length!=entry.sizeBytes()) throw rejected("upload.size.mismatch");
            var checked=validator.validate(bytes,file.getOriginalFilename(),allowed);
            String bucket=destination.bucket(),key=destination.key(storeId,entry.id());
            var provider=destination.provider();
            // Durable location precedes the object write, including timeout/partial failures.
            tx.executeWithoutResult(status -> {
                lock(storeId);
                int updated=jdbc.update("update buyer_uploads set storage_provider=?,bucket=?,object_key=?,original_name=?,content_type=? where id=? and state='RESERVED' and bucket is null",
                        provider.name(),bucket,key,checked.name(),checked.type(),entry.id());
                if(updated!=1) throw rejected("upload.not.ready");
                claimed[0]=true;
            });
            tx.executeWithoutResult(status -> {
                lock(storeId);
                var state=jdbc.queryForObject("select state from buyer_uploads where id=? for update",String.class,entry.id());
                if(!"RESERVED".equals(state)) throw rejected("upload.not.ready");
                destination.put(bucket,key,bytes,checked.type());
                ledger.stored(storeId,entry.id(),token,bytes.length);
            });
            uploadMetrics.uploaded(destination.provider(),bytes.length);
            metrics.counter("ncf.buyer.uploads","outcome","success").increment();
            return new Uploaded(entry.id(),checked.name(),bytes.length,"NCF-FILE:"+entry.id());
        } catch(Exception e) {
            tx.executeWithoutResult(status -> { lock(storeId); jdbc.update("update buyer_uploads set state='DELETING',delete_reason='FAILED' where id=? and state='RESERVED'"
                    +(claimed[0]?"":" and bucket is null"),entry.id()); });
            uploadMetrics.uploadFailed(destination.provider());
            metrics.counter("ncf.buyer.uploads","outcome","failed").increment();
            if(e instanceof BuyerUploadLedger.Rejected rejection) throw rejection;
            throw rejected("upload.failed");
        }
    }
    public void cart(long storeId,String token,String id) { authenticate(storeId,token);ledger.attachCart(storeId,id,token); }
    public void validate(long storeId,String token,String id) { authenticate(storeId,token);ledger.validateTemporary(storeId,id,token); }
    public void remove(long storeId,String token,String id) { authenticate(storeId,token);ledger.removeTemporary(storeId,id,token); }
    public record FilePage(List<Map<String,Object>> files, int page, int pages, long totalOrders, long pendingBytes) {}
    public FilePage orders(long storeId,int page,String sort,Instant before) {
        if (page<1 || !Set.of("oldest","newest","largest").contains(sort)) throw rejected("upload.selection.invalid");
        String filter="store_id=? and order_id is not null and state in ('ORDERED','DELETING')";
        List<Object> args=new ArrayList<>();args.add(storeId);
        if(before!=null){filter+=" and order_created_at<=?";args.add(ts(before));}
        long count=jdbc.queryForObject("select count(distinct order_id) from buyer_uploads where "+filter,Long.class,args.toArray());
        int pages=Math.max(1,(int)((count+19)/20));page=Math.min(page,pages);
        String order=switch(sort){case "largest" -> "sum(size_bytes) desc, order_id";case "newest" -> "min(order_created_at) desc, order_id";default -> "min(order_created_at), order_id";};
        var queryArgs=new ArrayList<>(args);queryArgs.add((page-1)*20);
        var orderIds=jdbc.queryForList("select order_id from buyer_uploads where "+filter+" group by order_id order by "+order+" limit 20 offset ?",Long.class,queryArgs.toArray());
        List<Map<String,Object>> result=List.of();
        if(!orderIds.isEmpty()) {
            var fileArgs=new ArrayList<Object>();fileArgs.add(storeId);fileArgs.addAll(orderIds);
            result=jdbc.queryForList("select id,original_name,size_bytes,state,order_id,order_item_id,order_created_at,expires_at from buyer_uploads "
                    +"where store_id=? and state in ('ORDERED','DELETING') and order_id in ("+String.join(",",Collections.nCopies(orderIds.size(),"?"))+") order by order_item_id,id",fileArgs.toArray());
            var positions=new HashMap<Long,Integer>();for(int i=0;i<orderIds.size();i++)positions.put(orderIds.get(i),i);
            result.sort(Comparator.comparingInt(row -> positions.get(((Number)row.get("order_id")).longValue())));
        }
        long pending=jdbc.queryForObject("select coalesce(sum(size_bytes),0) from buyer_uploads where store_id=? and state='DELETING'",Long.class,storeId);
        return new FilePage(result,page,pages,count,pending);
    }
    public record ArchiveFile(String id,long orderId,long itemId,String name,long size,String bucket,String key,BuyerStorageProvider provider) {}
    public record Archive(long storeId,List<ArchiveFile> files,long bytes) {}
    public Archive archive(long storeId,List<String> ids) {
        return tx.execute(status -> {
            lock(storeId);
            var selected=BuyerUploadLedger.selection(ids);
            List<ArchiveFile> result=new ArrayList<>();long bytes=0;
            for(String id:selected){
                var rows=jdbc.queryForList("select * from buyer_uploads where id=? and store_id=? and order_id is not null and state='ORDERED' and expires_at>? for update",id,storeId,ts(Instant.now()));
                if(rows.isEmpty())throw rejected("upload.not.found");
                var row=rows.getFirst();long size=((Number)row.get("size_bytes")).longValue();bytes+=size;
                result.add(new ArchiveFile(id,((Number)row.get("order_id")).longValue(),((Number)row.get("order_item_id")).longValue(),
                        (String)row.get("original_name"),size,(String)row.get("bucket"),(String)row.get("object_key"),BuyerStorageProvider.valueOf((String)row.get("storage_provider"))));
            }
            reserveDownloadTraffic(storeId,bytes);
            return new Archive(storeId,List.copyOf(result),bytes);
        });
    }
    private void reserveDownloadTraffic(long storeId,long bytes) {
        var traffic=downloadTraffic.get(storeId,key->new java.util.concurrent.atomic.AtomicLong());
        for(;;){long used=traffic.get();if(bytes>1_000_000_000L-used)throw rejected("upload.rate.limit");if(traffic.compareAndSet(used,used+bytes))return;}
    }
    public Download download(long storeId,String id) {
        var archive=archive(storeId,List.of(id));var file=archive.files().getFirst();
        java.io.InputStream stream;
        try {stream=uploadMetrics.download(file.provider(),file.size(),storage.forProvider(file.provider()).read(file.bucket(),file.key()));}
        catch(RuntimeException e) {uploadMetrics.downloadFailed(file.provider());throw e;}
        metrics.counter("ncf.buyer.downloads").increment();
        return new Download(file.name(),file.size(),stream);
    }
    @Scheduled(fixedDelayString="${buyer-uploads.cleanup-delay-ms:3600000}")
    public void cleanup() {
        var due=jdbc.queryForList("select store_id,id,state from buyer_uploads where (retry_at is null or retry_at<=?) and (state='DELETING' "
                + "or (state in ('RESERVED','TEMPORARY','CART','ORDERED') and expires_at<=?)) order by expires_at limit 100",ts(Instant.now()),ts(Instant.now()));
        for(var item:due) {
            long storeId=((Number)item.get("store_id")).longValue();String id=(String)item.get("id");
            try {
                if("CART".equals(item.get("state")) && !orders.reconcile(storeId)) {
                    jdbc.update("update buyer_uploads set retry_at=? where id=? and state='CART'",ts(Instant.now().plusSeconds(300)),id);
                    metrics.counter("ncf.buyer.cleanup","outcome","deferred").increment();
                    continue;
                }
                ledger.expire(storeId,id,Instant.now());
                boolean[] deleted={false};
                tx.executeWithoutResult(status -> {
                    jdbc.queryForList("select store_id from stores where store_id=? for update",storeId);
                    var rows=jdbc.queryForList("select bucket,object_key,storage_provider,delete_reason from buyer_uploads where id=? and state='DELETING' for update",id);
                    if(rows.isEmpty()) return;
                    var row=rows.getFirst();
                    if(row.get("bucket")!=null) storage.forProvider(BuyerStorageProvider.valueOf((String)row.get("storage_provider"))).delete((String)row.get("bucket"),(String)row.get("object_key"));
                    ledger.acknowledgeDeletion(storeId,id);
                    if("ERASED".equals(row.get("delete_reason"))) jdbc.update("delete from buyer_uploads where id=?",id);
                    else jdbc.update("update buyer_uploads set bucket=null,object_key=null,original_name=null,session_hash='',request_id=id where id=?",id);
                    deleted[0]=true;
                });
                if(deleted[0])metrics.counter("ncf.buyer.cleanup","outcome","success").increment();
            } catch(RuntimeException e) {
                jdbc.update("update buyer_uploads set retry_at=?,delete_attempts=delete_attempts+1 where id=?",ts(Instant.now().plusSeconds(300)),id);
                metrics.counter("ncf.buyer.cleanup","outcome","failed").increment();
            }
        }
        jdbc.update("delete from buyer_upload_sessions where expires_at<?",ts(Instant.now()));
    }
    private void lock(long storeId) {
        if(jdbc.queryForList("select store_id from stores where store_id=? for update",storeId).isEmpty()) throw rejected("upload.unavailable");
    }
    static String hash(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private static BuyerUploadLedger.Rejected rejected(String code) {return new BuyerUploadLedger.Rejected(code);}
    private static Timestamp ts(Instant value) {return Timestamp.from(value);}
}
