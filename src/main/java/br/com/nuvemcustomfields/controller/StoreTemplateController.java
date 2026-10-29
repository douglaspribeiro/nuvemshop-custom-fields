package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.dto.*;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.i18n.Messages;
import br.com.nuvemcustomfields.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Controller
public class StoreTemplateController {
    public record Document(String name,String description,Long revision,List<TemplateFieldDefinition> fields) {}
    public record Copy(String key,String name) {}
    public record Delete(Long revision,boolean confirmed) {}
    public record Apply(String key,Long productId,String mode,boolean confirmed,String requestId,String reviewToken) {}
    private final StoreTemplateService templates;
    private final AdminStoreService stores;
    private final NuvemshopApiClient api;
    private final OptionImageService images;
    private final PlanLimitService limits;
    private final Messages messages;
    public StoreTemplateController(StoreTemplateService templates,AdminStoreService stores,NuvemshopApiClient api,
            OptionImageService images,PlanLimitService limits,Messages messages) {
        this.templates=templates;this.stores=stores;this.api=api;this.images=images;this.limits=limits;this.messages=messages;
    }
    @GetMapping("/admin/templates")
    public String library(HttpSession session,Model model) {
        var store=stores.requireCurrentStore(session);model.addAttribute("store",store);
        model.addAttribute("ownTemplates",templates.list(store.getStoreId()));model.addAttribute("initialTemplates",templates.initial());
        return "admin/templates-library";
    }
    @GetMapping("/admin/templates/new")
    public String createPage(HttpSession session,Model model) {return editor(session,model,null);}
    @GetMapping("/admin/templates/{templateId}/edit")
    public String editPage(@PathVariable long templateId,HttpSession session,Model model) {
        var store=stores.requireCurrentStore(session);return editor(session,model,templates.get(store.getStoreId(),"own:"+templateId));
    }
    private String editor(HttpSession session,Model model,StoreTemplateService.Model template) {
        var store=stores.requireCurrentStore(session);model.addAttribute("store",store);
        Map<String,Object> data=new HashMap<>();data.put("template",template);data.put("uploadAvailable",templates.uploadsAvailable(store));
        data.put("imagesAvailable",images.enabled() && limits.imageProductLimit(store.getEffectivePlan())!=0);
        data.put("imageOptionLimit",limits.imageOptionLimit(store.getEffectivePlan()));model.addAttribute("editorData",data);
        return "admin/template-editor";
    }
    @GetMapping("/admin/templates/choose")
    public String choose(@RequestParam String key,@RequestParam(defaultValue="1") int page,@RequestParam(required=false) String q,
            HttpSession session,Model model) {
        var store=stores.requireCurrentStore(session);model.addAttribute("store",store);model.addAttribute("template",templates.get(store.getStoreId(),key));
        model.addAttribute("productPage",api.listProducts(store,page,NuvemshopApiClient.DEFAULT_PER_PAGE,q));return "admin/template-choose";
    }
    @GetMapping("/admin/templates/review")
    public String review(@RequestParam String key,@RequestParam long productId,HttpSession session,Model model) {
        var store=stores.requireCurrentStore(session);var product=product(store,productId);
        var preview=templates.preview(store.getStoreId(),key,productId);model.addAttribute("store",store);
        model.addAttribute("preview",preview);model.addAttribute("product",product);model.addAttribute("requestId",UUID.randomUUID().toString());return "admin/template-review";
    }
    @PostMapping("/admin/templates") @ResponseBody
    public ResponseEntity<?> create(@RequestHeader("X-NCF-Action") String action,@RequestBody Document document,HttpSession session) {
        write(action);return ok(templates.create(stores.requireCurrentStore(session).getStoreId(),document.name(),document.description(),document.fields()));
    }
    @PostMapping("/admin/templates/{templateId}") @ResponseBody
    public ResponseEntity<?> update(@PathVariable long templateId,@RequestHeader("X-NCF-Action") String action,@RequestBody Document document,HttpSession session) {
        write(action);if(document.revision()==null)throw new StoreTemplateService.Invalid("template.stale");
        return ok(templates.update(stores.requireCurrentStore(session).getStoreId(),templateId,document.revision(),document.name(),document.description(),document.fields()));
    }
    @PostMapping("/admin/templates/duplicate") @ResponseBody
    public ResponseEntity<?> duplicate(@RequestHeader("X-NCF-Action") String action,@RequestBody Copy document,HttpSession session) {
        write(action);return ok(templates.duplicate(stores.requireCurrentStore(session).getStoreId(),document.key(),document.name()));
    }
    @PostMapping("/admin/products/{productId}/save-template") @ResponseBody
    public ResponseEntity<?> saveProduct(@PathVariable long productId,@RequestHeader("X-NCF-Action") String action,@RequestBody Document document,HttpSession session) {
        write(action);return ok(templates.saveProduct(stores.requireCurrentStore(session).getStoreId(),productId,document.name(),document.description()));
    }
    @PostMapping("/admin/templates/{templateId}/delete") @ResponseBody
    public ResponseEntity<?> delete(@PathVariable long templateId,@RequestHeader("X-NCF-Action") String action,@RequestBody Delete document,HttpSession session) {
        write(action);if(document.revision()==null)throw new StoreTemplateService.Invalid("template.stale");
        templates.delete(stores.requireCurrentStore(session).getStoreId(),templateId,document.revision(),document.confirmed());return ok(Map.of("deleted",true));
    }
    @PostMapping("/admin/templates/apply") @ResponseBody
    public ResponseEntity<?> apply(@RequestHeader("X-NCF-Action") String action,@RequestBody Apply document,HttpSession session) {
        write(action);var store=stores.requireCurrentStore(session);
        if(document.productId()==null)throw new StoreTemplateService.Invalid("template.product.invalid");
        StoreTemplateService.Mode mode;
        try{mode=StoreTemplateService.Mode.valueOf(document.mode());}catch(IllegalArgumentException|NullPointerException e){throw new StoreTemplateService.Invalid("template.mode.required");}
        var product=product(store,document.productId());
        int count=templates.apply(store.getStoreId(),document.key(),product.id(),product.name(),mode,document.confirmed(),document.requestId(),document.reviewToken());
        return ok(Map.of("count",count,"redirect","/admin/products/"+product.id()+"/fields"));
    }
    @PostMapping("/admin/templates/images") @ResponseBody
    public ResponseEntity<?> uploadImage(@RequestHeader("X-NCF-Action") String action,@RequestParam MultipartFile file,HttpSession session) {
        write(action);return ok(images.uploadForTemplate(stores.requireCurrentStore(session).getStoreId(),file));
    }
    private ProductSummary product(Store store,long id) {
        try{return api.getProduct(store,id);}catch(RuntimeException e){throw new StoreTemplateService.Invalid("template.product.invalid");}
    }
    private void write(String action) {if(!"template-write".equals(action))throw new StoreTemplateService.Invalid("template.confirm");}
    private ResponseEntity<?> ok(Object value) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @ExceptionHandler(IllegalArgumentException.class) @ResponseBody
    public ResponseEntity<?> invalid(IllegalArgumentException error) {
        String code=error instanceof StoreTemplateService.Invalid?error.getMessage():"template.fields.invalid";
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("error",messages.get(code),"code",code));
    }
}
