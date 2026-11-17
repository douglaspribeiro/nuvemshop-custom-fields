package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.mail.Session;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"app.background.enabled=false","winback.discount.enabled=true","winback.discount.allow-production=false",
        "winback.mail-enabled=true","notifications.ses.host=smtp.example.test","notifications.ses.port=587",
        "notifications.ses.username=test","notifications.ses.password=test","notifications.ses.from=support@example.test",
        "notifications.ses-events.configuration-set=tracking-test"})
@AutoConfigureMockMvc
class DodoWinbackIntegrationTest {
    private static final long ID=990088900L;
    @Autowired StoreRepository stores;
    @Autowired WinbackCampaignRepository campaigns;
    @Autowired WinbackCouponRepository coupons;
    @Autowired WinbackEmailRepository emails;
    @Autowired PaymentCatalogPriceRepository prices;
    @Autowired PlanEventRepository events;
    @Autowired PaymentSubscriptionRepository subscriptions;
    @Autowired PaymentNotificationOutboxRepository notifications;
    @Autowired WinbackTrackingService tracking;
    @Autowired WinbackCampaignService campaignService;
    @Autowired WinbackFollowupPublisher publisher;
    @Autowired DodoCouponService checkoutCoupons;
    @Autowired PaymentSubscriptionService payments;
    @Autowired StoreDataErasureService erasure;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockBean DodoTestGateway gateway;
    @MockBean DodoLiveGateway live;
    @MockBean EfiGateway efi;
    @MockBean PaymentGatewayRouter router;
    @MockBean JavaMailSender sender;
    @MockBean br.com.nuvemcustomfields.properties.HomologProperties homolog;
    Store store;WinbackCampaign campaign;WinbackEmail feedback;
    final List<Long> priceIds=new ArrayList<>();

