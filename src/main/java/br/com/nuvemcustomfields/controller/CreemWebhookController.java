package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.payment.PaymentGatewayException;
import br.com.nuvemcustomfields.service.CreemWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class CreemWebhookController {
    private final CreemWebhookService service;
    public CreemWebhookController(CreemWebhookService service) { this.service = service; }
    @PostMapping({"/prod/webhooks/creem", "/webhooks/creem"})
    public ResponseEntity<Void> receive(@RequestBody String body,
            @RequestHeader(name="creem-signature", required=false) String signature) {
        try { service.receive(body, signature); }
        catch (PaymentGatewayException | IllegalArgumentException ex) { return ResponseEntity.status(401).build(); }
        return ResponseEntity.ok().build();
    }
}
