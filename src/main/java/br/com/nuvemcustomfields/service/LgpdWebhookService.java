package br.com.nuvemcustomfields.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class LgpdWebhookService {

    private static final String SUBJECT = "Solicitacao LGPD";

    private final ObjectMapper objectMapper;
    private final SupportService supportService;

    public LgpdWebhookService(ObjectMapper objectMapper, SupportService supportService) {
        this.objectMapper = objectMapper;
        this.supportService = supportService;
    }

    public void forwardToSupport(String requestType, String rawBody) throws Exception {
        JsonNode payload = objectMapper.readTree(rawBody);
        Long storeId = requiredStoreId(payload);
        String message = """
                Pedido recebido: %s

                Dados enviados pela Nuvemshop:
                %s
                """.formatted(requestType, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
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
