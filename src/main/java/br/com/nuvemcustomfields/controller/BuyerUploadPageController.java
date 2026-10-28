package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.AdminStoreService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class BuyerUploadPageController {
    private final AdminStoreService stores;
    public BuyerUploadPageController(AdminStoreService stores){this.stores=stores;}
    @GetMapping("/admin/buyer-uploads")
    public String page(HttpSession session,Model model){model.addAttribute("store",stores.requireCurrentStore(session));return "admin/buyer-uploads";}
}
