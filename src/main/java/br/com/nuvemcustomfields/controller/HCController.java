package br.com.nuvemcustomfields.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HCController {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(HCController.class);
    private final String appVersion;

    public HCController(@Value("${app.version:unknown}") final String appVersion) {
        this.appVersion = appVersion;
    }

    @GetMapping("/support/health-check")
    public ResponseEntity<String> healthCheck() {
        LOGGER.info("Health check endpoint called, version={}", appVersion);
        return ResponseEntity.ok("ok - version: " + appVersion);
    }
}
