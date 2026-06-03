package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.repository.StoreRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {

    private final StoreRepository storeRepository;

    public AdminController(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @GetMapping("/admin")
    public String index(HttpSession session, Model model) {
        Long storeId = (Long) session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY);
        storeRepository.findActiveByStoreId(storeId).ifPresent(store -> model.addAttribute("store", store));
        return "admin/index";
    }
}
