package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PaymentSubscription;
import br.com.nuvemcustomfields.entity.Store;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BuyerUploadOfferService {
    private final boolean enabled;
    public BuyerUploadOfferService(@Value("${buyer-uploads.new-offer-enabled:false}") boolean enabled){this.enabled=enabled;}
    /** Called only when creating the first local subscription, never on refresh/upgrade. */
    public void captureNewSubscription(PaymentSubscription subscription,Store store) {
        if(enabled && subscription.getId()==null && !store.getPlan().isBillable() && !store.isCourtesyPremium())
            subscription.setBuyerUploadOffer(BuyerUploadPolicy.OFFER);
    }
}
