package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.AdminStoreService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class BuyerUploadPageController {
    private final AdminStoreService stores;
    private final br.com.nuvemcustomfields.i18n.Messages messages;
    public BuyerUploadPageController(AdminStoreService stores,br.com.nuvemcustomfields.i18n.Messages messages){this.stores=stores;this.messages=messages;}
    @GetMapping("/admin/buyer-uploads")
    public String page(HttpSession session,Model model){model.addAttribute("store",stores.requireCurrentStore(session));
        var ui=new java.util.LinkedHashMap<String,String>();
        for(String key:java.util.List.of(
                "title","intro","retention","quotaNote","occupied","available",
                "quota","plans","sort","oldest","newest","largest",
                "before","filter","clear","refresh","listTitle","selectionHint",
                "selectPage","downloadSelected","deleteSelected","selection","noneSelected","empty",
                "loading","loadFailed","pending","full","warning","disabled",
                "order","orderSummary","selectOrder","selectFile","downloadOrder","file",
                "item","size","expires","state","actions","download",
                "ordered","deleting","expired","unavailable","page","previous",
                "next","confirmTitle","confirmSummary","confirmWarning","cancel","confirmDelete",
                "deletingNow","deleteQueued","deleteFailed","maxSelection","downloadStarted","downloadFailed",
                "expiredSelection","downloadLimit","invalidSelection","missingFile")) ui.put(key,messages.get("buyer.files."+key));
        model.addAttribute("fileUi",ui);return "admin/buyer-uploads";}
}
