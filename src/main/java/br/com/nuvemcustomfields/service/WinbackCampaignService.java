package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.properties.SesProperties;
import br.com.nuvemcustomfields.properties.SesEventsProperties;
import br.com.nuvemcustomfields.repository.*;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Service
public class WinbackCampaignService {
    public static final Set<String> REASONS = Set.of("PRICE", "CONFIGURATION", "NO_LONGER_NEEDED", "MISSING_FEATURE", "OTHER");
    private final StoreRepository stores;
    private final WinbackOutboxRepository outbox;
    private final WinbackCampaignRepository campaigns;
    private final WinbackEmailRepository emails;
    private final JavaMailSender sender;
    private final SesProperties smtp;
    private final SesEventsProperties ses;
    private final String baseUrl;
    private final WinbackDiscountService discounts;
    private final WinbackCouponRepository coupons;
    public WinbackCampaignService(StoreRepository stores, WinbackOutboxRepository outbox,
            WinbackCampaignRepository campaigns, WinbackEmailRepository emails, JavaMailSender sender,
            SesProperties smtp, SesEventsProperties ses, @Value("${nuvemshop.app-base-url}") String baseUrl,
            WinbackDiscountService discounts, WinbackCouponRepository coupons) {
        this.stores = stores; this.outbox = outbox; this.campaigns = campaigns; this.emails = emails;
        this.sender = sender; this.smtp = smtp; this.ses = ses; this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.discounts = discounts; this.coupons = coupons;
    }

    /** Commit the sending claim BEFORE SMTP, so a crash cannot trigger an automatic duplicate. */
    @Transactional
    public String prepareManual(Long storeId) {
        return prepareManual(storeId, false);
    }

