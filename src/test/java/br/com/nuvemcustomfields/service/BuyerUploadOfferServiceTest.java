package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuyerUploadOfferServiceTest {
    @Test void onlyNewContractsAfterExplicitReleaseCaptureTheOffer() {
        var store=new Store();var legacy=new PaymentSubscription();
        new BuyerUploadOfferService(false).captureNewSubscription(legacy,store);
        assertNull(legacy.getBuyerUploadOffer());
        store.setPlan(PlanType.PREMIUM_PLUS);
        new BuyerUploadOfferService(true).captureNewSubscription(legacy,store);
        assertNull(legacy.getBuyerUploadOffer());
        store.setPlan(PlanType.FREE);
        var fresh=new PaymentSubscription();new BuyerUploadOfferService(true).captureNewSubscription(fresh,store);
        assertEquals(BuyerUploadPolicy.OFFER,fresh.getBuyerUploadOffer());
    }
}
