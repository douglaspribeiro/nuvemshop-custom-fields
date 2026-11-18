package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.repository.PaymentSubscriptionRepository;
import br.com.nuvemcustomfields.service.PaymentSubscriptionService;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class EfiPixCallbackController {
    private final PaymentSubscriptionRepository subscriptions;
    private final PaymentSubscriptionService billing;
    private final String secret;

    public EfiPixCallbackController(PaymentSubscriptionRepository subscriptions, PaymentSubscriptionService billing,
            @Value("${payments.efi.open-finance.webhook-secret:}") String secret) {
        this.subscriptions=subscriptions; this.billing=billing; this.secret=secret;
    }

    @PostMapping("/prod/webhooks/efi-open-finance")
    public ResponseEntity<Void> receive(@RequestParam(defaultValue="") String hmac,@RequestBody JsonNode event) {
        if (secret.isBlank() || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),hmac.getBytes(StandardCharsets.UTF_8)))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String id=event.path("idProprio").asText("");
        if (!id.isBlank()) subscriptions.findByProviderSubscriptionId(id)
                .filter(s -> s.getProvider()==PaymentProviderType.EFI_PIX)
                .ifPresent(s -> billing.reconcile(s.getStoreId()));
        return ResponseEntity.noContent().build();
    }
}
