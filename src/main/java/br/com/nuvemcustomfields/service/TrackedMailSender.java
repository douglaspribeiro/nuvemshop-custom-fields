package br.com.nuvemcustomfields.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.slf4j.LoggerFactory;

/** Records each SMTP attempt without resending after an uncertain result. */
public class TrackedMailSender extends JavaMailSenderImpl {
    private final StoreEmailHistoryService history;
    public TrackedMailSender(StoreEmailHistoryService history) { this.history=history; }

    @Override protected void doSend(MimeMessage[] messages,Object[] originals) throws MailException {
        for (MimeMessage message:messages) {
            String id;
            try { message.saveChanges(); id=history.begin(message); }
            catch(Exception ex) { throw new MailPreparationException("Não foi possível registrar o envio de e-mail."); }
            try { deliver(message); }
            catch(RuntimeException ex) { finish(id,false); throw ex; }
            finish(id,true);
        }
    }
    protected void deliver(MimeMessage message) { super.doSend(new MimeMessage[]{message},null); }
    private void finish(String id,boolean accepted) {
        try { history.finish(id,accepted); }
        catch(RuntimeException ex) {
            // A persistence error after SMTP must not turn a successful send into a retry.
            LoggerFactory.getLogger(getClass()).error("email.history_update_failed email_id={} accepted={} type={}",id,accepted,ex.getClass().getSimpleName());
        }
    }
}
