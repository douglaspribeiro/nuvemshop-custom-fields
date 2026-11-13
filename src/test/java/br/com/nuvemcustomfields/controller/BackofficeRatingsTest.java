package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.*;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class BackofficeRatingsTest {
    static final AtomicLong IDS=new AtomicLong(991185000);
    @Autowired StoreRepository stores;
    @Autowired MerchantRatingRepository ratings;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    long id;
    @BeforeEach void setup(){id=IDS.addAndGet(2);for(long storeId:List.of(id,id+1)){var s=new Store();s.setStoreId(storeId);s.setStoreName("Loja "+storeId);s.setPlan(PlanType.PREMIUM_PLUS);stores.saveAndFlush(s);}}
    @AfterEach void cleanup(){jdbc.update("delete from merchant_ratings where store_id in (?,?)",id,id+1);stores.deleteById(id);stores.deleteById(id+1);}
    MockHttpSession admin(){var session=new MockHttpSession();session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true);return session;}
    MerchantRating rating(long owner,int score,String comment,long age){
        var entry=new MerchantRating(owner,Instant.now().minusSeconds(age+10));entry.rate(score,comment,Instant.now().minusSeconds(age));return ratings.saveAndFlush(entry);
    }
    @Test void listsSubmittedRatingsWithSafeCommentsStoreLinksDatesAndPublicClick() throws Exception {
        var low=rating(id,2,"<script>alert('private')</script>",60);
        var high=rating(id,5,null,30);high.markPublicReviewClicked(Instant.now());ratings.saveAndFlush(high);
        var closed=new MerchantRating(id,Instant.now());closed.dismiss(Instant.now());ratings.saveAndFlush(closed);
        String html=mvc.perform(get("/backoffice/ratings").session(admin()).param("storeId",String.valueOf(id)))
            .andExpect(status().isOk()).andExpect(view().name("backoffice/ratings")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("5 de 5 estrelas","2 de 5 estrelas","Loja "+id,"/backoffice/stores/"+id,"Sem comentário.","Clicou em avaliar publicamente","&lt;script&gt;");
        assertThat(html).doesNotContain("<script>alert('private')</script>","rating-"+closed.getId());
        assertThat(html.indexOf("rating-"+high.getId())).isLessThan(html.indexOf("rating-"+low.getId()));
    }
    @Test void filtersByStoreAndScoreAndHandlesEmptyResults() throws Exception {
        var keep=rating(id,4,"Melhorar o editor",10);var otherScore=rating(id,5,null,5);var otherStore=rating(id+1,4,"Outra loja",1);
        var response=mvc.perform(get("/backoffice/ratings").session(admin()).param("storeId",String.valueOf(id)).param("rating","4"))
            .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked") var rows=(List<BackofficeRatingsController.Rating>)response.getModelAndView().getModel().get("ratings");
        assertThat(rows).extracting(BackofficeRatingsController.Rating::id).containsExactly(keep.getId());
        String html=response.getResponse().getContentAsString();assertThat(html).doesNotContain("rating-"+otherScore.getId(),"rating-"+otherStore.getId());
        mvc.perform(get("/backoffice/ratings").session(admin()).param("storeId",String.valueOf(id)).param("rating","1"))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Nenhuma avaliação enviada")));
    }
    @Test void onlyBackofficeCanReadPrivateCommentsAndFiltersMustBeValid() throws Exception {
        var merchant=new MockHttpSession();merchant.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,id);
        mvc.perform(get("/backoffice/ratings").session(merchant)).andExpect(redirectedUrl("/backoffice/login"));
        mvc.perform(get("/backoffice/ratings")).andExpect(redirectedUrl("/backoffice/login"));
        for(String score:List.of("0","6"))mvc.perform(get("/backoffice/ratings").session(admin()).param("rating",score)).andExpect(status().isBadRequest());
        mvc.perform(get("/backoffice/ratings").session(admin()).param("storeId","-1")).andExpect(status().isBadRequest());
    }
}
