package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.i18n.Messages;
import br.com.nuvemcustomfields.service.*;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/admin/rate-box")
public class MerchantRateBoxController {
    public record Vote(java.math.BigDecimal rating,String comment) {}
    private final AdminStoreService stores;
    private final MerchantRateBoxService rateBox;
    private final Messages messages;
    public MerchantRateBoxController(AdminStoreService stores,MerchantRateBoxService rateBox,Messages messages) {
        this.stores=stores;this.rateBox=rateBox;this.messages=messages;
    }
    private long owner(String action,HttpSession session) {
        if(!"rate-box".equals(action) || Boolean.TRUE.equals(session.getAttribute(BackofficeSessionInterceptor.STORE_MODE_SESSION_KEY)))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        return stores.requireCurrentStore(session).getStoreId();
    }
    @PostMapping("/display")
    public ResponseEntity<?> display(@RequestHeader("X-NCF-Action") String action,HttpSession session) {
        return rateBox.display(owner(action,session)).<ResponseEntity<?>>map(this::ok)
                .orElseGet(()->ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }
    @PostMapping("/{id}/rate")
    public ResponseEntity<?> rate(@RequestHeader("X-NCF-Action") String action,@PathVariable String id,
            @RequestBody Vote vote,HttpSession session) {
        long storeId=owner(action,session);
        Integer score;
        try { score=vote.rating()==null?null:vote.rating().intValueExact(); }
        catch (ArithmeticException error) { throw new IllegalArgumentException("ratebox.invalid"); }
        return ok(rateBox.rate(storeId,id,score,vote.comment()));
    }
    @PostMapping("/{id}/public-review")
    public ResponseEntity<?> publicReview(@RequestHeader("X-NCF-Action") String action,@PathVariable String id,HttpSession session) {
        rateBox.publicReviewClicked(owner(action,session),id);return ok(Map.of("recorded",true));
    }
    @PostMapping("/{id}/dismiss")
    public ResponseEntity<?> dismiss(@RequestHeader("X-NCF-Action") String action,@PathVariable String id,HttpSession session) {
        rateBox.dismiss(owner(action,session),id);return ok(Map.of("dismissed",true));
    }
    private ResponseEntity<?> ok(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid(IllegalArgumentException error){return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("error",messages.get(error.getMessage())));}
}
