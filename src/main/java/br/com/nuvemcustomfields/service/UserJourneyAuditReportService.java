package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.entity.UserJourneyAudit;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserJourneyAuditReportService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss z")
            .withZone(ZoneId.of("America/Sao_Paulo"));
    private static final Map<String, String> OUTCOMES = Map.of(
            "STARTED", "Sem conclusão registrada", "COMPLETED", "Requisição concluída",
            "REJECTED", "Ação recusada", "ERROR", "Falha", "ACCESS_REQUIRED", "Acesso necessário");
    private static final Map<String, String> ACTORS = Map.of("MERCHANT", "Lojista", "OPERATOR", "Operador");
    private static final Map<String, String> ACTIONS = Map.ofEntries(
            Map.entry("AuthController.callback", "Autorização da loja"),
            Map.entry("AdminEmbeddedController.embedded", "Abertura do aplicativo"),
            Map.entry("AdminEmbeddedController.createSession", "Entrada pelo painel da Nuvemshop"),
            Map.entry("AdminController.index", "Acesso ao início"), Map.entry("AdminController.help", "Consulta à ajuda"),
            Map.entry("AdminController.dashboard", "Consulta ao painel"), Map.entry("AdminController.products", "Consulta aos produtos"),
            Map.entry("AdminController.onboarding", "Acesso ao onboarding"), Map.entry("AdminController.applyTemplate", "Aplicação de template"),
            Map.entry("AdminController.fields", "Abertura do editor de campos"), Map.entry("AdminController.addField", "Criação de campo"),
            Map.entry("AdminController.updateField", "Edição de campo"), Map.entry("AdminController.deleteField", "Exclusão de campo"),
            Map.entry("AdminController.deleteRule", "Remoção da configuração do produto"),
            Map.entry("AdminController.styleSettings", "Consulta à aparência"), Map.entry("AdminController.updateStyleSettings", "Alteração da aparência"),
            Map.entry("AdminController.billing", "Consulta aos planos e assinatura"), Map.entry("AdminController.subscribe", "Solicitação de assinatura"),
            Map.entry("AdminController.upgradePage", "Consulta ao upgrade"), Map.entry("AdminController.upgrade", "Solicitação de upgrade"),
            Map.entry("AdminController.efiPaymentPage", "Abertura do pagamento"), Map.entry("AdminController.payWithEfi", "Envio de pagamento"),
            Map.entry("AdminController.paymentProcessing", "Consulta ao processamento do pagamento"), Map.entry("AdminController.billingReturn", "Retorno do pagamento"),
            Map.entry("AdminController.cancellationPage", "Consulta ao cancelamento"), Map.entry("AdminController.cancelSubscription", "Solicitação de cancelamento"),
            Map.entry("OptionImageController.upload", "Envio de imagem para opções"),
            Map.entry("StoreTemplateController.library", "Consulta à biblioteca de templates"),
            Map.entry("StoreTemplateController.create", "Criação de template"),
            Map.entry("StoreTemplateController.update", "Edição de template"),
            Map.entry("StoreTemplateController.duplicate", "Duplicação de template"),
            Map.entry("StoreTemplateController.saveProduct", "Produto salvo como template"),
            Map.entry("StoreTemplateController.apply", "Aplicação de template a um produto"),
            Map.entry("StoreTemplateController.delete", "Exclusão de template"),
            Map.entry("FeatureRequestController.form", "Consulta às sugestões"), Map.entry("FeatureRequestController.submit", "Envio de sugestão"),
            Map.entry("PublicPagesController.support", "Acesso ao suporte"), Map.entry("PublicPagesController.supportAlias", "Acesso ao suporte"),
            Map.entry("PublicPagesController.openTicket", "Abertura de chamado"), Map.entry("PublicPagesController.ticket", "Consulta ao chamado"),
            Map.entry("PublicPagesController.reply", "Envio de mensagem ao suporte"));
    private final UserJourneyAuditRepository audits;
    private final StoreRepository stores;
    private final ObjectMapper mapper;
    public UserJourneyAuditReportService(UserJourneyAuditRepository audits, StoreRepository stores, ObjectMapper mapper) {
        this.audits=audits; this.stores=stores; this.mapper=mapper;
    }
    public Map<String, String> outcomes() { return OUTCOMES; }
    public Map<String, String> actors() { return ACTORS; }

    @Transactional(readOnly = true)
    public Slice<Entry> report(Long storeId, String outcome, String actor, int page) {
        if ((storeId != null && storeId <= 0) || page < 0 || page > 10000
                || (outcome != null && !OUTCOMES.containsKey(outcome))
                || (actor != null && !ACTORS.containsKey(actor)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro de auditoria inválido.");
        var events = audits.search(storeId, outcome, actor, PageRequest.of(page, 50));
        var ids = events.getContent().stream().map(a -> a.storeId).distinct().toList();
        var names = ids.isEmpty() ? Map.<Long,String>of() : stores.findByStoreIdIn(ids).stream()
                .collect(Collectors.toMap(Store::getStoreId, s -> s.getStoreName() == null ? "Loja sem nome" : s.getStoreName()));
        return events.map(a -> new Entry(a.id, a.storeId, names.getOrDefault(a.storeId, "Loja sem cadastro"),
                date(a.occurredAt), date(a.completedAt), ACTIONS.getOrDefault(a.action, "Ação registrada"), a.action,
                OUTCOMES.getOrDefault(a.outcome, "Resultado não informado"), a.outcome,
                ACTORS.getOrDefault(a.actor, "Autor não informado"), a.journeyId, a.method, a.route,
                a.productId, a.fieldId, a.selectedPlan, a.httpStatus,
                summary(a.beforeState), summary(a.afterState), pretty(a.beforeState), pretty(a.afterState)));
    }
    private static String date(Instant value) { return value == null ? "Não registrado" : TIME.format(value); }
    private String summary(String state) {
        if (state == null || state.isBlank()) return "Estado não registrado";
        try {
            var node = mapper.readTree(state);
            if (node.has("exists")) {
                if (!node.path("exists").asBoolean()) return "Produto sem configuração";
                return (node.path("enabled").asBoolean() ? "Produto habilitado" : "Produto desabilitado")
                        + " · " + quantity(node.path("fields").size(), "campo", "campos");
            }
            if (node.has("products") && node.has("fields"))
                return quantity(node.path("products").asLong(), "produto", "produtos") + " · "
                        + quantity(node.path("fields").asLong(), "campo", "campos")
                        + (node.has("templates")?" · "+quantity(node.path("templates").asLong(),"template","templates"):"");
        } catch (Exception ignored) { }
        return "Consulte os dados registrados";
    }
    private static String quantity(long value, String singular, String plural) { return value + " " + (value == 1 ? singular : plural); }
    private String pretty(String state) {
        if (state == null || state.isBlank()) return "Estado não registrado";
        if (state.length() > 16000) return state.substring(0,16000) + "\n[exibição limitada; registro completo preservado no banco]";
        try { return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(state)); }
        catch (Exception ignored) { return state; }
    }
    public record Entry(Long id, Long storeId, String storeName, String occurredAt, String completedAt,
            String action, String technicalAction, String outcomeLabel, String outcome, String actor, String journey,
            String method, String route, Long productId, Long fieldId, String selectedPlan, Integer httpStatus,
            String beforeSummary, String afterSummary, String beforeState, String afterState) { }
}
