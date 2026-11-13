package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.*;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.BuyerStorageUsageService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
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
class BackofficeStorageUsageTest {
    static final AtomicLong IDS=new AtomicLong(991184000);
    @Autowired StoreRepository stores;
    @Autowired BuyerStorageUsageService usage;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    long id;Store store;
    @BeforeEach void setup(){id=IDS.addAndGet(2);store=new Store();store.setStoreId(id);store.setStoreName("Loja de teste");store.setPlan(PlanType.PREMIUM_PLUS);stores.saveAndFlush(store);}
    @AfterEach void cleanup(){jdbc.update("delete from buyer_uploads where store_id in (?,?)",id,id+1);stores.deleteById(id);}
    MockHttpSession backoffice(){var s=new MockHttpSession();s.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true);return s;}
    void file(long owner,String state,long bytes){
        var now=Timestamp.from(Instant.now());
        jdbc.update("insert into buyer_uploads(id,store_id,product_id,field_id,session_hash,request_id,size_bytes,state,created_at,expires_at) values(?,?,123,1,'test',?,?,?,?,?)",
            UUID.randomUUID().toString(),owner,UUID.randomUUID().toString(),bytes,state,now,Timestamp.from(Instant.now().minusSeconds(60)));
    }
    @Test void showsCurrentUsageIncludingPendingDeletionAndExpiredFilesNotYetRemoved() throws Exception {
        file(id,"ORDERED",1_000_000_000L);file(id,"DELETING",500_000_000);file(id,"RESERVED",100_000_000);file(id,"TEMPORARY",200_000_000);
        file(id,"DELETED",2_000_000_000L);file(id,"EXPIRED",3_000_000_000L);file(id+1,"ORDERED",9_000_000_000L);
        var summary=usage.summary(id);
        assertThat(summary.occupiedBytes()).isEqualTo(1_800_000_000L);assertThat(summary.availableBytes()).isEqualTo(200_000_000);
        assertThat(summary.quotaBytes()).isEqualTo(2_000_000_000L);assertThat(summary.files()).isEqualTo(4);
        assertThat(summary.percent()).isEqualTo(90);assertThat(summary.pendingBytes()).isEqualTo(500_000_000);
        mvc.perform(get("/backoffice/stores/"+id+"/storage").session(backoffice())).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("1,80 GB")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("90,0%")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Aguardando exclusão")));
    }
    @Test void handlesEmptyFreeAndOverQuotaStoresWithoutLosingExistingUsage() throws Exception {
        assertThat(usage.summary(id).files()).isZero();
        store.setPlan(PlanType.FREE);stores.saveAndFlush(store);file(id,"ORDERED",3_000_000_000L);
        assertThat(usage.summary(id).hasQuota()).isFalse();assertThat(usage.summary(id).occupiedBytes()).isEqualTo(3_000_000_000L);
        mvc.perform(get("/backoffice/stores/"+id+"/storage").session(backoffice())).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Sem cota de uploads no plano atual")));
        store.setPlan(PlanType.PREMIUM_PLUS);stores.saveAndFlush(store);
        assertThat(usage.summary(id).percent()).isEqualTo(150);assertThat(usage.summary(id).progressPercent()).isEqualTo(100);
        assertThat(usage.summary(id).availableBytes()).isZero();
    }
    @Test void requiresBackofficeAndReturnsNotFoundForUnknownStore() throws Exception {
        var merchant=new MockHttpSession();merchant.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,id);
        mvc.perform(get("/backoffice/stores/"+id+"/storage").session(merchant)).andExpect(redirectedUrl("/backoffice/login"));
        mvc.perform(get("/backoffice/stores/"+id+"/storage")).andExpect(redirectedUrl("/backoffice/login"));
        mvc.perform(get("/backoffice/stores/"+(id+1)+"/storage").session(backoffice())).andExpect(status().isNotFound());
    }
}
