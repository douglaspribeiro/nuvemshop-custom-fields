package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.StoreEmailHistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class BackofficeStoreEmailHistoryController {
    private final StoreRepository stores;
    private final StoreEmailHistoryService history;
    public BackofficeStoreEmailHistoryController(StoreRepository stores,StoreEmailHistoryService history) {
        this.stores=stores;this.history=history;
    }
    @GetMapping("/backoffice/stores/{storeId}/emails")
    public String page(@PathVariable long storeId,@RequestParam(defaultValue="0") int page,Model model,jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        if(page<0 || page>100_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        model.addAttribute("store",stores.findByStoreId(storeId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)));
        var rows=history.page(storeId,page);
        model.addAttribute("emails",rows.stream().limit(25).toList());
        model.addAttribute("hasNext",rows.size()>25);
        model.addAttribute("page",page);
        return "backoffice/store-emails";
    }
}
