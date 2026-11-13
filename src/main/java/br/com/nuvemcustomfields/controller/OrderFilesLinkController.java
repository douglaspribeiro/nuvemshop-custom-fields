package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.repository.StoreRepository;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Entry point for Nuvemshop admin links. Query parameters never authenticate a store. */
@Controller
public class OrderFilesLinkController {
    private static final String PENDING="orderFilesNavigation";
    private final StoreRepository stores;
    public OrderFilesLinkController(StoreRepository stores){this.stores=stores;}
    private record Destination(long storeId,long orderId,Instant expiresAt) implements Serializable {
        String path(){return "/admin/buyer-uploads?orderId="+orderId;}
    }
    @GetMapping("/nuvemshop/order-files")
    public String open(@RequestParam long store,@RequestParam long id,HttpSession session) {
        if(store<=0 || id<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var destination=new Destination(store,id,Instant.now().plusSeconds(600));
        Object owner=session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY);
        if(owner instanceof Long active && stores.findActiveByStoreId(active).isPresent()) {
            session.removeAttribute(PENDING);
            if(active!=store)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            return "redirect:"+destination.path();
        }
        session.setAttribute(PENDING,destination);
        return "redirect:/admin/embedded";
    }
    static String afterAuthentication(HttpSession session,long storeId) {
        Object pending=session.getAttribute(PENDING);session.removeAttribute(PENDING);
        if(pending instanceof Destination destination && destination.storeId()==storeId && destination.expiresAt().isAfter(Instant.now()))
            return destination.path();
        return "/admin";
    }
}
