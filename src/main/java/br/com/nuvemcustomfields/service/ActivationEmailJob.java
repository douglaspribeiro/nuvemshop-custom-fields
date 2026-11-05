package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.ActivationEmailOutbox;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.SesEventsProperties;
import br.com.nuvemcustomfields.repository.ActivationEmailOutboxRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.PersonalizationFieldHistoryRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** NC-39 D3/D7/D11 activation sequence. Legacy D15 is paused pending the free-trial decision.
 * Disabled and simulation-only by default. */
@Service
public class ActivationEmailJob {
    private static final Logger log = LoggerFactory.getLogger(ActivationEmailJob.class);
    private static final String VERSION = "nc39-v1";
    private final StoreRepository stores;
    private final PersonalizationRuleRepository rules;
    private final ActivationEmailOutboxRepository outbox;
    private final StoreAdminEmailService mail;
    private final TransactionTemplate tx;
    private final PersonalizationFieldHistoryRepository history;
    private final ActivationEmailProtectionService protection;
    private final SesEventsProperties sesEvents;
    @Value("${activation.email.enabled:false}") private boolean enabled;
    @Value("${activation.email.dry-run:true}") private boolean dryRun;
    @Value("${activation.email.allowlist:}") private String allowlistText;
    @Value("${activation.email.start-at:}") private String startAtText;
    @Value("${activation.email.suppression-ready:false}") private boolean suppressionReady;

