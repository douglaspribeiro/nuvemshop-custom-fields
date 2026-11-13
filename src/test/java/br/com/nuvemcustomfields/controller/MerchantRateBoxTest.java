package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.*;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class MerchantRateBoxTest {
    static final AtomicLong IDS=new AtomicLong(991183000);
    @Autowired StoreRepository stores;
    @Autowired PaymentSubscriptionRepository subscriptions;
    @Autowired MerchantRatingRepository ratings;
    @Autowired MerchantRateBoxService rateBox;
    @Autowired StoreDataErasureService erasure;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    long storeId;MockHttpSession session;
    @BeforeEach void setup(){
        storeId=IDS.incrementAndGet();var store=new Store();store.setStoreId(storeId);store.setPlan(PlanType.PREMIUM_PLUS);store.setStoreCountryCode("BR");stores.saveAndFlush(store);
        var sub=new PaymentSubscription();sub.setStoreId(storeId);sub.setProvider(PaymentProviderType.CREEM);sub.setProviderSubscriptionId("rate_sub_"+storeId);
        sub.setExternalReference("rate_"+storeId);sub.setCountryCode("BR");sub.setCurrency("USD");sub.setAmountValue(java.math.BigDecimal.TEN);
        sub.setPlan(PlanType.PREMIUM_PLUS);sub.setStatus(PaymentSubscriptionStatus.ACTIVE);sub.setAccessActive(true);
        sub.setCurrentPeriodEnd(Instant.now().plus(Duration.ofDays(20)));sub.markPaidStartedAt(Instant.now().minus(Duration.ofDays(8)));subscriptions.saveAndFlush(sub);
        session=new MockHttpSession();session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,storeId);
    }
    @AfterEach void cleanup(){erasure.erase(storeId);}
    String display() throws Exception {
        var response=mvc.perform(post("/admin/rate-box/display").session(session).header("X-NCF-Action","rate-box"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("id").asText();
    }
    @Test void persistsFeedbackAcrossSessionsAndErasesItWithTheStore() throws Exception {
        String id=display();
        mvc.perform(post("/admin/rate-box/"+id+"/rate").session(session).header("X-NCF-Action","rate-box")
            .contentType("application/json").content("{\"rating\":3,\"comment\":\"Melhorar o editor\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("reviewUrl").isEmpty());
        var saved=ratings.findById(id).orElseThrow();assertThat(saved.getRating()).isEqualTo(3);
        assertThat(saved.getComment()).isEqualTo("Melhorar o editor");assertThat(saved.getRatedAt()).isNotNull();assertThat(saved.getDisplayedAt()).isNotNull();
        var another=new MockHttpSession();another.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,storeId);
        mvc.perform(post("/admin/rate-box/display").session(another).header("X-NCF-Action","rate-box")).andExpect(status().isNoContent());
        erasure.erase(storeId);assertThat(ratings.findById(id)).isEmpty();
    }
    @Test void requiresSessionActionHeaderAndOwnerAndExcludesBackofficeImpersonation() throws Exception {
        mvc.perform(post("/admin/rate-box/display").header("X-NCF-Action","rate-box")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/rate-box/display").session(session)).andExpect(status().isBadRequest());
        mvc.perform(post("/admin/rate-box/display").session(session).header("X-NCF-Action","wrong")).andExpect(status().isForbidden());
        session.setAttribute(BackofficeSessionInterceptor.STORE_MODE_SESSION_KEY,true);
        mvc.perform(post("/admin/rate-box/display").session(session).header("X-NCF-Action","rate-box")).andExpect(status().isForbidden());
        session.removeAttribute(BackofficeSessionInterceptor.STORE_MODE_SESSION_KEY);
        var other=ratings.saveAndFlush(new MerchantRating(storeId+100000,Instant.now()));
        try {
            mvc.perform(post("/admin/rate-box/"+other.getId()+"/rate").session(session).header("X-NCF-Action","rate-box")
                .contentType("application/json").content("{\"rating\":5}" )).andExpect(status().isBadRequest());
            assertThat(ratings.findById(other.getId()).orElseThrow().getRating()).isNull();
        }finally{ratings.delete(other);}
    }
    @Test void closeWithoutRatingPersistsAndFractionalScoresCannotBecomeAnotherRating() throws Exception {
        String id=display();
        mvc.perform(post("/admin/rate-box/"+id+"/rate").session(session).header("X-NCF-Action","rate-box")
            .contentType("application/json").content("{\"rating\":4.5}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/admin/rate-box/"+id+"/dismiss").session(session).header("X-NCF-Action","rate-box")).andExpect(status().isOk());
        assertThat(ratings.findById(id).orElseThrow().getRating()).isNull();assertThat(ratings.findById(id).orElseThrow().getDismissedAt()).isNotNull();
        mvc.perform(post("/admin/rate-box/display").session(session).header("X-NCF-Action","rate-box")).andExpect(status().isNoContent());
    }
    @Test void publicReviewClickIsPersistedAndStopsPromptsAcrossSessions() throws Exception {
        String id=display();String path="/admin/rate-box/"+id;
        mvc.perform(post(path+"/public-review").session(session).header("X-NCF-Action","rate-box")).andExpect(status().isBadRequest());
        mvc.perform(post(path+"/rate").session(session).header("X-NCF-Action","rate-box")
            .contentType("application/json").content("{\"rating\":5}")).andExpect(status().isOk());
        mvc.perform(post(path+"/public-review").session(session).header("X-NCF-Action","wrong")).andExpect(status().isForbidden());
        mvc.perform(post(path+"/public-review").session(session).header("X-NCF-Action","rate-box")).andExpect(status().isOk());
        assertThat(ratings.findById(id).orElseThrow().getPublicReviewClickedAt()).isNotNull();
        jdbc.update("update merchant_ratings set displayed_at=?,rated_at=? where id=?",
            java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(30))),java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(30))),id);
        var another=new MockHttpSession();another.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,storeId);
        mvc.perform(post("/admin/rate-box/display").session(another).header("X-NCF-Action","rate-box")).andExpect(status().isNoContent());
    }
    @Test void parallelVisitsProduceExactlyOneDisplayForTheStore() throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)){
            var start=new CountDownLatch(1);
            Callable<Optional<MerchantRateBoxService.Display>> call=()->{start.await();return rateBox.display(storeId);};
            var first=executor.submit(call);var second=executor.submit(call);start.countDown();
            assertThat(java.util.stream.Stream.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS)).filter(Optional::isPresent).count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from merchant_ratings where store_id=?",Long.class,storeId)).isEqualTo(1);
        }
    }
    @Test void overviewHasLocalizedAccessibleStarsAndIsHiddenForOperatorMode() throws Exception {
        String html=mvc.perform(get("/admin").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("merchant-rate-box","Que nota você dá", "maxlength=\"2000\"");
        assertThat(ratings.findFirstByStoreIdOrderByDisplayedAtDesc(storeId)).isEmpty();
        var store=stores.findByStoreId(storeId).orElseThrow();store.setStoreCountryCode("AR");stores.saveAndFlush(store);
        html=mvc.perform(get("/admin").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Tu opinión importa","¿Qué nota le das");
        session.setAttribute(BackofficeSessionInterceptor.STORE_MODE_SESSION_KEY,true);
        html=mvc.perform(get("/admin").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("merchant-rate-box");
    }
}
