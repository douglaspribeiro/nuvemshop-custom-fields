package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.SesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SupportEmailNotifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(SupportEmailNotifier.class);
    private final SesProperties properties;
    private final JavaMailSender sender;
    private final String baseUrl;

    @Autowired
    public SupportEmailNotifier(SesProperties properties, @Value("${nuvemshop.app-base-url}") String baseUrl) {
        this(properties, baseUrl, createSender(properties));
    }

    SupportEmailNotifier(SesProperties properties, String baseUrl, JavaMailSender sender) {
        this.properties = properties;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.sender = sender;
    }

    private static JavaMailSender createSender(SesProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.host());
        sender.setPort(properties.port());
        sender.setUsername(properties.username());
        sender.setPassword(properties.password());
        sender.setDefaultEncoding("UTF-8");
        var smtp = sender.getJavaMailProperties();
        smtp.setProperty("mail.smtp.auth", "true");
        smtp.setProperty("mail.smtp.starttls.enable", "true");
        smtp.setProperty("mail.smtp.starttls.required", "true");
        smtp.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        smtp.setProperty("mail.smtp.connectiontimeout", "5000");
        smtp.setProperty("mail.smtp.timeout", "10000");
        smtp.setProperty("mail.smtp.writetimeout", "10000");
        return sender;
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
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(properties.from());
            mail.setTo(event.recipient());
            mail.setSubject("Resposta ao chamado #" + event.ticketId() + " — "
                    + event.subject().replace('\r', ' ').replace('\n', ' '));
            mail.setText("Você recebeu uma resposta do suporte Campos Personalizados.\n\n"
                    + "Chamado #" + event.ticketId() + ": " + event.subject() + "\n\n"
                    + event.message() + "\n\n"
                    + "Para acompanhar e responder, acesse o aplicativo pelo painel da Nuvemshop e abra:\n"
                    + baseUrl + "/support/tickets/" + event.ticketId()
                    + "\n\nResponda pelo chamado no aplicativo. Este e-mail é uma notificação automática.");
            sender.send(mail);
            LOGGER.info("support.email_sent ticket_id={}", event.ticketId());
        } catch (RuntimeException ex) {
            // Exceções do provedor podem incluir conteúdo ou dados do destinatário.
            LOGGER.warn("support.email_failed ticket_id={} type={}", event.ticketId(), ex.getClass().getSimpleName());
        }
    }
}
