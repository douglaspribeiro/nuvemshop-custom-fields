package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.dto.WebhookPayload;
import br.com.nuvemcustomfields.service.WebhookLifecycleService;
import br.com.nuvemcustomfields.service.WebhookSecurityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookController {

    private final ObjectMapper objectMapper;
    private final WebhookSecurityService securityService;
    private final WebhookLifecycleService lifecycleService;

    public WebhookController(
            ObjectMapper objectMapper,
            WebhookSecurityService securityService,
            WebhookLifecycleService lifecycleService
    ) {
        this.objectMapper = objectMapper;
        this.securityService = securityService;
        this.lifecycleService = lifecycleService;
    }

    @PostMapping("/webhooks/nuvemshop")
    public ResponseEntity<Void> receive(
            @RequestHeader(name = "x-linkedstore-hmac-sha256", required = false) String hmac,
            @RequestBody String body
    ) throws Exception {
        if (!securityService.isValid(body, hmac)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        lifecycleService.handle(objectMapper.readValue(body, WebhookPayload.class));
        return ResponseEntity.noContent().build();
    }
}
