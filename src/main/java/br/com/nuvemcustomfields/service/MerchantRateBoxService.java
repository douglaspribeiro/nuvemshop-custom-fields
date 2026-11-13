package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantRateBoxService {
    public static final String REVIEW_URL="https://www.nuvemshop.com.br/loja-aplicativos-nuvem/campos-personalizados7/rating";
    private final StoreRepository stores;
    private final PaymentSubscriptionRepository subscriptions;
    private final MerchantRatingRepository ratings;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public MerchantRateBoxService(StoreRepository stores,PaymentSubscriptionRepository subscriptions,MerchantRatingRepository ratings) {
        this(stores,subscriptions,ratings,Clock.systemUTC());
    }
    MerchantRateBoxService(StoreRepository stores,PaymentSubscriptionRepository subscriptions,MerchantRatingRepository ratings,Clock clock) {
        this.stores=stores;this.subscriptions=subscriptions;this.ratings=ratings;this.clock=clock;
    }
    public record Display(String id) {}
    public record Saved(int rating,String reviewUrl) {}

    private boolean eligible(Store store,Instant now) {
        var sub=subscriptions.findByStoreId(store.getStoreId()).orElse(null);
        if(!store.isActive() || !store.getEffectivePlan().isBillable() || store.isManualPlanOverride()
                || store.isCourtesyPremium() || store.isBillingSuspended() || sub==null
                || sub.getStatus()!=PaymentSubscriptionStatus.ACTIVE || !sub.isAccessActive()
                || sub.getProviderEnvironment()!=PaymentEnvironment.PRODUCTION || sub.getProvider()==PaymentProviderType.DODO_TEST
                || !sub.getPlan().isBillable() || sub.isCancellationPending() || sub.getCancellationEffectiveAt()!=null
                || sub.getRenewalFailureDetectedAt()!=null || sub.getWebhookDowngradeAfter()!=null
                || sub.getPaidStartedAt()==null || now.isBefore(sub.getPaidStartedAt().plus(Duration.ofDays(7)))) return false;
        var end=sub.getCurrentPeriodEnd()!=null?sub.getCurrentPeriodEnd():sub.getNextPaymentAt();
        return end!=null && end.isAfter(now);
    }
    private Store lock(long storeId) {
        return stores.findActiveByStoreIdForUpdate(storeId).orElseThrow(()->new IllegalArgumentException("ratebox.unavailable"));
    }
    @Transactional
    public Optional<Display> display(long storeId) {
        var store=lock(storeId);Instant now=clock.instant();
        if(!eligible(store,now)) return Optional.empty();
        if(ratings.existsByStoreIdAndPublicReviewClickedAtIsNotNull(storeId))return Optional.empty();
        var last=ratings.findFirstByStoreIdOrderByDisplayedAtDesc(storeId).orElse(null);
        if(last!=null) {
            var nextDisplay=last.getRating()==null && last.getDismissedAt()!=null
                    ?last.getDismissedAt().plus(Duration.ofDays(14))
                    :(last.getRatedAt()!=null?last.getRatedAt():last.getDisplayedAt()).plus(Duration.ofDays(21));
            if(now.isBefore(nextDisplay))return Optional.empty();
        }
        var entry=ratings.saveAndFlush(new MerchantRating(storeId,now));return Optional.of(new Display(entry.getId()));
    }
    @Transactional
    public Saved rate(long storeId,String id,Integer score,String comment) {
        var store=lock(storeId);Instant now=clock.instant();
        if(score==null || score<1 || score>5 || comment!=null && comment.length()>2000)
            throw new IllegalArgumentException("ratebox.invalid");
        var entry=ratings.findByIdAndStoreId(id,storeId).orElseThrow(()->new IllegalArgumentException("ratebox.unavailable"));
        String text=score==5 || comment==null || comment.isBlank()?null:comment.strip();
        if(entry.getRating()!=null) {
            if(entry.getRating().equals(score) && Objects.equals(entry.getComment(),text))return saved(score);
            throw new IllegalArgumentException("ratebox.unavailable");
        }
        if(!eligible(store,now) || entry.getDismissedAt()!=null
                || !now.isBefore(entry.getDisplayedAt().plus(Duration.ofDays(21))))throw new IllegalArgumentException("ratebox.unavailable");
        entry.rate(score,text,now);ratings.saveAndFlush(entry);return saved(score);
    }
    private Saved saved(int score){return new Saved(score,score==5?REVIEW_URL:null);}
    @Transactional
    public void publicReviewClicked(long storeId,String id) {
        lock(storeId);
        var entry=ratings.findByIdAndStoreId(id,storeId).orElseThrow(()->new IllegalArgumentException("ratebox.unavailable"));
        if(!Integer.valueOf(5).equals(entry.getRating()))throw new IllegalArgumentException("ratebox.unavailable");
        entry.markPublicReviewClicked(clock.instant());ratings.saveAndFlush(entry);
    }
    @Transactional
    public void dismiss(long storeId,String id) {
        lock(storeId);
        var entry=ratings.findByIdAndStoreId(id,storeId).orElseThrow(()->new IllegalArgumentException("ratebox.unavailable"));
        entry.dismiss(clock.instant());ratings.save(entry);
    }
}
