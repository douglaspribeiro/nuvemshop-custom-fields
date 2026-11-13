package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.BuyerStorageUsageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class BackofficeStorageUsageController {
    private final StoreRepository stores;
    private final BuyerStorageUsageService usage;
    public BackofficeStorageUsageController(StoreRepository stores,BuyerStorageUsageService usage){this.stores=stores;this.usage=usage;}
    @GetMapping("/backoffice/stores/{storeId}/storage")
    public String page(@PathVariable long storeId,Model model){
        model.addAttribute("store",stores.findByStoreId(storeId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)));
        model.addAttribute("usage",usage.summary(storeId));
        return "backoffice/store-storage";
    }
}
