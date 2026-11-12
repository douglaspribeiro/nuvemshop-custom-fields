package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.FeatureRequest;
import br.com.nuvemcustomfields.entity.FeatureRequestStatus;
import br.com.nuvemcustomfields.entity.FeatureRequestUpdate;
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
    private final FeatureRequestUpdateRepository updates;
    public FeatureRequestService(FeatureRequestRepository requests,StoreRepository stores,DiscordSupportWebhookClient discord,PlatformTransactionManager manager,FeatureRequestUpdateRepository updates){this.requests=requests;this.stores=stores;this.discord=discord;this.tx=new TransactionTemplate(manager);this.updates=updates;}
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
    @Transactional(readOnly=true) public List<FeatureRequest> forStore(Long id){return withHistory(requests.findTop50ByStoreIdOrderByCreatedAtDesc(id));}
    @Transactional(readOnly=true) public List<FeatureRequest> forStore(Long id,Long highlightedId){
        var list=new java.util.ArrayList<>(requests.findTop50ByStoreIdOrderByCreatedAtDesc(id));
        if(highlightedId!=null && list.stream().noneMatch(r->r.getId().equals(highlightedId)))
            requests.findByIdAndStoreId(highlightedId,id).ifPresent(r->{if(list.size()==50)list.removeLast();list.addFirst(r);});
        return withHistory(list);
    }
    @Transactional(readOnly=true) public List<FeatureRequest> recent(){return withHistory(requests.findTop100ByOrderByCreatedAtDesc());}
    private List<FeatureRequest> withHistory(List<FeatureRequest> list){list.forEach(r->r.getUpdates().size());return list;}

    @Transactional
    public FeatureRequestUpdate respond(Long id,FeatureRequestStatus status,String message,long revision,String token) {
        if(status==null || token==null || !token.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Atualização inválida. Abra a página novamente.");
        String body=message==null?"":message.strip();
        if(body.length()>4000) throw new IllegalArgumentException("A resposta deve ter até 4.000 caracteres.");
        var request=requests.lockById(id).orElseThrow(()->new IllegalArgumentException("Sugestão não encontrada."));
        var previous=updates.findBySubmissionToken(token).orElse(null);
        if(previous!=null) {
            if(!previous.getRequest().getId().equals(id) || previous.getStatus()!=status || !previous.getMessage().equals(body))
                throw new IllegalArgumentException("Este envio já foi utilizado com outra atualização.");
            return previous;
        }
        if(request.getRevision()!=revision) throw new IllegalArgumentException("A sugestão foi atualizada. Recarregue a página antes de responder.");
        if(body.isBlank() && status==request.getStatus()) throw new IllegalArgumentException("Escreva uma resposta ou altere o status.");
        var update=new FeatureRequestUpdate();update.setRequest(request);update.setStatus(status);update.setMessage(body);update.setSubmissionToken(token);
        update.setEmailState(body.isBlank()?"NOT_REQUIRED":"PENDING");
        request.setStatus(status);request.setRevision(revision+1);request.getUpdates().add(update);
        return updates.save(update);
    }
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
