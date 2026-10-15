package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.payment.PaymentGatewayException;
import br.com.nuvemcustomfields.service.DodoLiveWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class DodoLiveWebhookController {
    private final DodoLiveWebhookService service;
    public DodoLiveWebhookController(DodoLiveWebhookService service) { this.service = service; }
    @PostMapping({"/prod/webhooks/dodo", "/webhooks/dodo"})
    public ResponseEntity<Void> receive(@RequestBody String body,
            @RequestHeader(name = "webhook-signature", required = false) String signature,
            @RequestHeader(name = "webhook-id", required = false) String id,
            @RequestHeader(name = "webhook-timestamp", required = false) String timestamp) {
        try { service.receive(body, signature, id, timestamp); }
        catch (PaymentGatewayException | IllegalArgumentException ex) { return ResponseEntity.status(401).build(); }
        return ResponseEntity.ok().build();
    }
}
