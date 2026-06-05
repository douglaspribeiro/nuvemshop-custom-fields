package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.NexoSessionTokenService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class AdminEmbeddedController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminEmbeddedController.class);

    private final NuvemshopProperties properties;
    private final StoreRepository storeRepository;
    private final NexoSessionTokenService nexoSessionTokenService;

    public AdminEmbeddedController(
            NuvemshopProperties properties,
            StoreRepository storeRepository,
            NexoSessionTokenService nexoSessionTokenService
    ) {
        this.properties = properties;
        this.storeRepository = storeRepository;
        this.nexoSessionTokenService = nexoSessionTokenService;
    }

    @GetMapping("/admin/embedded")
    public String embedded(HttpSession session, Model model) {
        Object storeId = session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY);
        if (storeId instanceof Long id && storeRepository.findActiveByStoreId(id).isPresent()) {
            LOGGER.info("admin.embedded.session_present store_id={}", id);
            return "redirect:/admin";
        }
        LOGGER.info("admin.embedded.open session_id={}", session.getId());
        model.addAttribute("nuvemshopClientId", properties.clientId());
        return "admin/embedded";
    }

    @PostMapping(
            value = "/admin/nexo/session",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createSession(@RequestBody NexoSessionRequest request, HttpSession session) {
        try {
            Long storeId = nexoSessionTokenService.requireStoreId(request.token());
            return storeRepository.findActiveByStoreId(storeId)
                    .map(store -> {
                        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, store.getStoreId());
                        LOGGER.info("admin.nexo.session.created session_id={} store_id={}", session.getId(), store.getStoreId());
                        return ResponseEntity.ok(Map.<String, Object>of("redirect", "/admin"));
                    })
                    .orElseGet(() -> {
                        LOGGER.warn("admin.nexo.session.store_not_found store_id={}", storeId);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.<String, Object>of("error", "Loja ativa nao encontrada."));
                    });
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("admin.nexo.session.invalid_token message={}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.<String, Object>of("error", ex.getMessage()));
        }
    }

    public record NexoSessionRequest(String token) {
    }
}
