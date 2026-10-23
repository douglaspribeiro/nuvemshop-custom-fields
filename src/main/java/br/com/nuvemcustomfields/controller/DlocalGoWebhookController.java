package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.payment.PaymentGatewayException;
import br.com.nuvemcustomfields.service.DlocalGoWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class DlocalGoWebhookController {
    private final DlocalGoWebhookService service;
    public DlocalGoWebhookController(DlocalGoWebhookService service) { this.service = service; }
    @PostMapping({"/prod/webhooks/dlocal-go", "/webhooks/dlocal-go"})
    public ResponseEntity<Void> receive(@RequestBody String body, @RequestHeader(name="Authorization", required=false) String signature, @RequestParam String reference) {
        try { service.receive(body, signature, reference); }
        catch (PaymentGatewayException | IllegalArgumentException ex) { return ResponseEntity.status(401).build(); }
        return ResponseEntity.ok().build();
    }
}
