package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.dto.FieldForm;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.*;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"buyer-uploads.cleanup-delay-ms=10000000","buyer-uploads.reconcile-delay-ms=10000000"})
@AutoConfigureMockMvc
class BuyerUploadsTest {
    @Autowired BuyerUploadService uploads;
    @Autowired BuyerUploadLedger ledger;
    @Autowired PersonalizationAdminService admin;
    @Autowired StoreRepository stores;
    @Autowired PersonalizationFieldRepository fields;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired BuyerUploadOrders orders;
    @Autowired StoreDataErasureService erasure;
    @MockitoBean BuyerFileStorage storage;
    @MockitoBean NuvemshopApiClient api;
    private static final AtomicLong IDS=new AtomicLong(997000);
    private long store,field;
    private String token;
    private MockHttpSession session;
    private final Map<String,byte[]> objects=new HashMap<>();
    @BeforeEach void setup() {
        store=IDS.incrementAndGet();var s=new Store();s.setStoreId(store);s.setAccessToken("test");s.setPlan(PlanType.PREMIUM_PLUS);stores.saveAndFlush(s);
        jdbc.update("insert into buyer_upload_entitlements(store_id,offer_code,quota_bytes,enabled,created_at) values (?,?,?,true,?)",store,BuyerUploadPolicy.OFFER,BuyerUploadPolicy.PRO_BYTES,Timestamp.from(Instant.now()));
        admin.ensureRule(store,123L,"Caderno");var f=new FieldForm();f.setLabel("Arte");f.setFieldType(FieldType.FILE_UPLOAD);f.setRequired(true);admin.addField(store,123L,f);
        field=fields.findWithRuleByStoreId(store).getFirst().getId();
        session=new MockHttpSession();session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,store);
        when(storage.enabled()).thenReturn(true);when(storage.bucket()).thenReturn("private-test");when(storage.key(anyLong(),anyString())).thenAnswer(a -> "test/"+a.getArgument(0)+"/"+a.getArgument(1));
        doAnswer(a->{objects.put(a.getArgument(1),a.getArgument(2));return null;}).when(storage).put(anyString(),anyString(),any(),anyString());
        when(storage.read(anyString(),anyString())).thenAnswer(a->new java.io.ByteArrayInputStream(objects.get(a.getArgument(1))));
        doAnswer(a->{objects.remove(a.getArgument(1));return null;}).when(storage).delete(anyString(),anyString());
        token=uploads.session(store);
    }
    static MockMultipartFile png() throws Exception {
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(20,10,BufferedImage.TYPE_INT_RGB),"png",out);
        return new MockMultipartFile("file","../arte.png","text/html",out.toByteArray());
    }
    BuyerUploadService.Uploaded upload() throws Exception {return uploads.upload(store,123,field,token,UUID.randomUUID().toString(),png());}
    void confirm(BuyerUploadService.Uploaded file,long item) throws Exception {
        uploads.cart(store,token,file.id());
        String platformTime=java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ").withZone(java.time.ZoneOffset.UTC).format(Instant.now());
        when(api.getOrder(any(),eq(300L))).thenReturn(json.readTree("{\"id\":300,\"created_at\":\""+platformTime+"\",\"products\":[{\"id\":"+item+",\"product_id\":123,\"properties\":{\"_ncf_file_"+field+"\":\""+file.reference()+"\"}}]}"));
        orders.order(store,300);
    }
    @Test void productToOrderToAuthenticatedDownloadAndDeletion() throws Exception {
        mvc.perform(get("/public/stores/"+store+"/personalization").param("productId","123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fields[0].fieldType").value("FILE_UPLOAD")).andExpect(jsonPath("$.fields[0].id").value(field));
        var file=upload();assertThat(file.name()).isEqualTo("arte.png");confirm(file,301);
        var response=mvc.perform(get("/admin/buyer-uploads/"+file.id()+"/download").session(session)).andReturn();
        mvc.perform(asyncDispatch(response)).andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(content().bytes(png().getBytes()));
        mvc.perform(get("/admin/buyer-uploads").session(session)).andExpect(status().isOk());
        mvc.perform(post("/admin/buyer-uploads/"+file.id()+"/delete").session(session).header("X-NCF-Action","delete").param("confirmed","true")).andExpect(status().isOk());
        assertThat(ledger.usage(store).occupiedBytes()).isEqualTo(file.size());
        uploads.cleanup();assertThat(objects).isEmpty();assertThat(ledger.usage(store).occupiedBytes()).isZero();
        mvc.perform(get("/admin/buyer-uploads/"+file.id()+"/download").session(session)).andExpect(status().isBadRequest());
    }
    @Test void filesFromDifferentItemsDoNotMixAndWebhookReplayIsIdempotent() throws Exception {
        var a=upload();var b=upload();confirm(a,301);confirm(b,302);orders.order(store,300);
        assertThat(jdbc.queryForObject("select order_item_id from buyer_uploads where id=?",Long.class,a.id())).isEqualTo(301);
        assertThat(jdbc.queryForObject("select order_item_id from buyer_uploads where id=?",Long.class,b.id())).isEqualTo(302);
    }
    @Test void anotherSessionCannotAttachRemoveOrDownload() throws Exception {
        var file=upload();String other=uploads.session(store);
        assertThatThrownBy(()->uploads.cart(store,other,file.id())).isInstanceOf(BuyerUploadLedger.Rejected.class);
        assertThatThrownBy(()->uploads.remove(store,other,file.id())).isInstanceOf(BuyerUploadLedger.Rejected.class);
        mvc.perform(get("/admin/buyer-uploads/"+file.id()+"/download")).andExpect(status().is3xxRedirection());
    }
    @Test void invalidFileNeverBecomesReadyAndFailedStorageRetainsDeletionIntent() throws Exception {
        assertThatThrownBy(()->uploads.upload(store,123,field,token,UUID.randomUUID().toString(),new MockMultipartFile("file","bad.pdf","application/pdf","%PDF-not-a-document".getBytes())))
                .isInstanceOf(BuyerUploadLedger.Rejected.class);
        uploads.cleanup();assertThat(ledger.usage(store).occupiedBytes()).isZero();
        doThrow(new IllegalStateException("test failure")).when(storage).put(anyString(),anyString(),any(),anyString());
        assertThatThrownBy(this::upload).isInstanceOf(BuyerUploadLedger.Rejected.class);
        assertThat(jdbc.queryForObject("select count(*) from buyer_uploads where store_id=? and state='DELETING'",Long.class,store)).isEqualTo(1);
    }
    @Test void erasureRemovesObjectsEvenAfterStoreRecordIsGone() throws Exception {
        var file=upload();confirm(file,301);erasure.erase(store);uploads.cleanup();
        assertThat(objects).isEmpty();assertThat(jdbc.queryForObject("select count(*) from buyer_uploads where store_id=?",Long.class,store)).isZero();
    }
    @Test void incompleteReconciliationPreservesExpiredCartUpload() throws Exception {
        var file=upload();uploads.cart(store,token,file.id());
        jdbc.update("update buyer_uploads set expires_at=? where id=?",Timestamp.from(Instant.now().minusSeconds(60)),file.id());
        when(api.listOrdersForSales(any(),anyInt(),anyInt(),any(),any(),any(),any())).thenThrow(new IllegalStateException("unavailable"));
        uploads.cleanup();assertThat(jdbc.queryForObject("select state from buyer_uploads where id=?",String.class,file.id())).isEqualTo("CART");
    }
    @Test void legacyPlanCannotUploadEvenUsingDirectRequests() throws Exception {
        jdbc.update("delete from buyer_upload_entitlements where store_id=?",store);
        mvc.perform(multipart("/public/stores/"+store+"/buyer-uploads").file(png()).param("productId","123").param("fieldId",String.valueOf(field))
                .header("X-Upload-Session",token).header("X-Upload-Request",UUID.randomUUID().toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("upload.unavailable"));
        assertThat(objects).isEmpty();
    }
    @Test void completedRequestRetryReturnsSameFileWithoutDuplicatingStorage() throws Exception {
        String request=UUID.randomUUID().toString();
        var first=uploads.upload(store,123,field,token,request,png());
        var again=uploads.upload(store,123,field,token,request,png());
        assertThat(again.id()).isEqualTo(first.id());assertThat(ledger.usage(store).occupiedBytes()).isEqualTo(first.size());
        verify(storage,times(1)).put(anyString(),anyString(),any(),anyString());
    }
    @Test void cancellationBlocksUploadsButPreservesAuthenticatedOrderDownload() throws Exception {
        var file=upload();confirm(file,301);
        jdbc.update("update stores set plan='FREE' where store_id=?",store);
        assertThatThrownBy(this::upload).isInstanceOf(BuyerUploadLedger.Rejected.class);
        var response=mvc.perform(get("/admin/buyer-uploads/"+file.id()+"/download").session(session)).andReturn();
        mvc.perform(asyncDispatch(response)).andExpect(status().isOk()).andExpect(content().bytes(png().getBytes()));
    }
}
