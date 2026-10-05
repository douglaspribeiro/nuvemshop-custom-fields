package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.PaymentSubscriptionService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PaddleCheckoutController {
    private final PaymentSubscriptionService subscriptions;
    public PaddleCheckoutController(PaymentSubscriptionService subscriptions) { this.subscriptions = subscriptions; }

    @GetMapping("/checkout/paddle")
    public String checkout(@RequestParam String token, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var checkout = subscriptions.paddleCheckout(token);
        model.addAttribute("checkout", checkout);
        model.addAttribute("analyticsPlan", java.util.Arrays.stream(br.com.nuvemcustomfields.entity.PlanType.values())
                .filter(plan -> plan.getDisplayName().equals(checkout.planName())).map(Enum::name).findFirst().orElse(null));
        model.addAttribute("analyticsProvider", "PADDLE");
        model.addAttribute("analyticsCurrency", checkout.currency());
        model.addAttribute("amount", checkout.amount());
        model.addAttribute("analyticsSandbox", checkout.sandbox());
        return "public/paddle-checkout";
    }
}
