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
    public LocalHomologationController(LocalHomologationGuard guard,StoreRepository stores){this.guard=guard;this.stores=stores;}
    @GetMapping("/local/homologacao")
    public String entry(HttpServletResponse response){response.setHeader("Cache-Control","no-store");return "local/homologacao";}
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
        var previous=request.getSession(false);if(previous!=null)previous.invalidate();
        request.getSession(true).setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,store.getStoreId());
        return "redirect:/admin";
    }
}
