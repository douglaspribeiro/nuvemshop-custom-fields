package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.payment.PaymentGatewayException;
import br.com.nuvemcustomfields.service.StripeWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class StripeWebhookController {
    private final StripeWebhookService service;
    public StripeWebhookController(StripeWebhookService service) { this.service = service; }
    @PostMapping({"/prod/webhooks/stripe", "/webhooks/stripe"})
    public ResponseEntity<Void> receive(@RequestBody String body, @RequestHeader(name="Stripe-Signature", required=false) String signature) {
        try { service.receive(body, signature); }
        catch (PaymentGatewayException | IllegalArgumentException ex) { return ResponseEntity.status(401).build(); }
        return ResponseEntity.ok().build();
    }
}
