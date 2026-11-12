package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.*;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import java.nio.charset.StandardCharsets;

@Service
public class TemplateBatchService {
 public enum Mode { APPEND, REPLACE, UPDATE }
 public record Product(long id,String name,String brand,Map<String,String> categories,boolean configured,String sourceKey,Long sourceRevision) {}
 public record Item(long productId,String name,String reviewToken,List<TemplateFieldDefinition> before,List<TemplateFieldDefinition> after,
                    List<TemplateFieldDefinition> managed,boolean modified,String state,String error,int attempts) {}
 public record Batch(String id,String key,long revision,Mode mode,List<Item> items) {}
 public record Summary(String id,String key,Mode mode,long succeeded,int total) {}
 private final StoreTemplateService templates;private final StoreRepository stores;private final PersonalizationRuleRepository rules;
 private final NuvemshopApiClient api;private final JdbcTemplate db;private final ObjectMapper json;private final TransactionTemplate tx;
 private final PlanLimitService limits;
 private record Catalog(Instant expires,List<JsonNode> products) {}
 private final Map<Long,Catalog> catalogs=new java.util.concurrent.ConcurrentHashMap<>();
 public TemplateBatchService(StoreTemplateService templates,StoreRepository stores,PersonalizationRuleRepository rules,NuvemshopApiClient api,
        JdbcTemplate db,ObjectMapper json,PlatformTransactionManager manager,PlanLimitService limits) {
  this.templates=templates;this.stores=stores;this.rules=rules;this.api=api;this.db=db;this.json=json;tx=new TransactionTemplate(manager);
  tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  this.limits=limits;
 }
 public List<Product> products(long store) {
  var owner=owner(store);Catalog cached=catalogs.get(store);
  if(cached==null || cached.expires().isBefore(Instant.now())) {
   var products=new ArrayList<JsonNode>();boolean complete=false;
   for(int page=1;page<=51;page++) {
    var result=api.listTemplateBatchProducts(owner,page);
    if(result==null || !result.isArray())throw error("batch.catalog.failed");
    if(page>50 && !result.isEmpty())throw error("batch.catalog.limit");
    result.forEach(products::add);if(result.size()<100){complete=true;break;}
   }
   if(!complete)throw error("batch.catalog.limit");
   if(catalogs.size()>100)catalogs.clear();
   cached=new Catalog(Instant.now().plusSeconds(300),List.copyOf(products));catalogs.put(store,cached);
  }
  var configured=rules.findWithFieldsByStoreIdOrderByCreatedAtAscIdAsc(store).stream().filter(r->!r.getFields().isEmpty()).map(PersonalizationRule::getProductId).collect(java.util.stream.Collectors.toSet());
  var sources=new HashMap<Long,Map<String,Object>>();db.queryForList("select product_id,source_key,source_revision from template_product_sources where store_id=?",store)
    .forEach(row->sources.put(((Number)row.get("product_id")).longValue(),row));
  var result=new ArrayList<Product>();
  for(var p:cached.products()) {
   var categories=new LinkedHashMap<String,String>();for(var c:p.path("categories"))categories.put(c.path("id").asText(),name(c.path("name"),c.path("id").asText()));
   long id=p.path("id").asLong();var source=sources.get(id);
   result.add(new Product(id,name(p.path("name"),"#"+id),p.path("brand").asText(""),categories,configured.contains(id),source==null?null:(String)source.get("source_key"),source==null?null:((Number)source.get("source_revision")).longValue()));
  }
  return result;
 }
 public Batch review(long store,String key,Mode mode,List<Long> products) {
  if(mode==null || products==null || products.isEmpty() || products.size()>200 || products.stream().anyMatch(id->id==null||id<=0) || new HashSet<>(products).size()!=products.size())throw error("batch.selection.invalid");
  var owner=owner(store);var source=templates.get(store,key);var items=new ArrayList<Item>();
  long projectedProducts=rules.countByStoreId(store),productLimit=limits.productLimit(owner.getEffectivePlan());
  var projectedImages=new HashSet<>(limits.imageProductIds(store));long imageLimit=limits.imageProductLimit(owner.getEffectivePlan());
  for(long id:products) {
   Item candidate=new Item(id,"#"+id,null,List.of(),List.of(),List.of(),false,"BLOCKED",null,0);
   try {
    var product=api.getProduct(owner,id);var preview=templates.preview(store,key,id);
    if(preview.template().revision()!=source.revision() || !preview.template().fields().equals(source.fields()))throw error("template.stale");
    List<TemplateFieldDefinition> before=preview.existing(),after,managed=source.fields();boolean modified=false;
    if(mode==Mode.UPDATE) {
     var previous=db.queryForList("select * from template_product_sources where store_id=? and product_id=?",store,id);
     if(previous.isEmpty() || !key.equals(previous.getFirst().get("source_key")))throw error("batch.source.missing");
     var row=previous.getFirst();modified=!write(before).equals(row.get("configuration_json"));
     var old=readFields((String)row.get("template_json"));var oldLabels=old.stream().map(f->label(f.label())).collect(java.util.stream.Collectors.toSet());
     after=new ArrayList<>(before.stream().filter(f->!oldLabels.contains(label(f.label()))).toList());after.addAll(source.fields());
    } else if(mode==Mode.APPEND) {
     var existing=before.stream().map(f->label(f.label())).collect(java.util.stream.Collectors.toSet());
     managed=source.fields().stream().filter(f->!existing.contains(label(f.label()))).toList();
     after=new ArrayList<>(before);after.addAll(managed);
    } else after=source.fields();
    candidate=new Item(id,product.name(),preview.reviewToken(),List.copyOf(before),List.copyOf(after),List.copyOf(managed),modified,"READY",null,0);
    templates.validateBatchResult(store,id,after);
    boolean newProduct=rules.findByStoreIdAndProductId(store,id).isEmpty();
    boolean hasImages=after.stream().anyMatch(f->f.fieldType()==FieldType.IMAGE_SELECT);
    if(newProduct && productLimit!=-1 && projectedProducts>=productLimit)throw error("template.products.limit");
    if(hasImages && !projectedImages.contains(id) && imageLimit!=-1 && projectedImages.size()>=imageLimit)throw error("template.images.limit");
    if(newProduct)projectedProducts++;
    if(hasImages)projectedImages.add(id);else projectedImages.remove(id);
    items.add(candidate);
   } catch(RuntimeException ex) {items.add(replace(candidate,"BLOCKED",code(ex),0));}
  }
  var batch=new Batch(UUID.randomUUID().toString(),key,source.revision(),mode,List.copyOf(items));
  String document=write(batch);
  if(document.getBytes(StandardCharsets.UTF_8).length>8_000_000)throw error("batch.review.too.large");
  db.update("insert into template_batches(id,store_id,source_key,apply_mode,total_products,succeeded_products,document_json,created_at) values(?,?,?,?,?,0,?,?)",batch.id(),store,key,mode.name(),items.size(),document,Timestamp.from(Instant.now()));
  return batch;
 }
 public Batch get(long store,String id) {owner(store);return load(store,id,false);}
 public List<Summary> recent(long store) {
  owner(store);return db.query("select id,source_key,apply_mode,succeeded_products,total_products from template_batches where store_id=? order by created_at desc limit 20",
    (row,n)->new Summary(row.getString(1),row.getString(2),Mode.valueOf(row.getString(3)),row.getLong(4),row.getInt(5)),store);
 }
 public Batch execute(long store,String id,long product,boolean confirmed,boolean allowModified) {
  if(!confirmed)throw error("template.confirm");
  try {return tx.execute(s->applyItem(store,id,product,allowModified));}
  catch(RuntimeException ex) {
   String code=code(ex);
   return tx.execute(s->{lock(store);var batch=load(store,id,true);var item=find(batch,product);
    if(item.state().equals("SUCCEEDED"))return batch;
    if(item.state().equals("BLOCKED"))return batch;
    return save(store,batch,replace(item,"FAILED",code,item.attempts()+1));});
  }
 }
 private Batch applyItem(long store,String id,long product,boolean allowModified) {
  var owner=lock(store);var batch=load(store,id,true);var item=find(batch,product);
  if(item.state().equals("SUCCEEDED"))return batch;
  if(item.state().equals("BLOCKED"))throw error(item.error());
  if(item.modified() && !allowModified)throw error("batch.modified.confirm");
  var current=templates.preview(store,batch.key(),product);
  if(current.template().revision()!=batch.revision() || !Objects.equals(current.reviewToken(),item.reviewToken()))throw error("template.stale");
  var remote=api.getProduct(owner,product);
  templates.validateBatchResult(store,product,item.after());
  String request=UUID.nameUUIDFromBytes((id+":"+product).getBytes(StandardCharsets.UTF_8)).toString();
  if(batch.mode()!=Mode.APPEND || !item.managed().isEmpty())
   templates.applyReviewed(store,batch.key(),product,remote.name(),batch.mode()==Mode.APPEND?StoreTemplateService.Mode.APPEND:StoreTemplateService.Mode.REPLACE,true,request,item.reviewToken(),batch.mode()==Mode.APPEND?item.managed():item.after());
  var actual=templates.preview(store,batch.key(),product).existing();
  var previous=db.queryForList("select source_key from template_product_sources where store_id=? and product_id=?",String.class,store,product);
  // Preserve ownership of fields from previous applications of the same template.
  List<TemplateFieldDefinition> managed=item.managed();
  if(batch.mode()==Mode.APPEND && !previous.isEmpty() && batch.key().equals(previous.getFirst())) {
   var old=readFields(db.queryForObject("select template_json from template_product_sources where store_id=? and product_id=?",String.class,store,product));
   var combined=new ArrayList<>(old);var labels=old.stream().map(f->label(f.label())).collect(java.util.stream.Collectors.toSet());
   item.managed().stream().filter(f->!labels.contains(label(f.label()))).forEach(combined::add);managed=combined;
  }
  if(previous.isEmpty())db.update("insert into template_product_sources(store_id,product_id,source_key,source_revision,template_json,configuration_json,updated_at) values(?,?,?,?,?,?,?)",store,product,batch.key(),batch.revision(),write(managed),write(actual),Timestamp.from(Instant.now()));
  else db.update("update template_product_sources set source_key=?,source_revision=?,template_json=?,configuration_json=?,updated_at=? where store_id=? and product_id=?",batch.key(),batch.revision(),write(managed),write(actual),Timestamp.from(Instant.now()),store,product);
  return save(store,batch,replace(item,"SUCCEEDED",null,item.attempts()+1));
 }
 private Batch save(long store,Batch batch,Item item) {
  var result=new Batch(batch.id(),batch.key(),batch.revision(),batch.mode(),batch.items().stream().map(i->i.productId()==item.productId()?item:i).toList());
  db.update("update template_batches set document_json=?,succeeded_products=? where id=? and store_id=?",write(result),result.items().stream().filter(i->i.state().equals("SUCCEEDED")).count(),batch.id(),store);return result;
 }
 private Item replace(Item i,String state,String error,int attempts){return new Item(i.productId(),i.name(),i.reviewToken(),i.before(),i.after(),i.managed(),i.modified(),state,error,attempts);}
 private Item find(Batch b,long product){return b.items().stream().filter(i->i.productId()==product).findFirst().orElseThrow(()->error("batch.selection.invalid"));}
 private Batch load(long store,String id,boolean lock) {
  var rows=db.queryForList("select document_json from template_batches where id=? and store_id=?"+(lock?" for update":""),String.class,id,store);
  if(rows.isEmpty())throw error("batch.not.found");return readBatch(rows.getFirst());
 }
 private Store owner(long id){return stores.findActiveByStoreId(id).orElseThrow(()->error("batch.not.found"));}
 private Store lock(long id){return stores.findActiveByStoreIdForUpdate(id).orElseThrow(()->error("batch.not.found"));}
 private String name(JsonNode n,String fallback){if(n.isTextual())return n.asText();for(String lang:List.of("pt","es","en"))if(n.hasNonNull(lang))return n.path(lang).asText();return fallback;}
 private String label(String value){return OrderPersonalization.propertyName(value).toLowerCase(Locale.ROOT);}
 private String write(Object o){try{return json.writeValueAsString(o);}catch(Exception ex){throw error("template.fields.invalid");}}
 private List<TemplateFieldDefinition> readFields(String value){try{return json.readValue(value,new TypeReference<List<TemplateFieldDefinition>>(){});}catch(Exception e){throw error("template.fields.invalid");}}
 private Batch readBatch(String value){try{return json.readValue(value,Batch.class);}catch(Exception e){throw error("batch.not.found");}}
 private String code(RuntimeException ex){return ex instanceof StoreTemplateService.Invalid?ex.getMessage():"batch.operation.failed";}
 private static StoreTemplateService.Invalid error(String code){return new StoreTemplateService.Invalid(code);}
}
