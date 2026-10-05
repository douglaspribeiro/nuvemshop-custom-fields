package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.EfiGateway;
import br.com.nuvemcustomfields.service.AdminStoreService;
import br.com.nuvemcustomfields.service.EfiUpgradeService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.text.NumberFormat;
import java.util.Locale;

@Controller
public class EfiUpgradeController {
    private final EfiUpgradeService upgrades;
    private final AdminStoreService stores;
    private final EfiGateway efi;
    public EfiUpgradeController(EfiUpgradeService upgrades,AdminStoreService stores,EfiGateway efi){this.upgrades=upgrades;this.stores=stores;this.efi=efi;}

    @GetMapping("/backoffice/payments/upgrades")
    public String adjustments(Model model,HttpServletResponse response){response.setHeader("Cache-Control","no-store");model.addAttribute("adjustments",upgrades.recent());return "backoffice/upgrade-adjustments";}

    @GetMapping("/admin/billing/upgrade/efi")
    public String preview(@RequestParam PlanType plan,@RequestParam(required=false) String couponCode,
            HttpSession session,Model model,HttpServletResponse response,RedirectAttributes flash){
        response.setHeader("Cache-Control","no-store");
        var store=stores.requireCurrentStore(session);
        try{var a=upgrades.quote(store.getStoreId(),plan,couponCode);populate(model,store,a);return "admin/billing-upgrade-efi";}
        catch(RuntimeException ex){
            if(ex instanceof IllegalArgumentException && couponCode!=null && !couponCode.isBlank()){
                try{var a=upgrades.quote(store.getStoreId(),plan,null);populate(model,store,a);
                    model.addAttribute("error",safeError(ex));return "admin/billing-upgrade-efi";
                }catch(RuntimeException ignored){ /* Elegibilidade mudou: volte ao painel. */ }
            }
            flash.addFlashAttribute("error",safeError(ex));return "redirect:/admin/billing";
        }
    }

    @GetMapping("/admin/billing/upgrade/efi/pay")
    public String payment(@RequestParam String id,HttpSession session,Model model,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");
        var store=stores.requireCurrentStore(session);var a=upgrades.owned(store.getStoreId(),id);
        if(a.getState()!=UpgradeAdjustment.State.QUOTED)return processingUrl(id);
        populate(model,store,a);
        model.addAttribute("plan",a.getTargetPlan());model.addAttribute("discounted",false);
        model.addAttribute("formattedAmount",money(a.getDueAmount()));model.addAttribute("formattedRegularAmount",money(a.getRegularAmount()));
        model.addAttribute("payeeCode",efi.payeeCode());model.addAttribute("efiEnvironment",efi.sandbox()?"sandbox":"production");
        return "admin/billing-payment";
    }

    @PostMapping("/admin/billing/upgrade/efi/pay")
    public String pay(@RequestParam String adjustmentId,@RequestParam(required=false) String payerName,
            @RequestParam(required=false) String cpf,@RequestParam(required=false) String payerEmail,
            @RequestParam(required=false) String phone,@RequestParam(required=false) String birth,
            @RequestParam(required=false) String paymentToken,HttpSession session,RedirectAttributes flash){
        var store=stores.requireCurrentStore(session);
        try{
            var a=upgrades.owned(store.getStoreId(),adjustmentId);
            var payer=a.getDueAmount().signum()==0?null:new EfiGateway.EfiPayer(payerName,
                    cpf==null?null:cpf.replaceAll("\\D",""),payerEmail,phone==null?null:phone.replaceAll("\\D",""),birth);
            upgrades.pay(store.getStoreId(),adjustmentId,payer,paymentToken);
        }catch(RuntimeException ex){
            flash.addFlashAttribute("error",safeError(ex));
            var a=upgrades.owned(store.getStoreId(),adjustmentId);
            if(a.getState()==UpgradeAdjustment.State.QUOTED)return "redirect:/admin/billing/upgrade/efi/pay?id="+adjustmentId;
        }
        return processingUrl(adjustmentId);
    }

    @GetMapping("/admin/billing/upgrade/efi/processing")
    public String processing(@RequestParam String id,HttpSession session,Model model,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");
        var store=stores.requireCurrentStore(session);upgrades.owned(store.getStoreId(),id);
        try{upgrades.reconcile(id);}catch(RuntimeException ex){model.addAttribute("error","Estamos confirmando a operação com a Efí. Não faça outro pagamento.");}
        populate(model,store,upgrades.owned(store.getStoreId(),id));return "admin/billing-upgrade-processing";
    }

    @PostMapping({"/prod/webhooks/efi-upgrades","/webhooks/efi-upgrades"})
    @ResponseBody
    public org.springframework.http.ResponseEntity<Void> notification(@RequestParam String notification){
        upgrades.receiveNotification(notification);return org.springframework.http.ResponseEntity.ok().build();
    }

    private void populate(Model model,Store store,UpgradeAdjustment a){
        model.addAttribute("store",store);model.addAttribute("upgradeAdjustment",a);
        model.addAttribute("analyticsProvider","EFI");model.addAttribute("analyticsCurrency","BRL");
        model.addAttribute("analyticsSandbox",a.getEnvironment()==PaymentEnvironment.SANDBOX);
        model.addAttribute("targetPrice",money(a.getRegularAmount()));model.addAttribute("currentPrice",money(a.getSourceAmount()));
        model.addAttribute("proratedPrice",money(a.getTargetProrated()));model.addAttribute("discountPrice",money(a.getDiscountAmount()));
        model.addAttribute("creditPrice",money(a.getCreditAmount()));model.addAttribute("duePrice",money(a.getDueAmount()));
    }
    private static String money(java.math.BigDecimal value){return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value);}
    private static String processingUrl(String id){return "redirect:/admin/billing/upgrade/efi/processing?id="+id;}
    private static String safeError(RuntimeException ex){return ex instanceof IllegalArgumentException?ex.getMessage():"A operação está aguardando confirmação. Não pague novamente; consulte o andamento ou fale com o suporte.";}
}
