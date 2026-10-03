package br.com.nuvemcustomfields.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class LgpdWebhookService {

    private static final String SUBJECT = "Solicitacao LGPD";

    private final ObjectMapper objectMapper;
    private final SupportService supportService;
    private final StoreErasureRequestService erasureRequests;

    public LgpdWebhookService(
            ObjectMapper objectMapper,
            SupportService supportService,
            StoreErasureRequestService erasureRequests
    ) {
        this.objectMapper = objectMapper;
        this.supportService = supportService;
        this.erasureRequests = erasureRequests;
    }

    public void eraseStore(String rawBody) throws Exception {
        JsonNode payload = objectMapper.readTree(rawBody);
        erasureRequests.receive(requiredStoreId(payload), java.time.Instant.now());
    }

    public void forwardToSupport(String requestType, String rawBody) throws Exception {
        JsonNode payload = objectMapper.readTree(rawBody);
        Long storeId = requiredStoreId(payload);
        String message = "Pedido recebido: %s%n%nO aplicativo nao persiste dados pessoais de compradores nem o conteudo dos pedidos."
                .formatted(requestType);
        supportService.openAutomatedTicket(storeId, SUBJECT, message);
    }

    private Long requiredStoreId(JsonNode payload) {
        JsonNode storeId = payload.path("store_id");
        if (!storeId.canConvertToLong()) {
            throw new IllegalArgumentException("Payload LGPD sem store_id valido.");
        }
        return storeId.longValue();
    }
}
