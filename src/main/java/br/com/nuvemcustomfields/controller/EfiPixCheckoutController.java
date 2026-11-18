package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.properties.FeatureAccessProperties;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.service.*;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/billing/pay/pix")
public class EfiPixCheckoutController {
    private record Reservation(LocalDate firstDebit, boolean fresh) {}
    private final StoreRepository stores;
    private final AdminStoreService admin;
    private final PaymentSubscriptionRepository subscriptions;
    private final EfiScheduledPixGateway gateway;
    private final EfiRecurringPixClient client;
    private final FeatureAccessProperties features;
    private final PaymentSubscriptionService billing;
    private final TransactionTemplate tx;
    private final JdbcTemplate db;

    public EfiPixCheckoutController(StoreRepository stores, AdminStoreService admin, PaymentSubscriptionRepository subscriptions,
            EfiScheduledPixGateway gateway, EfiRecurringPixClient client, FeatureAccessProperties features,
            PaymentSubscriptionService billing, TransactionTemplate tx, JdbcTemplate db) {
        this.stores=stores; this.admin=admin; this.subscriptions=subscriptions; this.gateway=gateway;
        this.client=client; this.features=features; this.billing=billing; this.tx=tx; this.db=db;
    }

    private BigDecimal price(Store store, PlanType plan) {
        if (!features.allows(store.getStoreId(), FeatureAccessProperties.Feature.SCHEDULED_PIX))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!gateway.configured() || !gateway.supports(store) || !plan.isBillable()
                || billing.provider(store).orElse(null) != PaymentProviderType.EFI || !billing.available(store))
            throw new PaymentGatewayException("Pix agendado indisponível para esta loja.");
        var quote = billing.efiQuote(store,plan);
        if (quote.discounted() || quote.regularAmount().signum() <= 0)
            throw new PaymentGatewayException("O Pix agendado requer mensalidades de valor fixo.");
        return quote.regularAmount();
    }

    @GetMapping
    public String form(@RequestParam PlanType plan,HttpSession session,Model model,RedirectAttributes flash) {
        try {
            var store=admin.requireCurrentStore(session);
            model.addAttribute("amount",price(store,plan));
            model.addAttribute("banks",client.participants().required("participantes"));
            model.addAttribute("plan",plan);
            var pending=subscriptions.findByStoreId(store.getStoreId())
                    .filter(s -> s.getProvider()==PaymentProviderType.EFI_PIX && s.getStatus()==PaymentSubscriptionStatus.PENDING);
            if (pending.isPresent() && pending.get().getPlan()!=plan)
                throw new PaymentGatewayException("Existe um Pix pendente para outro plano.");
            var token=pending.map(s -> UUID.fromString(s.getExternalReference())).orElseGet(UUID::randomUUID);
            session.setAttribute("efiPixAttempt",token);
            UUID csrf=UUID.randomUUID();
            session.setAttribute("efiPixToken",csrf);
            model.addAttribute("token",csrf);
            return "admin/efi-pix";
        } catch (PaymentGatewayException e) {
            flash.addFlashAttribute("error",e.getMessage());
            return "redirect:/admin/billing/pay?plan="+plan.name();
        }
    }

    @PostMapping
    public String submit(@RequestParam PlanType plan,@RequestParam("token") UUID csrf,@RequestParam UUID bank,
            @RequestParam String cpf,@RequestParam BigDecimal amount,HttpSession session,RedirectAttributes flash) {
        var store=admin.requireCurrentStore(session);
        if (!features.allows(store.getStoreId(),FeatureAccessProperties.Feature.SCHEDULED_PIX)
                || !csrf.equals(session.getAttribute("efiPixToken"))
                || !(session.getAttribute("efiPixAttempt") instanceof UUID token)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!cpf.matches("[0-9]{11}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        LocalDate first=LocalDate.now(EfiScheduledPixGateway.ZONE).plusDays(1);
        try {
            String fingerprint=fingerprint(token,bank,cpf);
            Reservation reservation=tx.execute(ignored -> {
                var locked=stores.findActiveByStoreIdForUpdate(store.getStoreId()).orElseThrow();
                if (price(locked,plan).compareTo(amount)!=0) throw new PaymentGatewayException("O preço mudou. Confira o valor antes de continuar.");
                var sub=subscriptions.findByStoreId(store.getStoreId()).orElseGet(PaymentSubscription::new);
                if (sub.getProvider()==PaymentProviderType.EFI_PIX && sub.getStatus()==PaymentSubscriptionStatus.PENDING
                        && sub.getPlan()==plan && token.toString().equals(sub.getExternalReference())) {
                    var rows=db.query("select first_debit from efi_pix_recurrences where request_id=? and payer_fingerprint=? and amount=? and environment=?",
                            (rs,i)->rs.getDate(1).toLocalDate(),token.toString(),fingerprint,amount,gateway.environment().name());
                    if (rows.isEmpty()) throw new PaymentGatewayException("Use o banco e CPF da tentativa anterior.");
                    db.update("update efi_pix_recurrences set status='reenvio' where request_id=? and status='pendente'",token.toString());
                    return new Reservation(rows.getFirst(),false);
                }
                if (sub.getId()!=null && (sub.isAccessActive() || sub.getStatus()!=PaymentSubscriptionStatus.CANCELED))
                    throw new PaymentGatewayException("Já existe uma assinatura ou tentativa de pagamento. Consulte o painel de cobrança.");
                db.update("insert into efi_pix_recurrences(request_id,store_id,plan_type,environment,amount,first_debit,debit_count,status,payer_fingerprint) values(?,?,?,?,?,?,?,?,?)",
                        token.toString(),store.getStoreId(),plan.name(),gateway.environment().name(),amount,first,EfiRecurringPixClient.MONTHLY_DEBITS,"pendente",fingerprint);
                sub.setStoreId(store.getStoreId()); sub.setProvider(gateway.provider()); sub.setProviderEnvironment(gateway.environment());
                sub.setExternalReference(token.toString()); sub.setProviderSubscriptionId(token.toString());
                sub.setProviderCheckoutId(null); sub.setProviderPriceId(null); sub.setNextPaymentAt(null);
                sub.setCancellationEffectiveAt(null); sub.setCancellationPending(false); sub.setCurrentPeriodEnd(null);
                sub.setLastPaymentId(null); sub.setLastPaymentStatus(null); sub.applyWinbackCoupon(null,null,null);
                sub.clearUpgrade(); sub.setUpgradePaymentPending(false); sub.clearRenewalFailure(); sub.setCurrentPeriodStart(null); sub.setProviderCustomerId(null);
                sub.setCancellationRequestedAt(null); sub.setCancellationReason(null); sub.setLastError(null);
                sub.setWebhookDowngradeAfter(null); sub.setWebhookDowngradeStatus(null); sub.setAnalyticsFirstPaymentId(null);
                sub.captureAnalytics();
                sub.setCheckoutUrl(null); sub.setGraceUntil(null); sub.setCountryCode("BR"); sub.setCurrency("BRL");
                sub.setAmountValue(amount); sub.setPlan(plan); sub.setPendingStartedAt(Instant.now());
                sub.setStatus(PaymentSubscriptionStatus.PENDING); sub.setProviderStatus("pendente");
                subscriptions.saveAndFlush(sub);
                return new Reservation(first,true);
            });
            EfiRecurringPixClient.Consent consent;
            try {
                consent=client.start(store.getStoreId(),token,cpf,bank.toString(),amount,reservation.firstDebit());
            } catch (EfiRecurringPixClient.RequestRejected rejected) {
                if (reservation.fresh()) tx.executeWithoutResult(ignored -> {
                    stores.findActiveByStoreIdForUpdate(store.getStoreId());
                    subscriptions.findByStoreId(store.getStoreId()).filter(s -> token.toString().equals(s.getExternalReference())
                            && s.getStatus()==PaymentSubscriptionStatus.PENDING).ifPresent(s -> {
                        int closed=db.update("update efi_pix_recurrences set status='rejeitada' where request_id=? and status='pendente' and payment_id is null",token.toString());
                        if (closed==1) {
                            s.setStatus(PaymentSubscriptionStatus.CANCELED); s.setProviderStatus("rejeitada");
                            s.setProviderSubscriptionId(null);
                        }
                    });
                });
                throw rejected;
            }
            db.update("update efi_pix_recurrences set payment_id=? where request_id=?",consent.paymentId(),token.toString());
            session.removeAttribute("efiPixToken");
            return "redirect:"+consent.redirect().toASCIIString();
        } catch (PaymentGatewayException e) {
            flash.addFlashAttribute("error",e.getMessage());
            return "redirect:/admin/billing";
        }
    }

    private static String fingerprint(UUID token,UUID bank,String cpf) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest((token+"|"+bank+"|"+cpf).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