    @Transactional
    public String prepareManual(Long storeId, boolean authorizeErasureContact) {
        if (!smtp.configured() || !StringUtils.hasText(ses.configurationSet()))
            throw new IllegalStateException("Configure SES e Configuration Set antes de disparar a reconquista.");
        var store = stores.findByStoreIdForUpdate(storeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (store.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "A loja está ativa.");
        if (store.isErasurePending() && !authorizeErasureContact)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Confirme a autorização do contato manual para esta exclusão pendente.");
        if (!StringUtils.hasText(store.getStoreEmail())) throw new IllegalStateException("A loja não possui e-mail cadastrado.");
        if (campaigns.existsByStoreIdAndOptedOutAtIsNotNull(storeId))
            throw new IllegalStateException("A loja optou por não receber contatos.");
        var campaign = campaigns.findByStoreIdAndUninstalledAt(storeId, store.getUninstalledAt()).orElseGet(() -> {
            var c = new WinbackCampaign(storeId, store.getUninstalledAt());
            c.setPaidBeforeDeparture(discounts.hasPaidHistory(storeId));
            return campaigns.saveAndFlush(c);
        });
        if (campaign.getReinstalledAt() != null || campaign.getOptedOutAt() != null)
            throw new IllegalStateException("Esta campanha não permite novos contatos.");
        var existing = emails.findByCampaignIdAndStep(campaign.getId(), "FEEDBACK").orElse(null);
        if (existing != null) return null;
        var mail = new WinbackEmail(campaign.getId(), "FEEDBACK");
        mail.authorizeErasureContact(store.isErasurePending() ? store.getErasureRequestedAt() : null);
        mail.status("SENDING");
        return emails.saveAndFlush(mail).getId();
    }

    /** Commit the sending claim BEFORE SMTP, so a crash cannot trigger an automatic duplicate. */
    @Transactional
    public String prepare(String eventId) {
        if (!smtp.configured() || !StringUtils.hasText(ses.configurationSet()))
            throw new IllegalStateException("Configure SES e Configuration Set antes de disparar a reconquista.");
        var event = outbox.findById(eventId).orElse(null);
        if (event == null) return null;
        var store = stores.findByStoreIdForUpdate(event.getStoreId()).orElse(null);
        if (store == null || store.isActive() || store.isErasurePending() || !event.getUninstalledAt().equals(store.getUninstalledAt())) return null;
        if (event.getPublishedAt() == null) return null;
        var campaign = campaigns.findByStoreIdAndUninstalledAt(store.getStoreId(), store.getUninstalledAt()).orElse(null);
        if (campaign == null || campaign.getReinstalledAt() != null || campaign.getOptedOutAt() != null) return null;
        if (campaigns.existsByStoreIdAndOptedOutAtIsNotNull(store.getStoreId())) return null;
        if (!StringUtils.hasText(store.getStoreEmail())) return null;
        var existing = emails.findByCampaignIdAndStep(campaign.getId(), "FEEDBACK");
        if (existing.isPresent()) return null;
        var mail = new WinbackEmail(campaign.getId(), "FEEDBACK");
        mail.status("SENDING");
        return emails.saveAndFlush(mail).getId();
    }

    @Transactional
    public void send(String emailId) throws MessagingException {
        String campaignId = emails.campaignIdFor(emailId).orElse(null);
        if (campaignId == null) return;
        var storeId = campaigns.storeIdFor(campaignId).orElse(null);
        if (storeId == null) return;
        // Locks held through SMTP: redact and reinstall cannot race the eligibility check.
        var store = stores.findByStoreIdForUpdate(storeId).orElse(null);
        var campaign = campaigns.findById(campaignId).orElse(null);
        var mail = emails.findById(emailId).orElse(null);
        if (campaign == null || mail == null || store == null) return;
        if (!"SENDING".equals(mail.getStatus())) return;
        boolean featureDelivered = "FEATURE_DELIVERED".equals(mail.getStep());
        if ((store.isErasurePending() && !authorizedPendingContact(store, mail)) || (!featureDelivered && (store.isActive() || !campaign.getUninstalledAt().equals(store.getUninstalledAt())
                || campaign.getReinstalledAt() != null)) || campaign.getOptedOutAt() != null
                || campaigns.existsByStoreIdAndOptedOutAtIsNotNull(storeId)
                || (featureDelivered && !"ENTREGUE".equals(campaign.getFeatureStatus()))) {
            mail.status("CANCELLED"); return;
        }
        boolean spanish = !"BR".equalsIgnoreCase(store.getStoreCountryCode());
        String link = baseUrl + "/winback/" + mail.getId();
        String question = spanish ? "¿Qué podemos mejorar para tu tienda?" : "O que podemos melhorar para sua loja?";
        String text = spanish
                ? "Vimos que desinstalaste Campos Personalizados. Nos gustaría conocer el motivo y cómo podemos ayudarte."
                : "Vimos que você desinstalou o Campos Personalizados. Gostaríamos de entender o motivo e como podemos ajudar.";
        String cta = spanish ? "Compartir el motivo" : "Contar o motivo";
        String footer = spanish ? "Para dejar de recibir estos mensajes, abre el enlace y elige dejar de recibir comunicaciones."
                : "Para deixar de receber estas mensagens, abra o link e escolha não receber novas comunicações.";
        if ("FOLLOWUP".equals(mail.getStep())) {
            var coupon = coupons.findByCampaignId(campaignId).filter(c -> c.getUsedAt() == null
                    && c.getExpiresAt().isAfter(Instant.now()) && discounts.available(store)).orElse(null);
            String context = switch (campaign.getReason() == null ? "" : campaign.getReason()) {
                case "PRICE" -> "Queremos ajudar você a voltar com um custo menor no primeiro mês.";
                case "CONFIGURATION" -> "Recebemos sua dificuldade de configuração e nossa equipe pode ajudar com essa etapa.";
                case "NO_LONGER_NEEDED" -> "Se sua loja precisar novamente de personalização, estaremos por aqui.";
                case "MISSING_FEATURE" -> "Recebemos sua solicitação de funcionalidade e ela será avaliada pela equipe.";
                default -> "Recebemos sua resposta e queremos entender como ajudar sua loja.";
            };
            question = coupon != null ? "Seu desconto para voltar ao Campos Personalizados" : "Recebemos sua resposta";
            text = context + (coupon == null ? "" : " Você tem 50% de desconto apenas na primeira mensalidade, após reinstalar"
                    + " na mesma loja. A partir da segunda mensalidade, será cobrado o valor integral do plano escolhido."
                    + " Oferta válida por 30 dias da emissão do cupom, para pagamento por cartão na Efí.");
            cta = coupon != null ? "Ver cupom e voltar" : "Acompanhar sua resposta";
            if (spanish) {
                question = "Recibimos tu respuesta";
                text = "Gracias por contarnos el motivo. Nuestro equipo evaluará tu comentario y cómo ayudarte.";
                cta = "Ver respuesta";
            }
        } else if (featureDelivered) {
            question = spanish ? "La funcionalidad que pediste ya está disponible" : "A funcionalidade que você pediu está disponível";
            text = spanish ? "Nuestro equipo marcó tu solicitud como entregada. Abre el enlace para volver a la aplicación."
                    : "Nossa equipe marcou sua solicitação como entregue. Abra o link para voltar ao aplicativo.";
            cta = spanish ? "Volver a la aplicación" : "Voltar ao aplicativo";
        }
        var message = sender.createMimeMessage();
        var helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(smtp.from()); helper.setTo(store.getStoreEmail()); helper.setSubject(question);
        helper.setText(text + "\n\n" + link + "\n\n" + footer,
                "<!doctype html><html><body style=\"font-family:Arial,sans-serif;margin:0;padding:24px;background:#f6f7f9\">"
                + "<main style=\"max-width:560px;margin:auto;padding:24px;background:white\"><p>Campos Personalizados</p>"
                + "<h1>" + question + "</h1><p>" + text + "</p><p><a href=\"" + link
                + "\" style=\"display:inline-block;padding:14px;background:#2563eb;color:white;border-radius:6px\">"
                + cta + "</a></p><p>" + footer + "</p></main></body></html>");
        message.setHeader("X-SES-CONFIGURATION-SET", ses.configurationSet());
        message.setHeader("X-SES-MESSAGE-TAGS", "winback_campaign=" + campaign.getId() + ",winback_email=" + mail.getId());
        sender.send(message);
        mail.sent();
    }

    @Transactional(readOnly = true)
    public WinbackEmail publicEmail(String id) {
        var mail = emails.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (mail.getSentAt() == null || !campaigns.existsById(mail.getCampaignId()))
            throw new ResponseStatusException(HttpStatus.GONE);
        Long storeId = campaigns.storeIdFor(mail.getCampaignId()).orElse(null);
        if (storeId == null || stores.findByStoreId(storeId).map(s -> s.isErasurePending() && !authorizedPendingContact(s, mail)).orElse(true))
            throw new ResponseStatusException(HttpStatus.GONE);
        return mail;
    }

    @Transactional
    public void respond(String emailId, String reason, String response, boolean optOut) {
        var mail = publicEmail(emailId);
        var storeId = campaigns.storeIdFor(mail.getCampaignId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE));
        var owner = stores.findByStoreIdForUpdate(storeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE));
        if (owner.isErasurePending() && !authorizedPendingContact(owner, mail)) throw new ResponseStatusException(HttpStatus.GONE);
        var campaign = campaigns.findById(mail.getCampaignId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE));
        if (optOut) { campaign.optOut(); return; }
        if (mail.getCreatedAt().plus(30, ChronoUnit.DAYS).isBefore(Instant.now()))
            throw new ResponseStatusException(HttpStatus.GONE, "O prazo para responder terminou.");
        if (!REASONS.contains(reason)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um motivo.");
        String value = response == null ? "" : response.strip();
        if (value.length() > 2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resposta muito longa.");
        if (("CONFIGURATION".equals(reason) || "MISSING_FEATURE".equals(reason)) && value.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conte a dificuldade ou a funcionalidade que precisa.");
        campaign.respond(reason, value);
        // A autorização manual cobre somente este pedido de feedback, não ofertas/follow-ups.
        if (owner.isErasurePending()) return;
        var store = stores.findByStoreId(storeId).orElseThrow();
        discounts.issue(campaign, store);
        if (emails.findByCampaignIdAndStep(campaign.getId(), "FOLLOWUP").isEmpty())
            emails.save(new WinbackEmail(campaign.getId(), "FOLLOWUP"));
    }

    @Transactional
    public String prepareFollowup(String emailId) {
        if (!smtp.configured() || !StringUtils.hasText(ses.configurationSet())) return null;
        String campaignId = emails.campaignIdFor(emailId).orElse(null);
        if (campaignId == null) return null;
        var storeId = campaigns.storeIdFor(campaignId).orElse(null);
        if (storeId == null) return null;
        var store = stores.findByStoreIdForUpdate(storeId).orElse(null);
        var mail = emails.findById(emailId).orElse(null);
        if (store == null || store.isErasurePending()) {
            if (mail != null && "PREPARED".equals(mail.getStatus())) mail.status("CANCELLED");
            return null;
        }
        if (mail == null || !"PREPARED".equals(mail.getStatus())) return null;
        mail.status("SENDING");
        return mail.getId();
    }

    private boolean authorizedPendingContact(Store store, WinbackEmail mail) {
        return "FEEDBACK".equals(mail.getStep()) && mail.getErasureContactRequestAt() != null
                && mail.getErasureContactRequestAt().equals(store.getErasureRequestedAt());
    }

    @Transactional(readOnly = true)
    public WinbackCoupon publicCoupon(String emailId) {
        var mail = publicEmail(emailId);
        var campaign = campaigns.findById(mail.getCampaignId()).orElseThrow();
        var store = stores.findByStoreId(campaign.getStoreId()).orElse(null);
        if (store == null || campaign.getOptedOutAt() != null || !discounts.available(store)
                || campaigns.existsByStoreIdAndOptedOutAtIsNotNull(store.getStoreId())) return null;
        return coupons.findByCampaignId(mail.getCampaignId())
                .filter(c -> c.getUsedAt() == null && c.getExpiresAt().isAfter(Instant.now())).orElse(null);
    }

    @Transactional
    public void featureStatus(String campaignId, String status) {
        if (!Set.of("NOVA", "EM_ANALISE", "PLANEJADA", "ENTREGUE", "NAO_PREVISTA").contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var storeId = campaigns.storeIdFor(campaignId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        stores.findByStoreIdForUpdate(storeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var c = campaigns.findById(campaignId).orElseThrow();
        if (!"MISSING_FEATURE".equals(c.getReason())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        c.setFeatureStatus(status);
        if ("ENTREGUE".equals(status) && emails.findByCampaignIdAndStep(campaignId, "FEATURE_DELIVERED").isEmpty())
            emails.save(new WinbackEmail(campaignId, "FEATURE_DELIVERED"));
    }
}
