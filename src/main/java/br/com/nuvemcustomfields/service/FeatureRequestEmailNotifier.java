package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.i18n.StoreLocale;
import br.com.nuvemcustomfields.repository.FeatureRequestUpdateRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;

/** Durable notification intent; an uncertain SMTP result is never automatically resent. */
@Service
public class FeatureRequestEmailNotifier {
    private final FeatureRequestUpdateRepository updates;
    private final StoreRepository stores;
    private final StoreAdminEmailService mail;
    private final MessageSource messages;
    private final TransactionTemplate tx;
    private final String baseUrl;
    public FeatureRequestEmailNotifier(FeatureRequestUpdateRepository updates,StoreRepository stores,StoreAdminEmailService mail,
            MessageSource messages,PlatformTransactionManager manager,@Value("${nuvemshop.app-base-url}") String baseUrl) {
        this.updates=updates;this.stores=stores;this.mail=mail;this.messages=messages;this.tx=new TransactionTemplate(manager);this.baseUrl=baseUrl.replaceAll("/+$","");
    }
    private record Delivery(Long updateId,Long requestId,Long storeId,String title,String message,String status) {}

    @Scheduled(fixedDelayString="${suggestions.response-email-delay-ms:60000}")
    public void notifyPending() {
        if(!mail.configured())return;
        for(var pending:updates.findTop50ByEmailStateOrderByCreatedAtAsc("PENDING")) {
            try {
                var delivery=tx.execute(s->{
                    var update=updates.lockById(pending.getId()).orElse(null);
                    if(update==null || !"PENDING".equals(update.getEmailState()))return null;
                    update.setEmailState("SENDING");
                    var request=update.getRequest();
                    return new Delivery(update.getId(),request.getId(),request.getStoreId(),request.getTitle(),update.getMessage(),update.getStatus().name());
                });
                if(delivery==null)continue;
                boolean sent=false;
                try {
                    var store=stores.findByStoreId(delivery.storeId()).orElseThrow();
                    var locale=StoreLocale.forCountry(store.getStoreCountryCode());
                    String status=messages.getMessage("suggestions.status."+delivery.status(),null,locale);
                    String subject=messages.getMessage("suggestions.email.subject",new Object[]{delivery.requestId()},locale);
                    String body=messages.getMessage("suggestions.email.body",new Object[]{delivery.title(),status,delivery.message()},locale);
                    mail.sendAction(delivery.storeId(),subject,body,messages.getMessage("suggestions.email.action",null,locale),
                            baseUrl+"/admin/suggestions?requestId="+delivery.requestId()+"#suggestion-"+delivery.requestId(),"FEATURE_REQUEST","feature-request-update:"+delivery.updateId());
                    sent=true;
                } catch(RuntimeException ex) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("suggestion.email.failed update_id={} type={}",delivery.updateId(),ex.getClass().getSimpleName());
                }
                boolean accepted=sent;
                tx.executeWithoutResult(s->{
                    var update=updates.lockById(delivery.updateId()).orElse(null);
                    if(update!=null){update.setEmailState(accepted?"SENT":"FAILED");if(accepted)update.setEmailSentAt(Instant.now());}
                });
            } catch(RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("suggestion.email.processing_failed update_id={} type={}",pending.getId(),ex.getClass().getSimpleName());
            }
        }
    }
}
