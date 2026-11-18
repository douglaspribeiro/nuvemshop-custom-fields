package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.entity.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EfiScheduledPixGateway implements PaymentGateway {
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private final EfiRecurringPixClient client;
    private final EfiRecurringPixClient.Settings settings;
    private final JdbcTemplate db;
    private final EfiGateway card;

    public EfiScheduledPixGateway(EfiRecurringPixClient client, EfiRecurringPixClient.Settings settings, JdbcTemplate db, EfiGateway card) {
        this.client = client;
        this.settings = settings;
        this.db = db;
        this.card = card;
    }
    public PaymentProviderType provider() { return PaymentProviderType.EFI_PIX; }
    public boolean configured() { return settings.ready(); }
    public PaymentEnvironment environment() { return Boolean.TRUE.equals(settings.sandbox()) ? PaymentEnvironment.SANDBOX : PaymentEnvironment.PRODUCTION; }
    public boolean supports(Store store) { return card.supports(store) && environment() == card.environment(); }
    public BigDecimal amount(PlanType plan) { return card.amount(plan); }
    public GatewayCheckout createCheckout(Store store, PlanType plan, String reference, String returnUrl) {
        throw new IllegalArgumentException("Selecione o banco para autorizar o Pix agendado.");
    }

    public GatewaySubscription getSubscription(String id) {
        var record = db.queryForMap("select payment_id, amount, first_debit from efi_pix_recurrences where request_id=? and environment=?", id, environment().name());
        LocalDate created = ((java.sql.Date)record.get("first_debit")).toLocalDate().minusDays(1);
        for (JsonNode remote : client.list(created.minusDays(1), created.plusDays(1))) {
            if (!id.equals(remote.path("idProprio").asText())) continue;
            String paymentId = remote.path("identificadorPagamento").asText("");
            BigDecimal amount = new BigDecimal(remote.required("valor").asText());
            if (paymentId.isBlank() || (record.get("payment_id") != null && !paymentId.equals(record.get("payment_id")))
                    || amount.compareTo((BigDecimal)record.get("amount")) != 0)
                throw new PaymentGatewayException("Dados da recorrência Pix divergentes.");
            String status = remote.required("status").asText();
            LocalDate latestPaid = null;
            boolean failed = false;
            for (JsonNode debit : remote.path("recorrencias")) {
                String endId = debit.path("endToEndId").asText("");
                if (endId.isBlank()) continue;
                LocalDate date = LocalDate.parse(debit.required("dataOperacao").asText());
                String state = debit.required("status").asText();
                for (JsonNode refund : debit.path("devolucoes"))
                    if ("aceito".equals(refund.path("status").asText())) state = "devolvido";
                failed |= Set.of("rejeitado","erro","devolvido").contains(state) && !date.isAfter(LocalDate.now(ZONE));
                db.update("insert into efi_pix_debits(request_id,end_to_end_id,operation_date,provider_status) values(?,?,?,?) on duplicate key update provider_status=values(provider_status)",id,endId,date,state);
                if ("aceito".equals(state) && !date.isAfter(LocalDate.now(ZONE)) && (latestPaid == null || date.isAfter(latestPaid))) latestPaid = date;
            }
            db.update("update efi_pix_recurrences set payment_id=?,status=? where request_id=?",paymentId,status,id);
            Instant end = latestPaid == null ? null : latestPaid.plusMonths(1).atStartOfDay(ZONE).toInstant();
            boolean terminal = Set.of("cancelada","concluída","concluida","expirada").contains(status);
            if ("ativa".equals(status) && latestPaid == null && !failed) {
                return new GatewaySubscription(id,null,id,"authorized","BRL",amount,
                        created.plusDays(1).atStartOfDay(ZONE).toInstant());
            }
            String mapped = terminal ? "canceled" : end == null ? (failed ? "paused" : "pending") : Instant.now().isBefore(end) ? "active" : "paused";
            return new GatewaySubscription(id,null,id,mapped,"BRL",amount,end);
        }
        throw new PaymentGatewayException("Aguardando confirmação da recorrência Pix pela Efí.");
    }
    public Optional<GatewayInvoice> getLatestInvoice(String id) {
        return db.query("select end_to_end_id from efi_pix_debits where request_id=? and provider_status='aceito' and operation_date<=? order by operation_date desc limit 1",
                (rs,i) -> new GatewayInvoice(rs.getString(1),id,rs.getString(1),"paid"),id,LocalDate.now(ZONE)).stream().findFirst();
    }
    public void cancel(String id) {
        String remote = db.queryForObject("select payment_id from efi_pix_recurrences where request_id=? and environment=?", String.class,id,environment().name());
        if (remote == null) throw new PaymentGatewayException("Aguardando identificação da recorrência Pix.");
        client.cancel(remote);
    }
    public void cancelCheckout(String id) { cancel(id); }
    public GatewayInvoice getInvoice(String id) { throw new UnsupportedOperationException(); }
    public GatewayNotification verifyNotification(String body,String signature,String requestId,String dataId) { throw new UnsupportedOperationException(); }
}
