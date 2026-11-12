package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.SesProperties;
import br.com.nuvemcustomfields.repository.StoreRepository;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class StoreAdminEmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoreAdminEmailService.class);
    private final StoreRepository stores;
    private final SesProperties properties;
    private final JavaMailSender sender;
    private final ActivationEmailTemplates templates;
    private final BrandedEmailTemplate design;

    public StoreAdminEmailService(StoreRepository stores, SesProperties properties, JavaMailSender sender, ActivationEmailTemplates templates) {
        this(stores, properties, sender, templates, new BrandedEmailTemplate());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public StoreAdminEmailService(StoreRepository stores, SesProperties properties, JavaMailSender sender,
            ActivationEmailTemplates templates, BrandedEmailTemplate design) {
        this.stores = stores;
        this.properties = properties;
        this.sender = sender;
        this.templates = templates;
        this.design = design;
    }

    public void send(Long storeId, String subject, String body) {
        var plain = prepare(storeId, subject, body);
        try {
            var mail = sender.createMimeMessage();
            var helper = new org.springframework.mail.javamail.MimeMessageHelper(mail, true, "UTF-8");
            helper.setFrom(plain.getFrom()); helper.setTo(plain.getTo()); helper.setSubject(plain.getSubject());
            var rendered = design.render(subject, body, null, null, null);
            helper.setText(rendered.text(), rendered.html());
            StoreEmailHistoryService.identify(mail,storeId,"MANUAL",null);
            sender.send(mail);
        }
        catch (RuntimeException ex) { throw deliveryFailure(storeId, ex); }
        catch (jakarta.mail.MessagingException ex) { throw deliveryFailure(storeId, ex); }
        LOGGER.info("store.admin_email.sent store_id={}", storeId);
    }

    public void sendActivation(Long storeId, Long eventId, String recipient, String subject, String body,
            String unsubscribeUrl, String configurationSet) {
        // Reuse all existing pre-SMTP validation, but never substitute a changed store address.
        var plain = prepare(storeId, subject, body);
        if (recipient == null || !recipient.equals(plain.getTo()[0])) throw new IllegalArgumentException("Destinatário alterado antes do envio.");
        if (eventId == null || configurationSet == null || !configurationSet.matches("[A-Za-z0-9_-]{1,64}")
                || unsubscribeUrl == null || !unsubscribeUrl.startsWith("https://") || unsubscribeUrl.contains("\r") || unsubscribeUrl.contains("\n"))
            throw new IllegalArgumentException("Configuração da sequência inválida.");
        try {
            var message = sender.createMimeMessage();
            var helper = new org.springframework.mail.javamail.MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(plain.getFrom()); helper.setTo(recipient); helper.setSubject(plain.getSubject());
            String unsubscribe = templates.unsubscribe(stores.findByStoreId(storeId).map(s -> s.getStoreCountryCode()).orElse(null), unsubscribeUrl);
            var rendered = design.render(subject, body, null, null, unsubscribe);
            helper.setText(rendered.text(), rendered.html());
            message.setHeader("X-SES-CONFIGURATION-SET", configurationSet);
            message.setHeader("X-SES-MESSAGE-TAGS", "nc39_event=" + eventId + ", nc39_store=" + storeId);
            message.setHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">");
            message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            StoreEmailHistoryService.identify(message,storeId,"ACTIVATION","activation:"+eventId);
            sender.send(message);
        } catch (jakarta.mail.MessagingException | RuntimeException ex) { throw deliveryFailure(storeId, ex); }
        LOGGER.info("nc39.transport.accepted store_id={} event_id={}", storeId, eventId);
    }

    public void sendAction(Long storeId, String subject, String body, String actionLabel, String actionUrl) {
        sendAction(storeId,subject,body,actionLabel,actionUrl,"ACTION",null);
    }

    public void sendAction(Long storeId, String subject, String body, String actionLabel, String actionUrl,String category,String sourceKey) {
        var plain = prepare(storeId, subject, body);
        java.net.URI uri;
        try { uri = java.net.URI.create(actionUrl); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Link do aplicativo inválido."); }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IllegalArgumentException("O link do aplicativo deve usar HTTPS.");
        try {
            var mail = sender.createMimeMessage();
            var helper = new org.springframework.mail.javamail.MimeMessageHelper(mail, true, "UTF-8");
            helper.setFrom(plain.getFrom()); helper.setTo(plain.getTo()); helper.setSubject(plain.getSubject());
            var rendered = design.render(subject, body, actionLabel, actionUrl, null);
            helper.setText(rendered.text(), rendered.html());
            StoreEmailHistoryService.identify(mail,storeId,category,sourceKey);
            sender.send(mail);
        } catch (jakarta.mail.MessagingException | RuntimeException ex) { throw deliveryFailure(storeId, ex); }
        LOGGER.info("store.admin_email.sent store_id={}", storeId);
    }

    private IllegalStateException deliveryFailure(Long storeId, Exception ex) {
        LOGGER.warn("store.admin_email.failed store_id={} type={}", storeId, ex.getClass().getSimpleName());
        return new IllegalStateException("Não foi possível confirmar o envio. Confira o provedor de e-mail antes de tentar novamente.");
    }

    public boolean configured() { return properties.configured(); }
    public static boolean validRecipient(String recipient) {
        try {
            if (recipient == null || recipient.isBlank() || recipient.contains("\r") || recipient.contains("\n")) return false;
            var address = new InternetAddress(recipient, true); address.validate();
            return recipient.equals(address.getAddress());
        } catch (AddressException ex) { return false; }
    }

    private SimpleMailMessage prepare(Long storeId, String subject, String body) {
        var store = stores.findByStoreId(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Loja não encontrada."));
        String recipient = store.getStoreEmail();
        if (!validRecipient(recipient)) throw new IllegalArgumentException("A loja não possui um e-mail válido cadastrado.");
        if (subject == null || subject.isBlank() || subject.length() > 200
                || subject.contains("\r") || subject.contains("\n"))
            throw new IllegalArgumentException("Informe um assunto de até 200 caracteres, em uma única linha.");
        if (body == null || body.isBlank() || body.length() > 10000)
            throw new IllegalArgumentException("Informe uma mensagem de até 10.000 caracteres.");
        if (!properties.configured())
            throw new IllegalArgumentException("O envio de e-mail não está configurado neste ambiente.");
        var mail = new SimpleMailMessage();
        mail.setFrom(properties.from());
        mail.setTo(recipient);
        mail.setSubject(subject.strip());
        mail.setText(body.strip());
        return mail;
    }
}
