package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.i18n.StoreLocale;
import org.springframework.beans.factory.annotation.Value;
import br.com.nuvemcustomfields.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RenewalFailureEmailService {
    private final StoreRepository stores;
    private final PaymentSubscriptionRepository subscriptions;
    private final PlanEventRepository events;
    private final PaymentNotificationOutboxRepository paidHistory;
    private final StoreAdminEmailService emails;
    private final String appBaseUrl;
    private RenewalFailureEmailRepository automaticEmails;
    @org.springframework.beans.factory.annotation.Autowired
    void setAutomaticEmails(RenewalFailureEmailRepository repository) { automaticEmails=repository; }
    public String automaticState(Long storeId) {
        return automaticEmails == null ? null : automaticEmails.findFirstByStoreIdOrderByIdDesc(storeId).map(row -> row.getState().name()).orElse(null);
    }

    public RenewalFailureEmailService(StoreRepository stores, PaymentSubscriptionRepository subscriptions,
            PlanEventRepository events, PaymentNotificationOutboxRepository paidHistory,
            StoreAdminEmailService emails, @Value("${nuvemshop.app-base-url}") String appBaseUrl) {
        this.stores = stores; this.subscriptions = subscriptions; this.events = events;
        this.paidHistory = paidHistory; this.emails = emails;
        this.appBaseUrl = appBaseUrl.replaceAll("/+$", "");
    }

    public record Notice(boolean eligible, String unavailableReason, String recipient, String subject, String body, String fingerprint,
                         String actionLabel, String actionUrl) { }

    public Notice preview(Long storeId) {
        var store = stores.findByStoreId(storeId).orElseThrow(() -> new IllegalArgumentException("Loja não encontrada."));
        var sub = subscriptions.findByStoreId(storeId).orElse(null);
        boolean grace = sub != null && sub.getRenewalFailureDetectedAt() != null && sub.getGraceUntil() != null
                && java.time.Instant.now().isBefore(sub.getGraceUntil()) && sub.getStatus() == PaymentSubscriptionStatus.PAST_DUE;
        if (sub != null && (sub.isUpgradePaymentPending() || sub.getUpgradePlan() != null))
            return unavailable("Há um upgrade pendente. Concilie o ajuste antes de enviar um aviso de renovação.");
        if (sub == null || !store.isActive() || sub.getProviderEnvironment() != PaymentEnvironment.PRODUCTION
                || sub.getProvider() == PaymentProviderType.DODO_TEST || !text(sub.getProviderSubscriptionId())
                || !text(sub.getLastPaymentId()) || sub.getPlan() == null || !sub.getPlan().isBillable()
                || (!grace && sub.isAccessActive()) || store.isCourtesyPremium() || store.isManualPlanOverride()
                || (sub.getProvider() == PaymentProviderType.EFI && !grace && sub.getStatus() != PaymentSubscriptionStatus.ERROR)
                || (!grace && store.getEffectivePlan().isBillable()) || !Set.of(PaymentSubscriptionStatus.ERROR,
                    PaymentSubscriptionStatus.PAST_DUE, PaymentSubscriptionStatus.PAUSED).contains(sub.getStatus())
                || !RenewalFailurePolicy.failedCharge(sub.getProvider(), sub.getProviderStatus(), sub.getLastPaymentStatus()))
            return unavailable("O aviso exige uma cobrança de produção não paga e acesso efetivamente no plano gratuito.");
        boolean previousPayment = sub.getRenewalFailureDetectedAt() != null || paidHistory.existsByStoreId(storeId) || events.existsByStoreIdAndToPlanInAndSourceIn(storeId,
            List.of(PlanType.PREMIUM, PlanType.PREMIUM_PLUS, PlanType.PREMIUM_ULTRA),
            List.of("EFI_CHECKOUT", "PAYMENT_RECONCILE", "PAYMENT_WEBHOOK", "PAYMENT_UPGRADE",
                "STRIPE_CHECKOUT", "CREEM_CHECKOUT", "DLOCAL_GO_CHECKOUT", "PENDING_TIMEOUT_RECONCILE"));
        if (!previousPayment) return unavailable("Não há histórico de pagamento confirmado para classificar esta falha como renovação.");
        if (!text(store.getStoreEmail())) return unavailable("A loja não possui e-mail cadastrado.");
        boolean es = "es".equals(StoreLocale.forCountry(store.getStoreCountryCode()).getLanguage());
        String deadline = grace ? java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone("BR".equalsIgnoreCase(store.getStoreCountryCode()) ? java.time.ZoneId.of("America/Sao_Paulo") : java.time.ZoneOffset.UTC)
                .format(sub.getGraceUntil()) + ("BR".equalsIgnoreCase(store.getStoreCountryCode()) ? " (horário de Brasília)" : " UTC") : null;
        String period = grace
            ? (es ? "Mantenemos el acceso a tu plan durante 24 horas desde la detección de la falla. Regularizá el pago hasta el " + deadline + ". Si el pago no se confirma hasta ese momento, el acceso vuelve al plan gratuito (FREE)."
                : "Mantemos seu acesso ao plano durante 24 horas a partir da identificação da falha. Regularize o pagamento até " + deadline + ". Se o pagamento não for confirmado até esse momento, o acesso volta ao plano gratuito (FREE).")
            : (es ? "Tu suscripción volvió al plan gratuito (FREE). Te pedimos que regularices el pago lo antes posible."
                : "Sua assinatura voltou ao plano gratuito (FREE). Pedimos que regularize o pagamento assim que possível.");
        String subject = es ? "No pudimos renovar tu suscripción — Campos Personalizados"
                : "Não conseguimos renovar sua assinatura — Campos Personalizados";
        String reason = reason(sub, es);
        String actionLabel = es ? "Reactivar mi plan " + sub.getPlan().getDisplayName() : "Reativar meu plano " + sub.getPlan().getDisplayName();
        boolean efi = sub.getProvider() == PaymentProviderType.EFI;
        String actionUrl = appBaseUrl + (efi ? "/admin/billing/reactivate?storeId=" + storeId : "/admin/billing");
        if (!efi) actionLabel = es ? "Regularizar suscripción" : "Regularizar assinatura";
        String action = efi
            ? (es ? "Usá el botón de abajo para reactivar tu plan " + sub.getPlan().getDisplayName() + ". El plan anterior ya estará seleccionado; solo tenés que revisar el valor e informar los datos de pago. Al confirmar, se reemplaza el contrato con error por una nueva suscripción del mismo plan, con un nuevo cobro y un nuevo ciclo mensual. Si el enlace solicita acceso, abrí la app desde el administrador de tu tienda, ingresá a Planes y elegí Reactivar mi plan."
                : "Use o botão abaixo para reativar seu plano " + sub.getPlan().getDisplayName() + ". Seu plano anterior já estará selecionado; basta conferir o valor e informar os dados de pagamento. Ao confirmar, o contrato com erro será substituído por uma nova assinatura do mesmo plano, com uma nova cobrança e um novo ciclo mensal. Se o link solicitar acesso, abra o app pelo administrador da loja, acesse Planos e escolha Reativar meu plano.")
            : (es ? "Abrí Campos Personalizados desde el administrador de tu tienda e ingresá a Planes para revisar y regularizar la suscripción con los datos de pago actualizados. Si no encontrás la opción, contactanos antes de realizar otra contratación."
                : "Abra o Campos Personalizados pelo administrador da sua loja e acesse Planos para revisar e regularizar a assinatura com os dados de pagamento atualizados. Se não encontrar a opção, entre em contato conosco antes de fazer outra contratação.");
        String body = es ? "Hola, equipo de " + name(store) + ".\n\nNo pudimos confirmar el pago de la renovación de tu suscripción de Campos Personalizados.\n\n"
            + reason + "\n\n" + period + "\n\nTus configuraciones siguen guardadas: no necesitás configurarlo todo de nuevo.\n\n" + action
            + "\n\nEl acceso al plan pago se restablece después de la confirmación del pago.\n\nSi ya regularizaste el pago, contactanos para revisar el estado.\n\nEquipo de Campos Personalizados"
            : "Olá, equipe da " + name(store) + ".\n\nNão conseguimos confirmar o pagamento da renovação da sua assinatura do Campos Personalizados.\n\n"
            + reason + "\n\n" + period + "\n\nSuas configurações continuam salvas: você não precisa cadastrar tudo novamente.\n\n" + action
            + "\n\nO acesso ao plano pago será restabelecido após a confirmação do pagamento.\n\nSe você já regularizou o pagamento, entre em contato para conferirmos o status.\n\nEquipe Campos Personalizados";
        return new Notice(true, null, store.getStoreEmail(), subject, body, fingerprint(store, sub, body), actionLabel, actionUrl);
    }

    public void send(Long storeId, String expectedFingerprint) {
        var sub = subscriptions.findByStoreId(storeId).orElse(null);
        if (automaticEmails != null && sub != null && sub.getRenewalFailurePaymentId() != null
                && automaticEmails.existsByProviderAndSubscriptionIdAndPaymentId(sub.getProvider(), sub.getProviderSubscriptionId(), sub.getRenewalFailurePaymentId()))
            throw new IllegalArgumentException("Já existe um aviso automático registrado para esta falha. Confira o envio antes de reenviar.");
        sendAutomatic(storeId, expectedFingerprint);
    }

    void sendAutomatic(Long storeId, String expectedFingerprint) {
        // Regenerate from current data; never trust the subject, recipient or failure in the POST.
        var current = preview(storeId);
        if (!current.eligible()) throw new IllegalArgumentException(current.unavailableReason());
        if (!current.fingerprint().equals(expectedFingerprint))
            throw new IllegalArgumentException("O pagamento ou o cadastro mudou. Reabra o detalhe da loja antes de enviar.");
        emails.sendAction(storeId, current.subject(), current.body(), current.actionLabel(), current.actionUrl());
    }

    private static Notice unavailable(String reason) { return new Notice(false, reason, null, null, null, null, null, null); }
    private static boolean text(String value) { return value != null && !value.isBlank(); }
    private static String normalize(String value) { return value == null ? "" : value.strip().toLowerCase(Locale.ROOT); }
    private static String name(Store store) { return text(store.getStoreName()) ? store.getStoreName().replaceAll("[\\r\\n]", " ") : "sua loja"; }
    private static String reason(PaymentSubscription sub, boolean es) {
        // Only explicit known customer codes are mapped. Never email raw gateway/technical exceptions.
        return switch (normalize(sub.getLastError())) {
            case "insufficient_funds" -> es ? "Motivo informado: fondos o límite insuficientes." : "Motivo informado: saldo ou limite insuficiente.";
            case "card_expired", "expired_card" -> es ? "Motivo informado: tarjeta vencida." : "Motivo informado: cartão vencido.";
            case "card_declined" -> es ? "Motivo informado: pago rechazado por el emisor de la tarjeta." : "Motivo informado: pagamento recusado pelo emissor do cartão.";
            default -> es ? "El proveedor registró el cobro como no pagado, sin un motivo específico disponible para informar."
                : "O provedor registrou a cobrança como não paga, sem um motivo específico disponível para informar.";
        };
    }
    private static String fingerprint(Store store, PaymentSubscription sub, String body) {
        String state = store.getStoreId() + "|" + store.getStoreEmail() + "|" + sub.getProvider() + "|"
            + sub.getProviderSubscriptionId() + "|" + sub.getLastPaymentId() + "|" + sub.getStatus() + "|" + sub.getVersion() + "|" + body;
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
