package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MerchantRateBoxServiceTest {
    final Instant now=Instant.parse("2026-11-12T12:00:00Z");
    final StoreRepository stores=mock(StoreRepository.class);
    final PaymentSubscriptionRepository subscriptions=mock(PaymentSubscriptionRepository.class);
    final MerchantRatingRepository ratings=mock(MerchantRatingRepository.class);
    final MerchantRateBoxService service=new MerchantRateBoxService(stores,subscriptions,ratings,Clock.fixed(now,ZoneOffset.UTC));
    Store store;PaymentSubscription sub;
    @BeforeEach void setup(){
        store=new Store();store.setStoreId(42L);store.setPlan(PlanType.PREMIUM_PLUS);
        sub=new PaymentSubscription();sub.setStoreId(42L);sub.setProvider(PaymentProviderType.CREEM);
        sub.setProviderSubscriptionId("contract");sub.setPlan(PlanType.PREMIUM_PLUS);sub.setStatus(PaymentSubscriptionStatus.ACTIVE);
        sub.setAccessActive(true);sub.setCurrentPeriodEnd(now.plus(Duration.ofDays(20)));sub.markPaidStartedAt(now.minus(Duration.ofDays(8)));
        when(stores.findActiveByStoreIdForUpdate(42L)).thenReturn(Optional.of(store));
        when(subscriptions.findByStoreId(42L)).thenReturn(Optional.of(sub));
        when(ratings.saveAndFlush(any())).thenAnswer(call->call.getArgument(0));
    }
    void start(Instant at){org.springframework.test.util.ReflectionTestUtils.setField(sub,"paidStartedAt",at);}
    @Test void requiresSevenCompletePaidDaysAndUnknownStartNeverUsesCheckoutDate(){
        start(null);assertThat(service.display(42L)).isEmpty();
        start(now.minus(Duration.ofDays(7)).plusSeconds(1));assertThat(service.display(42L)).isEmpty();
        start(now.minus(Duration.ofDays(7)));assertThat(service.display(42L)).isPresent();verify(ratings,times(1)).saveAndFlush(any());
    }
    @Test void aNewContractDoesNotInheritThePreviousSubscriptionsAge(){
        sub.setProviderSubscriptionId("new-contract");assertThat(service.display(42L)).isEmpty();
        sub.markPaidStartedAt(now);assertThat(service.display(42L)).isEmpty();
        var later=new MerchantRateBoxService(stores,subscriptions,ratings,Clock.fixed(now.plus(Duration.ofDays(7)),ZoneOffset.UTC));
        assertThat(later.display(42L)).isPresent();
    }
    @Test void neverPromptsFreeCourtesyManualOrInactiveSubscriptions(){
        store.setPlan(PlanType.FREE);assertThat(service.display(42L)).isEmpty();store.setPlan(PlanType.PREMIUM_PLUS);
        store.setManualPlanOverride(true);assertThat(service.display(42L)).isEmpty();store.setManualPlanOverride(false);
        store.setCourtesyPremium(true);assertThat(service.display(42L)).isEmpty();store.setCourtesyPremium(false);
        for(var status:List.of(PaymentSubscriptionStatus.PENDING,PaymentSubscriptionStatus.CANCELED,PaymentSubscriptionStatus.ERROR,PaymentSubscriptionStatus.PAST_DUE,PaymentSubscriptionStatus.PAUSED)){
            sub.setStatus(status);assertThat(service.display(42L)).isEmpty();
        }
        sub.setStatus(PaymentSubscriptionStatus.ACTIVE);sub.setProviderEnvironment(PaymentEnvironment.SANDBOX);assertThat(service.display(42L)).isEmpty();
        sub.setProviderEnvironment(PaymentEnvironment.PRODUCTION);sub.setAccessActive(false);assertThat(service.display(42L)).isEmpty();
        sub.setAccessActive(true);sub.setCurrentPeriodEnd(now);assertThat(service.display(42L)).isEmpty();verify(ratings,never()).saveAndFlush(any());
    }
    @Test void closedWithoutRatingReappearsAfterFourteenDaysFromDismissal(){
        var last=new MerchantRating(42L,now.minus(Duration.ofDays(21)));last.dismiss(now.minus(Duration.ofDays(14)).plusSeconds(1));
        when(ratings.findFirstByStoreIdOrderByDisplayedAtDesc(42L)).thenReturn(Optional.of(last));assertThat(service.display(42L)).isEmpty();
        last=new MerchantRating(42L,now.minus(Duration.ofDays(21)));last.dismiss(now.minus(Duration.ofDays(14)));
        when(ratings.findFirstByStoreIdOrderByDisplayedAtDesc(42L)).thenReturn(Optional.of(last));assertThat(service.display(42L)).isPresent();
    }
    @Test void ratedPromptsWaitTwentyOneDaysFromRatingAndUnansweredFromDisplay(){
        for(boolean rated:List.of(false,true)){
            var last=new MerchantRating(42L,now.minus(Duration.ofDays(21)).plusSeconds(1));
            if(rated){last.rate(5,null,now.minus(Duration.ofDays(15)));last.dismiss(now.minus(Duration.ofDays(15)));}
            when(ratings.findFirstByStoreIdOrderByDisplayedAtDesc(42L)).thenReturn(Optional.of(last));assertThat(service.display(42L)).isEmpty();
            last=new MerchantRating(42L,now.minus(Duration.ofDays(21)));
            if(rated){last.rate(5,null,now.minus(Duration.ofDays(21)));last.dismiss(now);}
            when(ratings.findFirstByStoreIdOrderByDisplayedAtDesc(42L)).thenReturn(Optional.of(last));assertThat(service.display(42L)).isPresent();
        }
    }
    @Test void onlyFiveStarsWithAPublicReviewClickStopsFutureInvitations(){
        var entry=new MerchantRating(42L,now.minus(Duration.ofDays(30)));
        when(ratings.findByIdAndStoreId(entry.getId(),42L)).thenReturn(Optional.of(entry));
        assertThatThrownBy(()->service.publicReviewClicked(42L,entry.getId())).hasMessage("ratebox.unavailable");
        entry.rate(4,null,now.minus(Duration.ofDays(25)));
        assertThatThrownBy(()->service.publicReviewClicked(42L,entry.getId())).hasMessage("ratebox.unavailable");
        entry.rate(5,null,now.minus(Duration.ofDays(25)));
        when(ratings.findFirstByStoreIdOrderByDisplayedAtDesc(42L)).thenReturn(Optional.of(entry));
        assertThat(service.display(42L)).isPresent();
        service.publicReviewClicked(42L,entry.getId());assertThat(entry.getPublicReviewClickedAt()).isEqualTo(now);
        service.publicReviewClicked(42L,entry.getId());assertThat(entry.getPublicReviewClickedAt()).isEqualTo(now);
        when(ratings.existsByStoreIdAndPublicReviewClickedAtIsNotNull(42L)).thenReturn(true);
        assertThat(service.display(42L)).isEmpty();
        assertThatThrownBy(()->service.publicReviewClicked(42L,"another-store")).hasMessage("ratebox.unavailable");
    }
    @Test void scoreFiveIsRecordedOnceAndOnlyItGetsTheOptionalPublicLink(){
        var entry=new MerchantRating(42L,now.minusSeconds(10));when(ratings.findByIdAndStoreId(entry.getId(),42L)).thenReturn(Optional.of(entry));
        var result=service.rate(42L,entry.getId(),5,"ignored");assertThat(result.reviewUrl()).isEqualTo(MerchantRateBoxService.REVIEW_URL);
        assertThat(entry.getRating()).isEqualTo(5);assertThat(entry.getComment()).isNull();assertThat(entry.getRatedAt()).isEqualTo(now);
        service.rate(42L,entry.getId(),5,null);verify(ratings,times(1)).saveAndFlush(entry);
        assertThatThrownBy(()->service.rate(42L,entry.getId(),4,"Different")).hasMessage("ratebox.unavailable");
    }
    @Test void oneToFourStoreFeedbackAndRejectInvalidOrAnotherStoresPrompt(){
        for(int score=1;score<=4;score++){
            var entry=new MerchantRating(42L,now);when(ratings.findByIdAndStoreId(entry.getId(),42L)).thenReturn(Optional.of(entry));
            assertThat(service.rate(42L,entry.getId(),score,"  Melhorar o editor  ").reviewUrl()).isNull();
            assertThat(entry.getComment()).isEqualTo("Melhorar o editor");
        }
        assertThatThrownBy(()->service.rate(42L,"another",5,null)).hasMessage("ratebox.unavailable");
        for(int score:List.of(0,6))assertThatThrownBy(()->service.rate(42L,"any",score,null)).hasMessage("ratebox.invalid");
        assertThatThrownBy(()->service.rate(42L,"any",3,"x".repeat(2001))).hasMessage("ratebox.invalid");
    }
    @Test void closingDoesNotCreateARatingAndDowngradePreventsNewSubmissions(){
        var entry=new MerchantRating(42L,now);when(ratings.findByIdAndStoreId(entry.getId(),42L)).thenReturn(Optional.of(entry));
        service.dismiss(42L,entry.getId());assertThat(entry.getDismissedAt()).isEqualTo(now);assertThat(entry.getRating()).isNull();
        assertThatThrownBy(()->service.rate(42L,entry.getId(),1,null)).hasMessage("ratebox.unavailable");
        var other=new MerchantRating(42L,now);when(ratings.findByIdAndStoreId(other.getId(),42L)).thenReturn(Optional.of(other));store.setPlan(PlanType.FREE);
        assertThatThrownBy(()->service.rate(42L,other.getId(),5,null)).hasMessage("ratebox.unavailable");
    }
}
