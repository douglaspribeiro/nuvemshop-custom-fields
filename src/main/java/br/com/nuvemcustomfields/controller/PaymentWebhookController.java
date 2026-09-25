package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.payment.PaymentGatewayException;
import br.com.nuvemcustomfields.service.PaymentWebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentWebhookController {
    private final PaymentWebhookService service;

    public PaymentWebhookController(PaymentWebhookService service) {
        this.service = service;
    }

    @PostMapping({"/prod/webhooks/mercado-pago2", "/webhooks/mercado-pago2"})
    public ResponseEntity<Void> mercadoPago(
            @RequestHeader(name = "x-signature", required = false) String signature,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestBody(required = false) String body
    ) {
        try {
            service.receiveMercadoPago(body, signature, requestId, dataId);
            return ResponseEntity.noContent().build();
        } catch (PaymentGatewayException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("Assinatura Mercado Pago")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            throw ex;
        }
    }

    // O proxy pode preservar ou remover /prod. Mantemos a rota antiga para assinaturas existentes.
    @PostMapping({"/prod/webhooks/efi3", "/webhooks/efi3", "/prod/webhooks/efi", "/webhooks/efi"})
    public ResponseEntity<Void> efi(@RequestParam(name = "notification", required = false) String notification) {
        if (notification == null || notification.isBlank()) return ResponseEntity.badRequest().build();
        service.receiveEfi(notification);
        return ResponseEntity.ok().build();
    }
}
