package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.dto.WebhookPayload;
import br.com.nuvemcustomfields.service.WebhookLifecycleService;
import br.com.nuvemcustomfields.service.WebhookSecurityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookController {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookController.class);

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
        LOGGER.info("webhook.receive.start hmac_present={} body_size={}", hmac != null && !hmac.isBlank(), body.length());
        if (!securityService.isValid(body, hmac)) {
            LOGGER.warn("webhook.receive.invalid_signature hmac_present={}", hmac != null && !hmac.isBlank());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        WebhookPayload payload = objectMapper.readValue(body, WebhookPayload.class);
        LOGGER.info("webhook.receive.valid event={} store_id={}", payload.event(), payload.storeId());
        lifecycleService.handle(payload);
        LOGGER.info("webhook.receive.done event={} store_id={}", payload.event(), payload.storeId());
        return ResponseEntity.noContent().build();
    }
}