    @BeforeEach void setup() {
        when(homolog.allows(ID)).thenReturn(true);
        when(gateway.provider()).thenReturn(PaymentProviderType.DODO_TEST);when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(gateway.operational()).thenReturn(true);when(gateway.configured()).thenReturn(true);
        when(gateway.planAvailable(any(),any())).thenReturn(true);when(gateway.currency(any())).thenReturn("USD");
        when(gateway.amount(any(),any())).thenReturn(BigDecimal.TEN);
        when(gateway.priceId(any(),any())).thenAnswer(i->"pdt_"+((PlanType)i.getArgument(1)).name());
        when(gateway.previewFirstAmount(any(),any(),anyString())).thenReturn(new BigDecimal("5.00"));
        when(router.forStore(any())).thenReturn(Optional.of(gateway));when(router.requireForStore(any())).thenReturn(gateway);
        when(router.require(PaymentProviderType.DODO_TEST)).thenReturn(gateway);
        when(sender.createMimeMessage()).thenAnswer(i->new MimeMessage(Session.getInstance(new Properties())));
        store=new Store();store.setStoreId(ID);store.setStoreName("Loja retorno");store.setStoreCountryCode("CO");store.setStoreCurrency("USD");
        store.setStoreEmail("owner@example.test");store.setUninstalledAt(Instant.now().minusSeconds(1000).truncatedTo(ChronoUnit.MICROS));stores.saveAndFlush(store);
        for(var plan:List.of(PlanType.PREMIUM,PlanType.PREMIUM_PLUS,PlanType.PREMIUM_ULTRA)) {
            var price=new PaymentCatalogPrice();price.setProvider(PaymentProviderType.DODO_TEST);price.setEnvironment(PaymentEnvironment.SANDBOX);
            price.setCountryCode("CO");price.setPlan(plan);price.setCurrency("USD");price.setAmountValue(BigDecimal.TEN);
            price.setProviderPriceId("pdt_"+plan.name());price.setEnabled(true);price.setValidatedAt(Instant.now());prices.saveAndFlush(price);priceIds.add(price.getId());
        }
        campaign=tracking.record(store);feedback=new WinbackEmail(campaign.getId(),"FEEDBACK");feedback.sent();emails.saveAndFlush(feedback);
        when(gateway.synchronizeWinbackDiscount(any(),anyList())).thenAnswer(i->{
            var coupon=(WinbackCoupon)i.getArgument(0);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(coupons.findById(coupon.getCode())).as("intent committed before HTTP").isPresent();
            return nativeCoupon(coupon);
        });
        when(gateway.discountByCode(anyString())).thenAnswer(i->coupons.findById(i.getArgument(0)).map(this::nativeCoupon));
    }
    @AfterEach void cleanup() {
        jdbc.update("delete from winback_email_events where email_id in (select id from winback_emails where campaign_id=?)",campaign.getId());
        jdbc.update("delete from winback_emails where campaign_id=?",campaign.getId());jdbc.update("delete from winback_coupons where store_id=?",ID);
        jdbc.update("delete from winback_campaigns where store_id=?",ID);jdbc.update("delete from payment_notification_outbox where store_id=?",ID);
        jdbc.update("delete from plan_events where store_id=?",ID);jdbc.update("delete from payment_attempts where store_id=?",ID);
        jdbc.update("delete from payment_subscriptions where store_id=?",ID);jdbc.update("delete from stores where store_id=?",ID);
        priceIds.forEach(prices::deleteById);
    }
    private ObjectNode nativeCoupon(WinbackCoupon coupon) {
        var n=json.createObjectNode();n.put("discount_id","dsc_winback");n.put("code",coupon.getCode());n.put("type","percentage");
        n.put("amount",5000);n.put("subscription_cycles",1);n.put("usage_limit",1);n.put("times_used",0);
        n.putNull("starts_at");n.put("expires_at",coupon.getExpiresAt().toString());n.put("customer_eligibility","any");
        n.putNull("per_customer_usage_limit");n.put("preserve_on_plan_change",false);
        n.putObject("metadata").put("ncf_winback_coupon",coupon.getCode()).put("ncf_winback_campaign",coupon.getCampaignId());
        var products=n.putArray("restricted_to");List.of(PlanType.PREMIUM,PlanType.PREMIUM_PLUS,PlanType.PREMIUM_ULTRA).forEach(p->products.add("pdt_"+p.name()));
        return n;
    }
    private void reply() {campaignService.respond(feedback.getId(),"PRICE","",false);}
    private WinbackCoupon coupon() {return coupons.findByStoreId(ID).orElseThrow();}
    private WinbackEmail followup() {return emails.findByCampaignIdAndStep(campaign.getId(),"FOLLOWUP").orElseThrow();}
    private void reinstall() {var s=stores.findByStoreId(ID).orElseThrow();s.setUninstalledAt(null);s.setAccessToken("test");stores.saveAndFlush(s);tracking.reinstalled(ID);}
    private String mailText(Object body) throws Exception {
        if(body instanceof String text)return text;
        if(body instanceof Multipart parts){var out=new StringBuilder();for(int i=0;i<parts.getCount();i++)out.append(mailText(parts.getBodyPart(i).getContent()));return out.toString();}
        return "";
    }

