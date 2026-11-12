package br.com.nuvemcustomfields.controller;
import br.com.nuvemcustomfields.service.*;
import br.com.nuvemcustomfields.i18n.Messages;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@Controller
public class TemplateBatchController {
 public record Review(String key,String mode,List<Long> productIds) {}
 public record Execute(long productId,boolean confirmed,boolean allowModified) {}
 private final TemplateBatchService batches;private final StoreTemplateService templates;private final AdminStoreService stores;private final Messages messages;
 public TemplateBatchController(TemplateBatchService batches,StoreTemplateService templates,AdminStoreService stores,Messages messages){this.batches=batches;this.templates=templates;this.stores=stores;this.messages=messages;}
 @GetMapping("/admin/templates/batch")
 public String page(@RequestParam String key,@RequestParam(required=false) String batchId,HttpSession session,Model model) {
  var store=stores.requireCurrentStore(session);model.addAttribute("store",store);
  // Existing batches remain viewable even after their source template has been deleted.
  if(batchId!=null && !batchId.isBlank())key=batches.get(store.getStoreId(),batchId).key();
  String templateName;
  try{templateName=templates.get(store.getStoreId(),key).name();}catch(StoreTemplateService.Invalid ex){templateName=messages.get("batch.source.deleted");}
  model.addAttribute("batchName",templateName);model.addAttribute("batchKey",key);model.addAttribute("batchId",batchId);
  var ui=new LinkedHashMap<String,String>();
  for(String name:List.of("title","intro","search","brand","category","configuration","all","configured","unconfigured","linked","selectAll","selection","review","execute","retry","before","after","modified","confirmModified","ready","succeeded","failed","blocked","running","empty","refresh","history","resume","replaceConfirm","selectionHint","source","revision","unchanged","append","replace","update","otherSource","complete","required","optional","maxLength","example","validation","options","maxFiles","zoomOn","zoomOff","columns"))ui.put(name,messages.get("batch."+name));
  for(String code:List.of("batch.catalog.failed","batch.catalog.limit","batch.selection.invalid","batch.source.missing","batch.modified.confirm","batch.operation.failed","template.stale","template.fields.limit","template.products.limit","template.labels.duplicate","template.upload.unavailable","template.images.limit","template.fields.invalid","template.product.invalid","template.not.found","template.confirm","upload.multiple.ultra"))ui.put(code,messages.get(code));
  for(var type:br.com.nuvemcustomfields.entity.FieldType.values())ui.put("type."+type.name(),messages.get("field.type."+type.name()));
  model.addAttribute("batchUi",ui);return "admin/template-batch";
 }
 @GetMapping("/admin/templates/batch/products") @ResponseBody
 public ResponseEntity<?> products(HttpSession session){return ok(batches.products(stores.requireCurrentStore(session).getStoreId()));}
 @GetMapping("/admin/templates/batch/history") @ResponseBody
 public ResponseEntity<?> history(HttpSession session){return ok(batches.recent(stores.requireCurrentStore(session).getStoreId()));}
 @PostMapping("/admin/templates/batch/review") @ResponseBody
 public ResponseEntity<?> review(@RequestHeader("X-NCF-Action") String action,@RequestBody Review body,HttpSession session) {
  write(action);TemplateBatchService.Mode mode;
  try{mode=TemplateBatchService.Mode.valueOf(body.mode());}catch(RuntimeException ex){throw new StoreTemplateService.Invalid("template.mode.required");}
  return ok(batches.review(stores.requireCurrentStore(session).getStoreId(),body.key(),mode,body.productIds()));
 }
 @GetMapping("/admin/templates/batch/{id}") @ResponseBody
 public ResponseEntity<?> get(@PathVariable String id,HttpSession session){return ok(batches.get(stores.requireCurrentStore(session).getStoreId(),id));}
 @PostMapping("/admin/templates/batch/{id}/execute") @ResponseBody
 public ResponseEntity<?> execute(@PathVariable String id,@RequestHeader("X-NCF-Action") String action,@RequestBody Execute body,HttpSession session){write(action);return ok(batches.execute(stores.requireCurrentStore(session).getStoreId(),id,body.productId(),body.confirmed(),body.allowModified()));}
 private void write(String action){if(!"template-write".equals(action))throw new StoreTemplateService.Invalid("template.confirm");}
 private ResponseEntity<?> ok(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
 @ExceptionHandler(IllegalArgumentException.class) @ResponseBody
 public ResponseEntity<?> error(IllegalArgumentException ex){String code=ex instanceof StoreTemplateService.Invalid?ex.getMessage():"batch.operation.failed";return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("error",messages.get(code),"code",code));}
}
