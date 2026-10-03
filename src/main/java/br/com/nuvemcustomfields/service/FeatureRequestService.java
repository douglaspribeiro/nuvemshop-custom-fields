package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.FeatureRequest;
import br.com.nuvemcustomfields.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;
import java.util.List;

@Service
public class FeatureRequestService {
    private final FeatureRequestRepository requests;
    private final StoreRepository stores;
    private final DiscordSupportWebhookClient discord;
    private final TransactionTemplate tx;
    public FeatureRequestService(FeatureRequestRepository requests,StoreRepository stores,DiscordSupportWebhookClient discord,PlatformTransactionManager manager){this.requests=requests;this.stores=stores;this.discord=discord;this.tx=new TransactionTemplate(manager);}
    @Transactional public FeatureRequest submit(Long storeId,String title,String description,String token){
        var store=stores.findActiveByStoreIdForUpdate(storeId).orElseThrow(()->new IllegalArgumentException("Loja ativa não encontrada."));
        var existing=requests.findBySubmissionToken(token).orElse(null);
        if(existing!=null){if(!storeId.equals(existing.getStoreId()))throw new IllegalArgumentException("Envio inválido.");return existing;}
        if(token==null || !token.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Envio inválido.");
        title=normalize(title,160);description=normalize(description,4000);
        if(requests.countByStoreIdAndCreatedAtAfter(storeId,Instant.now().minusSeconds(3600))>=5)throw new IllegalArgumentException("Aguarde antes de enviar outra sugestão.");
        var request=new FeatureRequest();request.setStoreId(storeId);request.setTitle(title);request.setDescription(description);request.setSubmissionToken(token);request.setPlanAtSubmission(store.getEffectivePlan());
        return requests.save(request);
    }
    public List<FeatureRequest> forStore(Long id){return requests.findTop50ByStoreIdOrderByCreatedAtDesc(id);}
    public List<FeatureRequest> recent(){return requests.findTop100ByOrderByCreatedAtDesc();}
    private static String normalize(String value,int max){if(value==null || value.isBlank() || value.strip().length()>max)throw new IllegalArgumentException("Preencha a sugestão respeitando o limite de caracteres.");return value.strip();}

    @Scheduled(fixedDelayString="${suggestions.notification-delay-ms:60000}")
    public void notifyPending(){
        if(!discord.configured())return;
        for(var request:requests.findTop50ByDiscordSentAtIsNullAndNextNotificationAtLessThanEqualOrderByCreatedAtAsc(Instant.now())){
            try{tx.executeWithoutResult(status->{
                var current=requests.lockById(request.getId()).orElse(null);
                if(current==null || current.getDiscordSentAt()!=null || current.getNextNotificationAt().isAfter(Instant.now()))return;
                var store=stores.findByStoreId(current.getStoreId()).orElse(null);
                if(store==null)return;
                current.setNotificationAttempts(current.getNotificationAttempts()+1);
                try{discord.sendFeatureRequest(store,current);current.setDiscordSentAt(Instant.now());}
                catch(RuntimeException ex){current.setNextNotificationAt(Instant.now().plusSeconds(Math.min(3600,60L<<Math.min(6,current.getNotificationAttempts()-1))));
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("suggestion.discord.pending request_id={} type={}",current.getId(),ex.getClass().getSimpleName());}
                requests.save(current);
            });}catch(RuntimeException ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("suggestion.notification.failed request_id={} type={}",request.getId(),ex.getClass().getSimpleName());}
        }
    }
}
