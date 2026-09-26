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
        model.addAttribute("checkout", subscriptions.paddleCheckout(token));
        return "public/paddle-checkout";
    }
}