    public ActivationEmailJob(StoreRepository stores, PersonalizationRuleRepository rules,
            ActivationEmailOutboxRepository outbox, StoreAdminEmailService mail, PlatformTransactionManager manager,
            PersonalizationFieldHistoryRepository history, ActivationEmailProtectionService protection,
            SesEventsProperties sesEvents) {
        this.stores = stores; this.rules = rules; this.outbox = outbox; this.mail = mail;
        this.tx = new TransactionTemplate(manager);
        this.history = history; this.protection = protection; this.sesEvents = sesEvents;
        this.tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${activation.email.schedule-delay-ms:60000}")
    public void schedule() {
        scheduleAt(Instant.now());
    }

    void scheduleAt(Instant now) {
        if (!enabled) { log.debug("nc39.schedule.skipped reason=disabled"); return; }
        Instant startAt = startAt();
        if (startAt == null) return;
        Set<Long> allowlist = parseAllowlist();
        log.debug("nc39.schedule.started dry_run={} allowlisted_stores={}", dryRun, allowlist.size());
        for (Store candidate : stores.findAllActive()) {
            if (!allowlist.contains(candidate.getStoreId())) continue;
            try { tx.executeWithoutResult(status -> scheduleStore(candidate.getStoreId(), startAt, now)); }
            catch (RuntimeException ex) { log.warn("nc39.schedule.failed store_id={} type={}", candidate.getStoreId(), ex.getClass().getSimpleName()); }
        }
        dispatchDue(now);
    }

    private void scheduleStore(Long storeId, Instant startAt, Instant now) {
        // Serializes concurrent schedulers before checking the unique installation/step key.
        Store store = stores.findActiveByStoreIdForUpdate(storeId).orElse(null);
        if (store == null) return;
        Instant installed = store.getInstalledAt();
        if (installed == null || installed.isBefore(startAt) || store.isErasurePending()) return;
        String blocked = protection.reason(store.getStoreId());
        if (blocked != null) { suppressPending(store, blocked); return; }
        if (configured(store.getStoreId())) {
            if (!outbox.existsByStoreIdAndStep(store.getStoreId(), "ACTIVATED")) {
                var activated = new ActivationEmailOutbox(store.getStoreId(), installed, "ACTIVATED", now, VERSION);
                activated.activated();
                outbox.save(activated);
                log.info("nc39.activation.detected store_id={} event_id={}", store.getStoreId(), activated.getId());
            }
            suppressPending(store, "first_activation");
            return;
        }
        if (outbox.existsByStoreIdAndStep(store.getStoreId(), "ACTIVATED")) {
            suppressPending(store, "first_activation"); return;
        }
        enqueueDue(store, installed, now, "D3", Duration.ofHours(72));
        enqueueDue(store, installed, now, "D7", Duration.ofHours(168));
        enqueueDue(store, installed, now, "D11", Duration.ofHours(264));
    }

    private void suppressPending(Store store, String reason) {
        outbox.findByStoreIdAndInstalledAtAndState(store.getStoreId(), store.getInstalledAt(), ActivationEmailOutbox.State.PENDING)
                .forEach(event -> suppress(event, reason));
    }

    private void enqueueDue(Store store, Instant installed, Instant now, String step, Duration delay) {
        Instant due = installed.plus(delay);
        if (due.isAfter(now) || outbox.existsByStoreIdAndInstalledAtAndStep(store.getStoreId(), installed, step)) return;
        var event = new ActivationEmailOutbox(store.getStoreId(), installed, step, due, VERSION);
        if (now.isAfter(due.plus(Duration.ofHours(24)))) suppress(event, "stage_expired");
        outbox.save(event);
        log.info("nc39.email.queued store_id={} event_id={} step={} due_at={} state={}",
                store.getStoreId(), event.getId(), step, due, event.getState());
    }

    private boolean configured(Long storeId) {
        // Onboarding stops after the first saved configuration, even if later paused or deleted.
        return rules.hasConfiguredFields(storeId) || history.existsByStoreId(storeId);
    }

    public void dispatchDue(Instant now) {
        if (!enabled || startAt() == null) return;
        for (var candidate : outbox.findDue(now, PageRequest.of(0, 50))) {
            try { dispatch(candidate.getId(), now); }
            catch (RuntimeException ex) { log.warn("nc39.dispatch.failed event_id={} type={}", candidate.getId(), ex.getClass().getSimpleName()); }
        }
    }

    public void dispatch(Long id, Instant now) {
        Instant startAt = startAt();
        if (!enabled || startAt == null) return;
        Delivery delivery = tx.execute(status -> claim(id, now, startAt));
        if (delivery == null) return;
        log.info("nc39.email.claimed store_id={} event_id={} step={} attempt={}",
                delivery.storeId(), id, delivery.step(), delivery.attempt());
        try {
            // Feedback committed after the claim still cancels delivery before SMTP begins.
            String blocked = protection.reason(delivery.storeId());
            if (blocked != null) { finish(id, event -> suppress(event, blocked)); return; }
            mail.sendActivation(delivery.storeId(), id, delivery.recipient(), delivery.copy().subject(),
                    delivery.copy().body(), delivery.unsubscribeUrl(), sesEvents.configurationSet());
            finish(id, event -> event.sent(now));
            log.info("nc39.email.sent store_id={} event_id={}", delivery.storeId(), id);
        } catch (IllegalArgumentException ex) {
            // Recipient/configuration validation failed before SMTP. Bounded retry is safe.
            finish(id, event -> event.failed(now));
            log.warn("nc39.email.validation_failed store_id={} event_id={} type={}", delivery.storeId(), id, ex.getClass().getSimpleName());
        } catch (RuntimeException ex) {
            // SMTP may already have accepted the email. Never automatically send it again.
            finish(id, ActivationEmailOutbox::review);
            log.warn("nc39.email.review store_id={} event_id={} type={}", delivery.storeId(), id, ex.getClass().getSimpleName());
        }
    }

    private Delivery claim(Long id, Instant now, Instant startAt) {
        Long storeId = outbox.storeIdFor(id).orElse(null);
        if (storeId == null) return null;
        // All workers use store -> event lock ordering, including scheduling and suppression.
        Store store = stores.findActiveByStoreIdForUpdate(storeId).orElse(null);
        var event = outbox.findForUpdate(id).orElse(null);
        if (event == null || event.getState() != ActivationEmailOutbox.State.PENDING || event.getNextAttemptAt().isAfter(now)) return null;
        // Also suppress any previously queued D15; changing flags must not revive this stage.
        if (event.getStep().equals("D15")) { suppress(event, "d15_temporarily_disabled"); return null; }
        if (!Set.of("D3", "D7", "D11").contains(event.getStep())) { suppress(event, "unsupported_stage"); return null; }
        if (store == null || store.isErasurePending() || !event.getInstalledAt().equals(store.getInstalledAt())) {
            suppress(event, "store_inactive_or_reinstalled"); return null;
        }
        if (store.getInstalledAt().isBefore(startAt)) { suppress(event, "outside_cohort"); return null; }
        if (outbox.existsByStoreIdAndStep(store.getStoreId(), "ACTIVATED")) { suppress(event, "first_activation"); return null; }
        if (configured(store.getStoreId())) { suppress(event, "first_activation"); return null; }
        Duration stageAge = switch (event.getStep()) {
            case "D3" -> Duration.ofHours(72);
            case "D7" -> Duration.ofHours(168);
            case "D11" -> Duration.ofHours(264);
            default -> throw new IllegalStateException("Unsupported activation stage");
        };
        if (now.isAfter(event.getInstalledAt().plus(stageAge).plus(Duration.ofHours(24)))) {
            suppress(event, "stage_expired"); return null;
        }
        if (now.isBefore(store.getInstalledAt().plus(stageAge))) return null;
        Set<Long> allowlist = parseAllowlist();
        if (allowlist.isEmpty() || !allowlist.contains(store.getStoreId())) { suppress(event, "not_allowlisted"); return null; }
        String blocked = protection.reason(store.getStoreId());
        if (blocked != null) { suppress(event, blocked); return null; }
        if (!dryRun && !protection.ready()) { log.debug("nc39.email.deferred event_id={} reason=feedback_not_configured", id); return null; }
        if (!suppressionReady) { log.debug("nc39.email.deferred event_id={} reason=suppression_not_ready", id); return null; }
        if (dryRun) {
            event.simulated(); log.info("nc39.email.simulated store_id={} step={}", store.getStoreId(), event.getStep()); return null;
        }
        MailCopy copy = copy(event.getStep(), store.getStoreCountryCode(), store.getStoreName());
        String unsubscribeUrl = protection.prepare(event, store.getStoreEmail());
        event.claim();
        outbox.saveAndFlush(event); // Committed by REQUIRES_NEW before any SMTP call.
        return new Delivery(store.getStoreId(), copy, event.getStep(), event.getAttempts(), store.getStoreEmail(), unsubscribeUrl);
    }

    private void finish(Long id, java.util.function.Consumer<ActivationEmailOutbox> update) {
        tx.executeWithoutResult(status -> outbox.findForUpdate(id).ifPresent(event -> {
            if (event.getState() == ActivationEmailOutbox.State.CLAIMED) update.accept(event);
        }));
    }

    private Instant startAt() {
        if (startAtText == null || startAtText.isBlank()) return null;
        try { return Instant.parse(startAtText); }
        catch (RuntimeException ex) { log.warn("nc39.schedule.invalid_start_at"); return null; }
    }

    private void suppress(ActivationEmailOutbox event, String reason) {
        event.suppress(reason);
        log.info("nc39.email.suppressed store_id={} event_id={} step={} reason={}",
                event.getStoreId(), event.getId(), event.getStep(), reason);
    }

    private Set<Long> parseAllowlist() {
        try { return Arrays.stream(allowlistText.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                .map(Long::valueOf).collect(Collectors.toUnmodifiableSet()); }
        catch (RuntimeException ex) { return Set.of(); }
    }

    private MailCopy copy(String step, String country, String name) {
        boolean es = country != null && Set.of("AR", "MX", "CL", "CO", "UY").contains(country.toUpperCase());
        String store = name == null || name.isBlank() ? "" : name.strip();
        if ("D3".equals(step)) return new MailCopy(es ? "Empezá a personalizar tu primer producto" : "Configure seu primeiro produto personalizado",
                es ? "¡Hola" + (store.isEmpty() ? "" : " " + store) + "! Elegí un producto y agregá los campos que tu cliente debe completar. Abrí la aplicación desde el administrador de Tiendanube. Si necesitás ayuda, contactanos desde el soporte de la aplicación. Equipo Campos Personalizados."
                        : "Olá" + (store.isEmpty() ? "" : " " + store) + "! Escolha um produto e adicione os campos que seu cliente deve preencher. Abra o aplicativo pelo painel da Nuvemshop. Se precisar de ajuda, fale conosco pelo suporte do aplicativo. Equipe Campos Personalizados.");
        if ("D7".equals(step)) return new MailCopy(es ? "¿Necesitás ayuda para personalizar tu primer producto?" : "Precisa de ajuda para personalizar seu primeiro produto?",
                es ? "¡Hola" + (store.isEmpty() ? "" : " " + store) + "! Elegí un producto, configurá los campos y revisá su página en la tienda. Si tenés alguna dificultad, abrí el soporte dentro de la aplicación y contanos qué producto querés personalizar. Equipo Campos Personalizados."
                        : "Olá" + (store.isEmpty() ? "" : " " + store) + "! Escolha um produto, configure os campos e confira a página na loja. Se tiver dificuldade, abra o suporte dentro do aplicativo e conte qual produto deseja personalizar. Equipe Campos Personalizados.");
        if ("D11".equals(step)) return new MailCopy(es ? "¿Te ayudamos a configurar tu primer producto?" : "Vamos ajudar a configurar seu primeiro produto?",
                es ? "Este es el último correo de esta secuencia de activación. Abrí la aplicación desde Tiendanube para configurar tu primer producto. Si necesitás ayuda, contactanos desde el soporte de la aplicación. Equipo Campos Personalizados."
                        : "Este é o último e-mail desta sequência de ativação. Abra o aplicativo pelo painel da Nuvemshop para configurar seu primeiro produto. Se precisar de ajuda, fale conosco pelo suporte do aplicativo. Equipe Campos Personalizados.");
        throw new IllegalArgumentException("Unsupported activation stage");
    }
    private record MailCopy(String subject, String body) { }
    private record Delivery(Long storeId, MailCopy copy, String step, int attempt, String recipient, String unsubscribeUrl) { }
}
