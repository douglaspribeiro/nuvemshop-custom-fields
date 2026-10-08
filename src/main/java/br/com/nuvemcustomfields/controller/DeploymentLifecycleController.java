package br.com.nuvemcustomfields.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** No SSE channels in this app; HTTP requests drain at the proxy before stop. */
@RestController
@ConditionalOnProperty(name = "app.deploy.enabled", havingValue = "true")
public class DeploymentLifecycleController {
    @PostMapping("/support/deploy/streams")
    public ResponseEntity<String> streams(HttpServletRequest request, @RequestParam("state") String state) {
        if (!"127.0.0.1".equals(request.getRemoteAddr()) && !"::1".equals(request.getRemoteAddr())
                && !"0:0:0:0:0:0:0:1".equals(request.getRemoteAddr()))
            return ResponseEntity.status(403).body("Local deployment control only");
        if (!"drain".equals(state) && !"resume".equals(state))
            return ResponseEntity.badRequest().body("Unknown stream state");
        return ResponseEntity.ok("ok");
    }
}
