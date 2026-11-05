package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.SesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SupportEmailNotifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(SupportEmailNotifier.class);
    private final SesProperties properties;
    private final JavaMailSender sender;
    private final String baseUrl;
    private final BrandedEmailTemplate design;

    public SupportEmailNotifier(SesProperties properties,
            @Value("${nuvemshop.app-base-url}") String baseUrl, JavaMailSender sender) {
        this(properties, baseUrl, sender, new BrandedEmailTemplate());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SupportEmailNotifier(SesProperties properties, @Value("${nuvemshop.app-base-url}") String baseUrl,
            JavaMailSender sender, BrandedEmailTemplate design) {
        this.properties = properties;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.sender = sender;
        this.design = design;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReply(SupportReplyEvent event) {
        if (!properties.configured()) {
            LOGGER.info("support.email_provider_not_configured ticket_id={} Provedor AWS SES não configurado.",
                    event.ticketId());
            return;
        }
        if (event.recipient() == null || event.recipient().isBlank()) {
            LOGGER.warn("support.email_recipient_missing ticket_id={} Loja sem e-mail cadastrado.", event.ticketId());
            return;
        }
        try {
            String subject = "Resposta ao chamado #" + event.ticketId() + " — "
                    + event.subject().replace('\r', ' ').replace('\n', ' ');
            String body = "Você recebeu uma resposta da nossa equipe de suporte.\n\n"
                    + "Chamado #" + event.ticketId() + ": " + event.subject() + "\n\n"
                    + event.message();
            String action = baseUrl + "/support/tickets/" + event.ticketId();
            var rendered = design.render(subject, body, "Acompanhar chamado", action,
                    "Responda pelo chamado no aplicativo. Este e-mail é uma notificação automática.");
            var mail = sender.createMimeMessage();
            var helper = new MimeMessageHelper(mail, true, "UTF-8");
            helper.setFrom(properties.from()); helper.setTo(event.recipient()); helper.setSubject(subject);
            helper.setText(rendered.text(), rendered.html());
            sender.send(mail);
            LOGGER.info("support.email_sent ticket_id={}", event.ticketId());
        } catch (jakarta.mail.MessagingException | RuntimeException ex) {
            // Exceções do provedor podem incluir conteúdo ou dados do destinatário.
            LOGGER.warn("support.email_failed ticket_id={} type={}", event.ticketId(), ex.getClass().getSimpleName());
        }
    }
}
