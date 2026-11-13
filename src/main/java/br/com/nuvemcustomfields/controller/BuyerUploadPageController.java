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
    public String page(HttpSession session,Model model,@org.springframework.web.bind.annotation.RequestParam(required=false) Long orderId){
        if(orderId!=null && orderId<=0)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        model.addAttribute("selectedOrderId",orderId);model.addAttribute("store",stores.requireCurrentStore(session));
        var ui=new java.util.LinkedHashMap<String,String>();
        for(String key:java.util.List.of(
                "title","intro","retention","quotaNote","occupied","available",
                "quota","plans","sort","oldest","newest","largest",
                "before","filter","clear","refresh","listTitle","selectionHint",
                "selectPage","deleteSelected","selection","noneSelected","empty",
                "loading","loadFailed","pending","full","warning","disabled",
                "order","orderSummary","selectOrder","selectFile","file",
                "item","size","expires","state","actions","download",
                "ordered","deleting","expired","unavailable","page","previous",
                "next","confirmTitle","confirmSummary","confirmWarning","cancel","confirmDelete",
                "deletingNow","deleteQueued","deleteFailed","maxSelection","downloadFailed",
                "downloadLimit","invalidSelection","missingFile","orderStatus","paymentStatus","shippingStatus","status.unknown")) ui.put(key,messages.get("buyer.files."+key));
        for(String state:java.util.List.of("open","closed","cancelled","authorized","pending","paid","partially_paid","abandoned","refunded","partially_refunded","voided","unpacked","shipped","unshipped","delivered","partially_packed","partially_fulfilled","fulfilled","unfulfilled"))
            ui.put("status."+state,messages.get("buyer.files.status."+state));
        model.addAttribute("fileUi",ui);return "admin/buyer-uploads";}
}
