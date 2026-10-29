package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.config.LocalHomologationGuard;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Controller
@Profile("local-homolog & !prod & !production")
@ConditionalOnProperty(name="local.homologation.enabled",havingValue="true")
public class LocalHomologationController {
    private final LocalHomologationGuard guard;
    private final StoreRepository stores;
    private final br.com.nuvemcustomfields.service.NuvemshopApiClient api;
    public LocalHomologationController(LocalHomologationGuard guard,StoreRepository stores,br.com.nuvemcustomfields.service.NuvemshopApiClient api){this.guard=guard;this.stores=stores;this.api=api;}
    @GetMapping("/local/homologacao")
    public String entry(HttpServletResponse response){response.setHeader("Cache-Control","no-store");return "local/homologacao";}
    @GetMapping("/local/vitrine")
    public String storefront(@RequestParam(defaultValue="900001") Long productId,
            HttpServletRequest request, HttpServletResponse response, org.springframework.ui.Model model){
        response.setHeader("Cache-Control","no-store");
        var session=request.getSession(false);
        if(session==null || session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY)==null)
            return "redirect:/local/homologacao";
        if(!Long.valueOf(LocalHomologationGuard.STORE_ID).equals(session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var store=stores.findActiveByStoreId(LocalHomologationGuard.STORE_ID)
                .filter(s->LocalHomologationGuard.STORE_TOKEN.equals(s.getAccessToken()))
                .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN));
        try{
            model.addAttribute("product",api.getProduct(store,productId));
            model.addAttribute("variants",api.listProductVariants(store,productId));
        }catch(IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
        model.addAttribute("products",api.listProducts(store,1,50,null).items());
        model.addAttribute("storeId",store.getStoreId());
        return "local/vitrine";
    }

    @PostMapping("/local/homologacao")
    @Transactional
    public String enter(@RequestParam String accessKey,HttpServletRequest request,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");
        if(!guard.accepts(accessKey))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chave local inválida.");
        Store store=stores.findByStoreId(LocalHomologationGuard.STORE_ID).orElseGet(()->{
            Store s=new Store();s.setStoreId(LocalHomologationGuard.STORE_ID);s.setStoreName("[HOMOLOGAÇÃO LOCAL] Loja fictícia");
            s.setAccessToken(LocalHomologationGuard.STORE_TOKEN);s.setStoreCountryCode("BR");s.setStoreCurrency("BRL");s.setStoreEmail("loja-local@example.invalid");return stores.save(s);
        });
        if(!LocalHomologationGuard.STORE_TOKEN.equals(store.getAccessToken()) || !store.isActive())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"O ID local já existe sem a identificação da loja fictícia.");
        store.setScope("read_products,read_orders,write_scripts,read_scripts");
        stores.save(store);
        var previous=request.getSession(false);if(previous!=null)previous.invalidate();
        request.getSession(true).setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,store.getStoreId());
        return "redirect:/admin";
    }
}
