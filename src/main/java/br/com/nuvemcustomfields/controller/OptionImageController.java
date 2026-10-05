package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.ImagePlanLimitException;
import br.com.nuvemcustomfields.service.OptionImageService;
import br.com.nuvemcustomfields.service.AdminStoreService;
import br.com.nuvemcustomfields.i18n.Messages;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.net.URI;
import java.util.Map;

@RestController
public class OptionImageController {
    private final OptionImageService images;
    private final AdminStoreService stores;
    private final Messages messages;
    public OptionImageController(OptionImageService images, AdminStoreService stores, Messages messages) {
        this.images=images; this.stores=stores; this.messages=messages;
    }
    @PostMapping("/admin/products/{productId}/images")
    public ResponseEntity<?> upload(@PathVariable Long productId, @RequestParam MultipartFile file, HttpSession session) {
        var store=stores.requireCurrentStore(session);
        try { return ResponseEntity.ok().header("Cache-Control","no-store").body(images.upload(store.getStoreId(),productId,file)); }
        catch(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e instanceof ImagePlanLimitException limit ? messages.get(limit.getMessage(), limit.arguments()) : messages.get(e.getMessage()))); }
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<?> tooLarge() {
        return ResponseEntity.badRequest().body(Map.of("error", messages.get("image.file.invalid")));
    }
    @GetMapping("/admin/images/{id}")
    public ResponseEntity<Void> adminImage(@PathVariable String id, @RequestParam(defaultValue="preview") String size, HttpSession session) {
        return redirect(images.adminReadUrl(stores.requireCurrentStore(session).getStoreId(),id,"thumbnail".equals(size)));
    }
    @GetMapping("/public/stores/{storeId}/images/{id}")
    public ResponseEntity<Void> publicImage(@PathVariable Long storeId, @PathVariable String id, @RequestParam(defaultValue="preview") String size) {
        return redirect(images.readUrl(storeId,id,"thumbnail".equals(size)));
    }
    private ResponseEntity<Void> redirect(String url) {
        if(url==null) return ResponseEntity.notFound().header("Cache-Control","no-store").build();
        return ResponseEntity.status(302).location(URI.create(url)).header("Cache-Control","no-store").build();
    }
}
