package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.entity.DlocalGoOperation.State;
import br.com.nuvemcustomfields.service.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.util.Set;

/** Existing admin/backoffice session interceptors protect these routes. */
@Controller
public class DlocalGoOperationsController {
    private final DlocalGoUpgradeService upgrades;
    private final DlocalGoRefundService refunds;
    private final AdminStoreService stores;
    public DlocalGoOperationsController(DlocalGoUpgradeService upgrades,DlocalGoRefundService refunds,AdminStoreService stores) {
        this.upgrades=upgrades;this.refunds=refunds;this.stores=stores;
    }
    @GetMapping("/admin/billing/upgrade/dlocal-go")
    public String quote(@RequestParam PlanType plan,HttpSession session,Model model,HttpServletResponse response,RedirectAttributes flash) {
        response.setHeader("Cache-Control","no-store");var store=stores.requireCurrentStore(session);
        try {model.addAttribute("operation",upgrades.quote(store.getStoreId(),plan));return "admin/billing-upgrade-dlocal-go";}
        catch(RuntimeException ex){flash.addFlashAttribute("error",safe(ex));return "redirect:/admin/billing";}
    }
    @PostMapping("/admin/billing/upgrade/dlocal-go/pay")
    public String pay(@RequestParam String id,HttpSession session,RedirectAttributes flash) {
        var store=stores.requireCurrentStore(session);
        try {String checkout=upgrades.pay(store.getStoreId(),id);if(checkout!=null)return "redirect:"+checkout;}
        catch(RuntimeException ex){flash.addFlashAttribute("error",safe(ex));}
        return "redirect:/admin/billing/upgrade/dlocal-go/processing?id="+upgrades.owned(store.getStoreId(),id).getId();
    }
    @GetMapping("/admin/billing/upgrade/dlocal-go/processing")
    public String processing(@RequestParam String id,HttpSession session,Model model,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");var store=stores.requireCurrentStore(session);upgrades.owned(store.getStoreId(),id);
        try {upgrades.reconcile(id);}catch(RuntimeException ex){model.addAttribute("error",safe(ex));}
        var op=upgrades.owned(store.getStoreId(),id);model.addAttribute("operation",op);
        if(Set.of(State.CREATING,State.PENDING,State.PAID,State.APPLYING).contains(op.getState())) response.setHeader("Refresh","10");
        return "admin/billing-upgrade-dlocal-go";
    }
    @GetMapping("/backoffice/payments/dlocal-go/refunds")
    public String refunds(Model model,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");model.addAttribute("operations",refunds.recent());return "backoffice/dlocal-go-refunds";
    }
    @PostMapping("/backoffice/payments/dlocal-go/refunds/quote")
    public String refundQuote(@RequestParam Long storeId,@RequestParam String invoiceId,@RequestParam BigDecimal amount,RedirectAttributes flash) {
        try{return "redirect:/backoffice/payments/dlocal-go/refunds/"+refunds.quote(storeId,invoiceId,amount).getId();}
        catch(RuntimeException ex){flash.addFlashAttribute("error",safe(ex));return "redirect:/backoffice/payments/dlocal-go/refunds";}
    }
    @GetMapping("/backoffice/payments/dlocal-go/refunds/{id}")
    public String refundDetail(@PathVariable String id,Model model,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");model.addAttribute("operation",refunds.require(id));return "backoffice/dlocal-go-refunds";
    }
    @PostMapping("/backoffice/payments/dlocal-go/refunds/{id}/execute")
    public String refundExecute(@PathVariable String id,RedirectAttributes flash) {
        try{refunds.execute(id);flash.addFlashAttribute("message","Solicitação registrada. O resultado será conciliado com o provedor.");}
        catch(RuntimeException ex){flash.addFlashAttribute("error",safe(ex));}
        return "redirect:/backoffice/payments/dlocal-go/refunds/"+refunds.require(id).getId();
    }
    private static String safe(RuntimeException ex) {
        return ex instanceof IllegalArgumentException?ex.getMessage():"A operação está aguardando confirmação. Não faça outro pagamento; consulte o andamento.";
    }
}
