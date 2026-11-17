package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.service.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;

@Controller
@RequestMapping("/admin/billing/pay/dodo")
public class DodoPaymentController {
    private final AdminStoreService stores;
    private final DodoCouponService coupons;
    private final PaymentSubscriptionService payments;
    public DodoPaymentController(AdminStoreService stores,DodoCouponService coupons,PaymentSubscriptionService payments) {
        this.stores=stores;this.coupons=coupons;this.payments=payments;
    }
    @GetMapping
    public String review(@RequestParam PlanType plan,@RequestParam(required=false) String couponCode,
            HttpSession session,HttpServletResponse response,Model model,RedirectAttributes flash) {
        response.setHeader("Cache-Control","no-store");
        var store=stores.requireCurrentStore(session);
        try {
            coupons.gateway(store);
            var pending=payments.find(store.getStoreId()).orElse(null);
            if (pending!=null && pending.getProvider().isDodo() && pending.getStatus()==PaymentSubscriptionStatus.PENDING) {
                model.addAttribute("pending",true);model.addAttribute("resumeUrl",pending.getCheckoutUrl());
                model.addAttribute("plan",pending.getPlan());
                return "admin/billing-dodo-payment";
            }
            var quote=coupons.quote(store,plan,null);
            if (couponCode!=null && !couponCode.isBlank()) {
                try {quote=coupons.quote(store,plan,couponCode);}
                catch (IllegalArgumentException | br.com.nuvemcustomfields.payment.PaymentGatewayException ex) {
                    model.addAttribute("couponError",ex.getMessage());
                }
            }
            model.addAttribute("plan",plan);model.addAttribute("quote",quote);model.addAttribute("enteredCouponCode",couponCode==null || couponCode.isBlank()?quote.code():couponCode);
            model.addAttribute("firstPrice",format(quote.first(),quote.currency()));
            model.addAttribute("regularPrice",format(quote.regular(),quote.currency()));
            return "admin/billing-dodo-payment";
        } catch (RuntimeException ex) {
            flash.addFlashAttribute("error",ex.getMessage());return "redirect:/admin/billing";
        }
    }
    @PostMapping
    public String pay(@RequestParam PlanType plan,@RequestParam(required=false) String couponCode,
            @RequestParam(required=false) BigDecimal quotedFirstAmount,@RequestParam(required=false) BigDecimal quotedRegularAmount,
            HttpSession session,RedirectAttributes flash) {
        var store=stores.requireCurrentStore(session);
        try {return "redirect:"+payments.startDodoCheckout(store.getStoreId(),plan,couponCode,quotedFirstAmount,quotedRegularAmount);}
        catch (RuntimeException ex) {
            flash.addFlashAttribute("error",ex.getMessage());flash.addAttribute("plan",plan.name());
            if (couponCode!=null && !couponCode.isBlank()) flash.addAttribute("couponCode",couponCode);
            return "redirect:/admin/billing/pay/dodo";
        }
    }
    private static String format(BigDecimal amount,String currency) {
        var format=NumberFormat.getNumberInstance(org.springframework.context.i18n.LocaleContextHolder.getLocale());
        int digits=Currency.getInstance(currency).getDefaultFractionDigits();
        format.setMinimumFractionDigits(digits);format.setMaximumFractionDigits(digits);
        return currency+" "+format.format(amount);
    }
}
