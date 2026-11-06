package br.com.nuvemcustomfields.service;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class StoreEmailHistoryService {
    private final JdbcTemplate jdbc;
    public StoreEmailHistoryService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public static void identify(MimeMessage mail, long storeId, String category, String sourceKey) throws MessagingException {
        mail.setHeader("X-NCF-Store-ID", Long.toString(storeId));
        mail.setHeader("X-NCF-Email-Category", category);
        if (sourceKey!=null) mail.setHeader("X-NCF-Email-Source",sourceKey);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public String begin(MimeMessage mail) throws Exception {
        String store=mail.getHeader("X-NCF-Store-ID",null);
        if (store==null) {
            String ticket=mail.getHeader("X-NCF-Ticket-ID",null);
            if (ticket!=null && ticket.matches("[1-9][0-9]{0,17}")) {
                var ids=jdbc.queryForList("select store_id from support_tickets where id=?",Long.class,Long.valueOf(ticket));
                if (!ids.isEmpty()) store=ids.getFirst().toString();
            }
        }
        if (store==null || !store.matches("[1-9][0-9]{0,17}")) throw new IllegalStateException("Email without store identity");
        String id=UUID.randomUUID().toString();
        var recipients=mail.getAllRecipients();
        String recipient=recipients==null ? null : String.join(", ", Arrays.stream(recipients).map(Object::toString).toList());
        String category=mail.getHeader("X-NCF-Email-Category",null);
        String source=mail.getHeader("X-NCF-Email-Source",null);
        if ("ACTIVATION".equals(category) && source!=null && source.matches("activation:[1-9][0-9]{0,17}")) {
            var steps=jdbc.queryForList("select step from nc39_activation_email_outbox where id=?",String.class,Long.valueOf(source.substring(11)));
            if(!steps.isEmpty()) category="ACTIVATION_"+steps.getFirst();
        }
        jdbc.update("""
            insert into store_email_history(id,store_id,category,source_key,recipient,subject,body,state,attempted_at)
            values(?,?,?,?,?,?,?,'SENDING',?)
            """,id,Long.valueOf(store),category,
            mail.getHeader("X-NCF-Email-Source",null),recipient,mail.getSubject(),redact(text(mail)),Timestamp.from(Instant.now()));
        return id;
    }

    // Never persist unsubscribe secrets, even in a backoffice-only body snapshot.
    static String redact(String text) {
        return text==null ? null : text.replaceAll("(?i)([?&](?:token|access_token)=)[^\\s<>\\\"]+", "$1[oculto]");
    }
    private static String text(Part part) throws Exception {
        if (part.isMimeType("text/plain")) return (String)part.getContent();
        if (part.isMimeType("multipart/*")) {
            var multipart=(Multipart)part.getContent();
            for(int i=0;i<multipart.getCount();i++) { String result=text(multipart.getBodyPart(i)); if(result!=null)return result; }
        }
        return null;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void finish(String id, boolean accepted) {
        jdbc.update("update store_email_history set state=?,accepted_at=? where id=?",
                accepted ? "ACCEPTED" : "UNKNOWN",accepted ? Timestamp.from(Instant.now()) : null,id);
    }

    public record Entry(String id,String category,String recipient,String subject,String body,String state,
            Instant attemptedAt,Instant acceptedAt,String providerEvent) {
        public String categoryLabel() {
            if(category.startsWith("ACTIVATION_")) return "Ativação · "+category.substring(11);
            if(category.startsWith("WINBACK_")) return "Reconquista · "+category.substring(8);
            return switch(category) {case "SUPPORT"->"Resposta de suporte";case "ACTION"->"Aviso com ação";
                case "RENEWAL_FAILURE"->"Falha de renovação";case "CONFIGURATION_PAUSE"->"Configuração pausada";default->"Mensagem do backoffice";};
        }
        public String stateLabel() {return switch(state) {case "ACCEPTED"->"Envio registrado";case "UNKNOWN"->"Resultado incerto";default->"Sem confirmação de envio";};}
        public String providerLabel() {return providerEvent==null ? "Sem confirmação de entrega" : switch(providerEvent) {
            case "Delivery","DELIVERY"->"Entrega confirmada";case "Bounce","BOUNCE"->"Devolvido";case "Complaint","COMPLAINT"->"Reclamação de spam";default->providerEvent;};}
    }
    public List<Entry> page(long storeId,int page) {
        return jdbc.query("""
            select h.*,
              case when h.source_key like 'activation:%' then
                (select f.event_type from nc39_activation_email_feedback f where concat('activation:',f.event_id)=h.source_key
                 and f.event_type in ('Delivery','Bounce','Complaint') order by f.occurred_at desc,f.id desc limit 1)
              when h.source_key like 'winback:%' then
                (select f.type from winback_email_events f where concat('winback:',f.email_id)=h.source_key
                 and f.type in ('Delivery','Bounce','Complaint') order by f.occurred_at desc,f.id desc limit 1)
              else null end as provider_event
            from store_email_history h where h.store_id=? order by h.attempted_at desc,h.id desc limit 26 offset ?
            """,(r,n)->new Entry(r.getString("id"),r.getString("category"),r.getString("recipient"),r.getString("subject"),
                r.getString("body"),r.getString("state"),r.getTimestamp("attempted_at").toInstant(),
                r.getTimestamp("accepted_at")==null?null:r.getTimestamp("accepted_at").toInstant(),r.getString("provider_event")),storeId,page*25);
    }
}