    @Test void replyCommitsOneIntentAndDoesNotClaimAnOfferBeforeNativeConfirmation() {
        reply();var c=coupon();assertThat(c.getProvider()).isEqualTo(PaymentProviderType.DODO_TEST);assertThat(c.getNativeDiscountId()).isNull();
        assertThat(campaignService.publicCoupon(feedback.getId())).isNull();assertThat(campaignService.prepareFollowup(followup().getId())).isNull();
        assertThat(followup().getStatus()).isEqualTo("PREPARED");verify(gateway,never()).synchronizeWinbackDiscount(any(),anyList());
        verify(sender,never()).send(any(MimeMessage.class));
    }
    @Test void couponIsConfirmedBeforeSpanishOfferAndOnlyOneEmailIsSent() throws Exception {
        reply();
        doAnswer(i->{assertThat(coupon().getNativeDiscountId()).isEqualTo("dsc_winback");assertThat(coupon().getNativeConfirmedAt()).isNotNull();return null;})
                .when(sender).send(any(MimeMessage.class));
        publisher.sendDue();publisher.sendDue();assertThat(followup().getStatus()).isEqualTo("SENT");
        var capture=org.mockito.ArgumentCaptor.forClass(MimeMessage.class);verify(sender,times(1)).send(capture.capture());
        assertThat(capture.getValue().getSubject()).contains("Tu descuento");
        assertThat(mailText(capture.getValue().getContent())).contains(coupon().getCode(),"50%","primera mensualidad","Dodo Payments");
        verify(gateway,times(1)).synchronizeWinbackDiscount(any(),argThat(ids->ids.size()==3));
        var html=mvc.perform(get("/winback/"+feedback.getId())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains(coupon().getCode(),"Dodo Payments").doesNotContain("por cartão na Efí");
    }
    @Test void unknownNativeResponseRetainsSameCodeAndRetriesBeforeSending() {
        reply();String code=coupon().getCode();
        doThrow(new PaymentGatewayException("response lost")).when(gateway).synchronizeWinbackDiscount(any(),anyList());
        publisher.sendDue();publisher.sendDue();
        assertThat(followup().getStatus()).isEqualTo("PREPARED");assertThat(coupon().getNativeSyncAttempts()).isEqualTo(1);
        assertThat(coupon().getCode()).isEqualTo(code);assertThat(campaignService.publicCoupon(feedback.getId())).isNull();
        verify(sender,never()).send(any(MimeMessage.class));verify(gateway,times(1)).synchronizeWinbackDiscount(any(),anyList());
        var c=coupon();org.springframework.test.util.ReflectionTestUtils.setField(c,"nativeSyncAfter",Instant.now().minusSeconds(1));coupons.saveAndFlush(c);
        doAnswer(i->nativeCoupon(i.getArgument(0))).when(gateway).synchronizeWinbackDiscount(any(),anyList());
        publisher.sendDue();assertThat(followup().getStatus()).isEqualTo("SENT");assertThat(coupon().getCode()).isEqualTo(code);
    }
    @Test void incorrectNativeDiscountNeverBecomesAnEmailOffer() {
        reply();doAnswer(i->{var n=nativeCoupon(i.getArgument(0));n.put("subscription_cycles",2);return n;})
                .when(gateway).synchronizeWinbackDiscount(any(),anyList());
        publisher.sendDue();assertThat(followup().getStatus()).isEqualTo("PREPARED");assertThat(coupon().getNativeDiscountId()).isNull();
        verify(sender,never()).send(any(MimeMessage.class));
    }
    @Test void optingOutDuringNativeCallCancelsTheOffer() {
        reply();doAnswer(i->{campaignService.respond(feedback.getId(),"","",true);return nativeCoupon(i.getArgument(0));})
                .when(gateway).synchronizeWinbackDiscount(any(),anyList());
        publisher.sendDue();assertThat(followup().getStatus()).isEqualTo("CANCELLED");assertThat(campaignService.publicCoupon(feedback.getId())).isNull();
        verify(sender,never()).send(any(MimeMessage.class));
    }
    @Test void reinstallingDuringNativeCallCancelsTheEmail() {
        reply();doAnswer(i->{reinstall();return nativeCoupon(i.getArgument(0));}).when(gateway).synchronizeWinbackDiscount(any(),anyList());
        publisher.sendDue();assertThat(followup().getStatus()).isEqualTo("CANCELLED");verify(sender,never()).send(any(MimeMessage.class));
    }
    @Test void erasureDuringNativeCallDoesNotRecreateDataOrSendEmail() {
        reply();doAnswer(i->{var n=nativeCoupon(i.getArgument(0));erasure.erase(ID);return n;}).when(gateway).synchronizeWinbackDiscount(any(),anyList());
        publisher.sendDue();assertThat(stores.findByStoreId(ID)).isEmpty();assertThat(coupons.findByStoreId(ID)).isEmpty();
        assertThat(emails.findByCampaignIdAndStep(campaign.getId(),"FOLLOWUP")).isEmpty();verify(sender,never()).send(any(MimeMessage.class));
    }
    @Test void aStoreOutsideTheReleaseListDoesNotGenerateACouponOrReceiveAnOffer() {
        when(homolog.allows(ID)).thenReturn(false);reply();publisher.sendDue();
        assertThat(coupons.findByStoreId(ID)).isEmpty();assertThat(followup().getStatus()).isEqualTo("PREPARED");
        verify(gateway,never()).synchronizeWinbackDiscount(any(),anyList());verify(sender,never()).send(any(MimeMessage.class));
    }

    @Test void priorPaymentDoesNotCreateAnyDiscount() {
        var e=new PlanEvent();e.setStoreId(ID);e.setFromPlan(PlanType.FREE);e.setToPlan(PlanType.PREMIUM);e.setSource("PAYMENT_WEBHOOK");events.saveAndFlush(e);
        reply();publisher.sendDue();assertThat(coupons.findByStoreId(ID)).isEmpty();verify(gateway,never()).synchronizeWinbackDiscount(any(),anyList());
    }
    @Test void productionStillRequiresExplicitDiscountAuthorization() {
        when(live.provider()).thenReturn(PaymentProviderType.DODO);when(live.environment()).thenReturn(PaymentEnvironment.PRODUCTION);
        when(live.operational()).thenReturn(true);when(router.forStore(any())).thenReturn(Optional.of(live));
        reply();publisher.sendDue();assertThat(coupons.findByStoreId(ID)).isEmpty();verify(live,never()).synchronizeWinbackDiscount(any(),anyList());
    }
    @Test void repeatedRepliesKeepOneCouponAndOneFollowup() {
        reply();String code=coupon().getCode();reply();assertThat(coupon().getCode()).isEqualTo(code);
        assertThat(emails.findByCampaignIdOrderByCreatedAtAsc(campaign.getId())).hasSize(2);publisher.sendDue();publisher.sendDue();
        verify(gateway,times(1)).synchronizeWinbackDiscount(any(),anyList());verify(sender,times(1)).send(any(MimeMessage.class));
    }
    @Test void couponAutomaticallyAppliesAfterReinstallAndIsConsumedByVerifiedPayment() throws Exception {
        reply();publisher.sendDue();reinstall();String code=coupon().getCode();
        var session=new MockHttpSession();session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,ID);
        var html=mvc.perform(get("/admin/billing/pay/dodo").session(session).param("plan","PREMIUM"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains(code,"5,00","10,00");
        when(gateway.createCheckout(any(),eq(PlanType.PREMIUM),anyString(),anyString(),eq(code)))
                .thenReturn(new GatewayCheckout(null,"cks_return","https://test.checkout.dodopayments.com/cks_return","pending"));
        payments.startDodoCheckout(ID,PlanType.PREMIUM,code,new BigDecimal("5.00"),BigDecimal.TEN);
        var local=subscriptions.findByStoreId(ID).orElseThrow();String ref=local.getExternalReference();
        assertThat(checkoutCoupons.reservedCode(ref)).isEqualTo(code);
        Instant end=Instant.now().plusSeconds(2592000);
        var remote=new GatewaySubscription("sub_return",null,ref,"active","USD",BigDecimal.TEN,end,"cus_return","pdt_PREMIUM",Instant.now(),end,null);
        var invoice=new GatewayInvoice("pay_return","sub_return","pay_return","paid","USD",new BigDecimal("5.00"));
        when(gateway.getSubscription("sub_return")).thenReturn(remote);when(gateway.getLatestInvoice("sub_return")).thenReturn(Optional.of(invoice));
        payments.synchronizeFromSubscription(PaymentProviderType.DODO_TEST,"sub_return");
        assertThat(coupon().getUsedAt()).isNotNull();assertThat(campaigns.findById(campaign.getId()).orElseThrow().getConvertedAt()).isNotNull();
        assertThat(subscriptions.findByStoreId(ID).orElseThrow().getAmountValue()).isEqualByComparingTo("10.00");
        assertThat(notifications.findAll()).singleElement().extracting(PaymentNotificationOutbox::getAmountValue).isEqualTo(new BigDecimal("5.00"));
        verify(efi,never()).updateRecurringAmount(anyString(),any(),any());
    }
    @Test void anotherStoreCannotUseTheGeneratedCode() {
        reply();publisher.sendDue();reinstall();var other=new Store();other.setStoreId(ID+1);other.setStoreCountryCode("CO");
        assertThatThrownBy(()->checkoutCoupons.quote(other,PlanType.PREMIUM,coupon().getCode())).isInstanceOf(IllegalArgumentException.class);
    }
}
