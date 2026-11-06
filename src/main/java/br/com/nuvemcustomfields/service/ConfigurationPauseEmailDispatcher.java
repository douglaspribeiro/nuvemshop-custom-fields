package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.properties.SesProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static br.com.nuvemcustomfields.entity.ConfigurationPauseEmail.State.*;

@Service
public class ConfigurationPauseEmailDispatcher {
    private final ConfigurationPauseEmailRepository queue;
    private final StoreRepository stores;
    private final PersonalizationRuleRepository rules;
    private final ConfigurationPlanPolicy policy;
    private final StoreAdminEmailService emails;
    private final PaymentSubscriptionService subscriptions;
    private final SesProperties ses;
    private final String baseUrl;
    private final TransactionTemplate tx;

    public ConfigurationPauseEmailDispatcher(ConfigurationPauseEmailRepository queue, StoreRepository stores,
            PersonalizationRuleRepository rules, ConfigurationPlanPolicy policy, StoreAdminEmailService emails,
            SesProperties ses, PlatformTransactionManager manager, PaymentSubscriptionService subscriptions,
            @Value("${nuvemshop.app-base-url}") String baseUrl) {
        this.queue=queue; this.stores=stores; this.rules=rules; this.policy=policy; this.emails=emails; this.ses=ses;
        this.subscriptions=subscriptions;
        this.baseUrl=baseUrl.replaceAll("/+$", ""); tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Notice(String subject, String body, String action) { }

    @Scheduled(fixedDelayString = "${personalization.pause-email-delay-ms:15000}")
    public void dispatch() {
        if (!ses.configured()) return; // Keep pending until SMTP is configured.
        for (int n=0; n<10; n++) {
            var row=tx.execute(status -> {
                var due=queue.findPending(PENDING, PageRequest.of(0,1));
                if (due.isEmpty()) return null;
                var first=due.getFirst(); first.claim(); return queue.saveAndFlush(first);
            });
            if (row==null) return;
            try {
                var notice=tx.execute(status -> preview(row));
                if (notice==null) { finish(row.getId(), SKIPPED); continue; }
                String path=subscriptions.reactivationPlan(row.getStoreId()).isPresent()
                        ? "/admin/billing/reactivate?storeId="+row.getStoreId() : "/admin/billing";
                emails.sendAction(row.getStoreId(), notice.subject(), notice.body(), notice.action(), baseUrl+path);
                finish(row.getId(), SENT);
            } catch (IllegalArgumentException ex) {
                finish(row.getId(), FAILED); // Recipient/link validation failed before SMTP; needs correction.
            } catch (RuntimeException ex) {
                // An uncertain SMTP result must never cause an automatic duplicate.
                finish(row.getId(), REVIEW);
                org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                        "personalization.pause_email.review store_id={} notice_id={} type={}",
                        row.getStoreId(), row.getId(), ex.getClass().getSimpleName());
            }
        }
    }

    Notice preview(ConfigurationPauseEmail event) {
        var store=stores.findActiveByStoreId(event.getStoreId()).orElse(null);
        if (store==null || store.getEffectivePlan().isBillable()) return null;
        var configured=rules.findWithFieldsByStoreIdOrderByCreatedAtAscIdAsc(store.getStoreId());
        var allowed=policy.evaluate(store, configured).allowedProducts();
        var original=new HashSet<>(Arrays.asList(event.getPausedProducts().split(",")));
        long paused=configured.stream().filter(r -> r.isEnabled() && r.isPlanPaused()
                && r.getPlanPausedAt()!=null && !r.getPlanPausedAt().isAfter(event.getCreatedAt())
                && !allowed.contains(r.getProductId()) && original.contains(r.getProductId().toString())).count();
        if (paused==0) return null; // Recovered, deleted or manually disabled since the transition.
        var retained=configured.stream().filter(r -> allowed.contains(r.getProductId())).limit(10).map(r -> r.getProductId().toString())
                .collect(java.util.stream.Collectors.joining(", "));
        boolean es=!"BR".equalsIgnoreCase(store.getStoreCountryCode());
        return notice(es, paused, retained, store.getEffectivePlan().isBillable());
    }

    static Notice notice(boolean es, long paused, String retained, boolean paid) {
        String state=paid ? (es ? "los límites del plan vigente" : "os limites do plano vigente")
                : (es ? "los límites del plan Free, porque tu tienda ya no tiene un plan pago vigente"
                : "os limites do plano Free, pois sua loja não tem mais um plano pago válido");
        String kept=retained.isEmpty() ? (es ? "Ninguna configuración cumple actualmente los límites del plan."
                : "Nenhuma configuração atende atualmente aos limites do plano.")
                : (es ? "Productos cuya personalización sigue activa (ID): " : "Produtos cuja personalização continua ativa (ID): ")+retained+".";
        String body=es ? "Pausamos la personalización de "+paused+" producto(s) para respetar "+state+".\n\n"+kept
                +"\n\nTus configuraciones y los datos personalizados de tus pedidos siguen guardados. Pausar no significa eliminar."
                +" Las configuraciones pausadas ya no se muestran para nuevas personalizaciones."
                +"\n\nReactiva tu suscripción en el panel. Al confirmarse el pago, las configuraciones pausadas por este control volverán a activarse dentro de los límites del plan contratado. Las configuraciones desactivadas manualmente seguirán desactivadas."
                : "Pausamos a personalização de "+paused+" produto(s) para respeitar "+state+".\n\n"+kept
                +"\n\nSuas configurações e os dados personalizados dos pedidos continuam salvos. Pausar não significa excluir."
                +" As configurações pausadas deixam de aparecer para novas personalizações."
                +"\n\nReative sua assinatura pelo painel. Após a confirmação do pagamento, as configurações pausadas por este controle voltarão a ficar ativas dentro dos limites do plano contratado. Configurações desativadas manualmente continuarão desativadas.";
        return new Notice(es ? "Tus configuraciones de personalización fueron pausadas" : "Suas configurações de personalização foram pausadas",
                body, es ? "Reactivar suscripción" : "Reativar assinatura");
    }

    private void finish(Long id, ConfigurationPauseEmail.State state) {
        tx.executeWithoutResult(status -> queue.findById(id).ifPresent(row -> { row.finish(state); queue.save(row); }));
    }
}
