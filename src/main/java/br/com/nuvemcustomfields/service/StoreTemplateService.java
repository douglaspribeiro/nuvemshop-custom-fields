package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.*;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class StoreTemplateService {
    public enum Mode { APPEND, REPLACE }
    public static class Invalid extends IllegalArgumentException {public Invalid(String code){super(code);}}
    public record Model(String key,Long id,String name,String description,String origin,long revision,List<TemplateFieldDefinition> fields) {}
    public record Preview(Model template,Long productId,List<TemplateFieldDefinition> existing,String reviewToken) {}
    private final StoreTemplateRepository templates;
    private final StoreRepository stores;
    private final PersonalizationRuleRepository rules;
    private final PersonalizationFieldRepository fields;
    private final PersonalizationAdminService admin;
    private final OptionImageService images;
    private final NicheTemplateService catalog;
    private final PlanLimitService limits;
    private final BuyerUploadService uploads;
    private final BuyerUploadLedger uploadRights;
    private final OrderPersonalizationCatalog history;
    private final StorefrontCache cache;
    private final ObjectMapper json;
    private final Validator validator;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    public StoreTemplateService(StoreTemplateRepository templates,StoreRepository stores,PersonalizationRuleRepository rules,
            PersonalizationFieldRepository fields,PersonalizationAdminService admin,OptionImageService images,NicheTemplateService catalog,
            PlanLimitService limits,BuyerUploadService uploads,BuyerUploadLedger uploadRights,OrderPersonalizationCatalog history,
            StorefrontCache cache,ObjectMapper json,Validator validator,JdbcTemplate jdbc,EntityManager em) {
        this.templates=templates;this.stores=stores;this.rules=rules;this.fields=fields;this.admin=admin;this.images=images;
        this.catalog=catalog;this.limits=limits;this.uploads=uploads;this.uploadRights=uploadRights;this.history=history;
        this.cache=cache;this.json=json;this.validator=validator;this.jdbc=jdbc;this.em=em;
    }
    @Transactional(readOnly=true)
    public List<Model> list(long store) {
        active(store);
        return templates.findByStoreIdOrderByUpdatedAtDescIdDesc(store).stream().map(this::model).toList();
    }
    @Transactional(readOnly=true)
    public List<Model> initial() {
        return catalog.listTemplates().stream().map(t->new Model("catalog:"+t.id(),null,t.name(),t.description(),"CATALOG",0,
                java.util.stream.IntStream.range(0,t.fields().size()).mapToObj(i->TemplateFieldDefinition.from(t.fields().get(i).toForm(i))).toList())).toList();
    }
    @Transactional(readOnly=true)
    public Model get(long store,String key) {active(store);return source(store,key);}
    @Transactional
    public Model create(long store,String name,String description,List<TemplateFieldDefinition> definitions) {
        var owner=lock(store);return createLocked(owner,name,description,definitions,"CUSTOM");
    }
    private Model createLocked(Store owner,String name,String description,List<TemplateFieldDefinition> definitions,String origin) {
        var content=validate(definitions);features(owner,content,null);
        validateImages(owner.getStoreId(),content);
        var template=new StoreTemplate();template.storeId=owner.getStoreId();template.name=name(name);template.description=description(description);
        template.origin=origin;template.fieldsJson=write(content);template.createdAt=Instant.now();template.updatedAt=template.createdAt;
        templates.saveAndFlush(template);images.pinTemplate(owner.getStoreId(),template.id,imageOptions(content));return model(template);
    }
    @Transactional
    public Model update(long store,long id,long revision,String name,String description,List<TemplateFieldDefinition> definitions) {
        var owner=lock(store);var template=require(store,id);
        if(template.revision!=revision)throw error("template.stale");
        var content=validate(definitions);features(owner,content,null);validateImages(store,content);
        template.name=name(name);template.description=description(description);template.fieldsJson=write(content);template.updatedAt=Instant.now();
        templates.saveAndFlush(template);images.pinTemplate(store,id,imageOptions(content));return model(template);
    }
    @Transactional
    public Model duplicate(long store,String key,String name) {
        var owner=lock(store);var source=source(store,key);
        return createLocked(owner,name,source.description(),source.fields(),key);
    }
    @Transactional
    public Model saveProduct(long store,long product,String name,String description) {
        var owner=lock(store);var rule=rules.findWithFieldsByStoreIdAndProductId(store,product).orElseThrow(()->error("template.product.empty"));
        return createLocked(owner,name,description,ordered(rule).stream().map(TemplateFieldDefinition::from).toList(),"PRODUCT:"+product);
    }
    @Transactional
    public void delete(long store,long id,long revision,boolean confirmed) {
        lock(store);var template=require(store,id);
        if(!confirmed)throw error("template.confirm");
        if(template.revision!=revision)throw error("template.stale");
        images.unpinTemplate(store,id);templates.delete(template);templates.flush();
    }
    @Transactional
    public Preview preview(long store,String key,long product) {
        lock(store);if(product<=0)throw error("template.product.invalid");
        var source=source(store,key);var rule=rules.findWithFieldsByStoreIdAndProductId(store,product).orElse(null);
        return new Preview(source,product,rule==null?List.of():ordered(rule).stream().map(TemplateFieldDefinition::from).toList(),token(store,product,source,rule));
    }
    @Transactional
    public int apply(long store,String key,long product,String productName,Mode mode,boolean confirmed,String requestId,String reviewToken) {
        var owner=lock(store);
        try {if(!UUID.fromString(requestId).toString().equals(requestId))throw error("template.request.invalid");}
        catch(IllegalArgumentException|NullPointerException e){throw error("template.request.invalid");}
        if(mode==null)throw error("template.mode.required");
        var previous=jdbc.queryForList("select * from template_applications where store_id=? and request_id=?",store,requestId);
        if(!previous.isEmpty()) {
            var row=previous.getFirst();
            if(!key.equals(row.get("source_key")) || ((Number)row.get("product_id")).longValue()!=product
                    || !mode.name().equals(row.get("apply_mode")) || !Objects.equals(reviewToken,row.get("review_token")))throw error("template.request.conflict");
            return ((Number)row.get("field_count")).intValue();
        }
        var source=source(store,key);
        var rule=rules.findWithFieldsByStoreIdAndProductId(store,product).orElse(null);
        if(product<=0 || !Objects.equals(reviewToken,token(store,product,source,rule)))throw error("template.stale");
        if(mode==Mode.REPLACE && !confirmed)throw error("template.confirm");
        var definitions=validate(source.fields());
        var current=rule==null?List.<PersonalizationField>of():ordered(rule);
        long count=definitions.size()+(mode==Mode.APPEND?current.size():0),limit=limits.fieldLimit(owner.getEffectivePlan());
        if(limit!=-1 && count>limit)throw error("template.fields.limit");
        if(rule==null && !limits.canAddProduct(owner))throw error("template.products.limit");
        List<TemplateFieldDefinition> combined=new ArrayList<>();
        if(mode==Mode.APPEND)current.forEach(f->combined.add(TemplateFieldDefinition.from(f)));
        combined.addAll(definitions);uniqueLabels(combined);
        features(owner,definitions,product);validateImages(store,definitions);
        if(mode==Mode.REPLACE && rule!=null) {
            for(var old:current)admin.deleteField(store,product,old.getId());
            em.flush();em.clear();
        }
        rule=admin.ensureRule(store,product,productName);
        int order=mode==Mode.APPEND?current.stream().mapToInt(PersonalizationField::getSortOrder).max().orElse(-1)+1:0;
        for(var definition:definitions) {
            var field=new PersonalizationField();admin.applyForm(field,definition.toForm(order++));field.setRule(rule);
            fields.saveAndFlush(field);history.remember(field);images.bindShared(field,field.imageOptions());
        }
        jdbc.update("insert into template_applications(store_id,request_id,source_key,product_id,apply_mode,review_token,field_count,created_at) values (?,?,?,?,?,?,?,?)",
                store,requestId,key,product,mode.name(),reviewToken,definitions.size(),Timestamp.from(Instant.now()));
        cache.invalidateStoreAfterCommit(store);return definitions.size();
    }
    public boolean uploadsAvailable(Store store) {return uploads.enabled() && uploadRights.usage(store.getStoreId()).acceptsUploads();}
    private List<TemplateFieldDefinition> validate(List<TemplateFieldDefinition> definitions) {
        if(definitions==null || definitions.isEmpty() || definitions.size()>100)throw error("template.fields.invalid");
        List<TemplateFieldDefinition> result=new ArrayList<>();
        for(var definition:definitions) {
            if(definition==null || definition.label()==null || definition.label().isBlank() || definition.label().length()>100
                    || definition.label().contains("\n") || definition.label().contains("\r") || definition.maxLength()==null
                    || definition.placeholder()!=null && definition.placeholder().length()>150
                    || definition.validationPattern()!=null && definition.validationPattern().length()>255
                    || definition.optionsText()!=null && definition.optionsText().length()>20000)throw error("template.fields.invalid");
            var form=definition.toForm(result.size());
            if(!validator.validate(form).isEmpty())throw error("template.fields.invalid");
            var normalized=new PersonalizationField();
            try{admin.applyForm(normalized,form);}catch(IllegalArgumentException e){throw error("template.fields.invalid");}
            result.add(TemplateFieldDefinition.from(normalized));
        }
        uniqueLabels(result);return List.copyOf(result);
    }
    private void uniqueLabels(List<TemplateFieldDefinition> definitions) {
        Set<String> labels=new HashSet<>();
        for(var field:definitions) {
            String key=OrderPersonalization.propertyName(field.label()).toLowerCase(Locale.ROOT);
            if(key.startsWith("_") || !labels.add(key))throw error("template.labels.duplicate");
        }
    }
    private void features(Store owner,List<TemplateFieldDefinition> definitions,Long product) {
        for(var definition:definitions) {
            if(definition.fieldType()==FieldType.FILE_UPLOAD && !uploadsAvailable(owner))throw error("template.upload.unavailable");
            if(definition.fieldType()==FieldType.IMAGE_SELECT) {
                var options=ImageOptions.parse(definition.imageOptionsJson());
                if(limits.imageProductLimit(owner.getEffectivePlan())==0 || options.size()>limits.imageOptionLimit(owner.getEffectivePlan()))throw error("template.images.limit");
                if(product!=null && !limits.canConfigureImageProduct(owner,product))throw error("template.images.limit");
            }
        }
    }
    private void validateImages(long store,List<TemplateFieldDefinition> definitions) {images.validateShared(store,imageOptions(definitions));}
    private List<ImageOption> imageOptions(List<TemplateFieldDefinition> definitions) {
        Map<String,ImageOption> options=new LinkedHashMap<>();
        definitions.stream().filter(f->f.fieldType()==FieldType.IMAGE_SELECT).forEach(f->ImageOptions.parse(f.imageOptionsJson()).forEach(o->options.put(o.id(),o)));
        return List.copyOf(options.values());
    }
    private Store active(long store) {return stores.findActiveByStoreId(store).orElseThrow(()->error("template.not.found"));}
    private Store lock(long store) {return stores.findActiveByStoreIdForUpdate(store).orElseThrow(()->error("template.not.found"));}
    private StoreTemplate require(long store,long id) {return templates.findByIdAndStoreId(id,store).orElseThrow(()->error("template.not.found"));}
    private Model source(long store,String key) {
        if(key==null)throw error("template.not.found");
        if(key.startsWith("catalog:"))return initial().stream().filter(t->t.key().equals(key)).findFirst().orElseThrow(()->error("template.not.found"));
        if(key.startsWith("own:"))try{return model(require(store,Long.parseLong(key.substring(4))));}catch(NumberFormatException e){throw error("template.not.found");}
        throw error("template.not.found");
    }
    private Model model(StoreTemplate template) {
        try{return new Model("own:"+template.id,template.id,template.name,template.description,template.origin,template.revision,
                List.copyOf(json.readValue(template.fieldsJson,new TypeReference<List<TemplateFieldDefinition>>(){})));}
        catch(Exception e){throw error("template.fields.invalid");}
    }
    private List<PersonalizationField> ordered(PersonalizationRule rule) {return rule.getFields().stream().sorted(Comparator.comparing(PersonalizationField::getSortOrder).thenComparing(PersonalizationField::getId)).toList();}
    private String token(long store,long product,Model source,PersonalizationRule rule) {
        String state=store+":"+product+":"+source.key()+":"+source.revision()+":"+write(source.fields())+":"+(rule==null?"new":rule.getId()+":"+ordered(rule).stream().map(f->f.getId()+":"+write(TemplateFieldDefinition.from(f))).toList());
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private String write(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw error("template.fields.invalid");}}
    private String name(String name) {if(name==null || name.isBlank() || name.strip().length()>100)throw error("template.name.invalid");return name.strip();}
    private String description(String description) {if(description!=null && description.length()>500)throw error("template.description.invalid");return description==null?null:description.strip();}
    private static Invalid error(String code){return new Invalid(code);}
}
