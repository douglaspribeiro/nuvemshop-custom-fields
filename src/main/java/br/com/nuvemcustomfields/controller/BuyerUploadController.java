package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class BuyerUploadController {
    private final BuyerUploadService files;
    private final BuyerUploadLedger ledger;
    private final AdminStoreService stores;
    public BuyerUploadController(BuyerUploadService files,BuyerUploadLedger ledger,AdminStoreService stores) {
        this.files=files;this.ledger=ledger;this.stores=stores;
    }
    @PostMapping("/public/stores/{store}/buyer-uploads/session")
    public ResponseEntity<?> session(@PathVariable long store) { return ok(Map.of("token",files.session(store))); }
    @PostMapping("/public/stores/{store}/buyer-uploads")
    public ResponseEntity<?> upload(@PathVariable long store,@RequestParam long productId,@RequestParam long fieldId,
            @RequestHeader("X-Upload-Session") String token,@RequestHeader("X-Upload-Request") String request,
            @RequestParam MultipartFile file) { return ok(files.upload(store,productId,fieldId,token,request,file)); }
    @PostMapping("/public/stores/{store}/buyer-uploads/{id}/cart")
    public ResponseEntity<?> cart(@PathVariable long store,@PathVariable String id,@RequestHeader("X-Upload-Session") String token) {
        files.cart(store,token,id);return ok(Map.of("ready",true));
    }
    @PostMapping("/public/stores/{store}/buyer-uploads/{id}/validate")
    public ResponseEntity<?> validate(@PathVariable long store,@PathVariable String id,@RequestHeader("X-Upload-Session") String token) {
        files.validate(store,token,id);return ok(Map.of("ready",true));
    }
    @DeleteMapping("/public/stores/{store}/buyer-uploads/{id}")
    public ResponseEntity<?> remove(@PathVariable long store,@PathVariable String id,@RequestHeader("X-Upload-Session") String token) {
        files.remove(store,token,id);return ok(Map.of("removed",true));
    }
    @GetMapping("/admin/buyer-uploads/data")
    public ResponseEntity<?> list(HttpSession session) {
        long store=stores.requireCurrentStore(session).getStoreId();
        return ok(Map.of("usage",ledger.usage(store),"files",files.orders(store),"enabled",files.enabled()));
    }
    @GetMapping("/admin/buyer-uploads/{id}/download")
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> download(@PathVariable String id,HttpSession session) {
        var file=files.download(stores.requireCurrentStore(session).getStoreId(),id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .header("Content-Security-Policy","sandbox")
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.name(),StandardCharsets.UTF_8).build().toString())
                .contentLength(file.size()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(output->{try(var input=file.stream()){input.transferTo(output);}});
    }
    @PostMapping("/admin/buyer-uploads/{id}/delete")
    public ResponseEntity<?> delete(@PathVariable String id,@RequestParam boolean confirmed,
            @RequestHeader("X-NCF-Action") String action,HttpSession session) {
        if(!"delete".equals(action)) throw new BuyerUploadLedger.Rejected("upload.confirm.required");
        if(!confirmed) throw new BuyerUploadLedger.Rejected("upload.confirm.required");
        ledger.removeOrdered(stores.requireCurrentStore(session).getStoreId(),id);return ok(Map.of("removed",true));
    }
    @ExceptionHandler(BuyerUploadLedger.Rejected.class)
    public ResponseEntity<?> rejected(BuyerUploadLedger.Rejected error) {
        String code=error.getMessage();
        String message=switch(code) {
            case "upload.size.invalid", "upload.size.mismatch" -> "Envie um arquivo completo de até 20 MB.";
            case "upload.content.invalid", "upload.formats.invalid" -> "Arquivo inválido. Use JPG, PNG ou PDF sem senha, ações ou anexos.";
            case "upload.quota.exceeded", "upload.unavailable" -> "O envio está indisponível nesta loja. Entre em contato com a loja.";
            case "upload.rate.limit", "upload.session.limit" -> "Muitas tentativas de envio. Aguarde e tente novamente.";
            case "upload.session.invalid" -> "Sua sessão expirou. Atualize a página e tente novamente.";
            default -> "Não foi possível concluir. Tente novamente ou entre em contato com a loja.";
        };
        return ResponseEntity.status(code.equals("upload.rate.limit")?429:400).cacheControl(CacheControl.noStore()).body(Map.of("error",message,"code",code));
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<?> tooLarge() {return ResponseEntity.status(413).cacheControl(CacheControl.noStore())
            .body(Map.of("error","Envie um arquivo de até 20 MB.","code","upload.size.invalid"));}
    private ResponseEntity<?> ok(Object value) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
