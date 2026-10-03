package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.AdminStoreService;
import br.com.nuvemcustomfields.service.EfiUpgradeService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

@ControllerAdvice(assignableTypes=AdminController.class)
public class UpgradeBillingAdvice {
    private final AdminStoreService stores;
    private final EfiUpgradeService upgrades;
    public UpgradeBillingAdvice(AdminStoreService stores,EfiUpgradeService upgrades){this.stores=stores;this.upgrades=upgrades;}
    @ModelAttribute
    public void history(HttpSession session,HttpServletRequest request,Model model){
        if(request.getRequestURI().equals(request.getContextPath()+"/admin/billing"))
            model.addAttribute("upgradeHistory",upgrades.history(stores.requireCurrentStore(session).getStoreId()));
    }
}
