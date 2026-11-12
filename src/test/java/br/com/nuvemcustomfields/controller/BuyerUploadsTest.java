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
        admin.ensureRule(store,123L,"Caderno");var f=new FieldForm();f.setLabel("Arte");f.setFieldType(FieldType.FILE_UPLOAD);f.setRequired(true);admin.addField(store,123L,f);
        field=fields.findWithRuleByStoreId(store).getFirst().getId();
        session=new MockHttpSession();session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,store);
        when(storage.forStore(anyLong())).thenReturn(storage);when(storage.forProvider(any())).thenReturn(storage);when(storage.provider()).thenReturn(BuyerStorageProvider.AWS_S3);
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
    @Test void changingDestinationPreservesDownloadsAndCleanupOfExistingFiles() throws Exception {
        var awsFile=upload();confirm(awsFile,301);
        var oci=mock(BuyerFileStorage.class);
        when(oci.provider()).thenReturn(BuyerStorageProvider.OCI_OBJECT_STORAGE);
        when(oci.enabled()).thenReturn(true);when(oci.bucket()).thenReturn("oci-bucket");
        when(oci.key(anyLong(),anyString())).thenAnswer(a -> "oci/"+a.getArgument(1));
        when(storage.forStore(store)).thenReturn(oci);
        when(storage.forProvider(BuyerStorageProvider.OCI_OBJECT_STORAGE)).thenReturn(oci);
        when(oci.read(anyString(),anyString())).thenReturn(new java.io.ByteArrayInputStream(png().getBytes()));
        var ociFile=upload();confirm(ociFile,302);
        assertThat(jdbc.queryForObject("select storage_provider from buyer_uploads where id=?",String.class,awsFile.id())).isEqualTo("AWS_S3");
        assertThat(jdbc.queryForObject("select storage_provider from buyer_uploads where id=?",String.class,ociFile.id())).isEqualTo("OCI_OBJECT_STORAGE");
        // Changing the current destination (even disabling it) must not disable old reads or cleanup.
        when(storage.forStore(store)).thenReturn(storage);
        when(storage.enabled()).thenReturn(false);when(oci.enabled()).thenReturn(false);
        try(var stream=uploads.download(store,awsFile.id()).stream()){assertThat(stream.readAllBytes()).isEqualTo(png().getBytes());}
        try(var stream=uploads.download(store,ociFile.id()).stream()){assertThat(stream.readAllBytes()).isEqualTo(png().getBytes());}
        ledger.removeOrderedBatch(store,List.of(awsFile.id(),ociFile.id()));uploads.cleanup();
        verify(storage).delete("private-test","test/"+store+"/"+awsFile.id());
        verify(oci).delete(eq("oci-bucket"),anyString());
        assertThat(ledger.usage(store).occupiedBytes()).isZero();
    }
    @Test void failedOciWriteKeepsItsProviderForCleanup() throws Exception {
        var oci=mock(BuyerFileStorage.class);
        when(oci.provider()).thenReturn(BuyerStorageProvider.OCI_OBJECT_STORAGE);
        when(oci.enabled()).thenReturn(true);when(oci.bucket()).thenReturn("oci-bucket");
        when(oci.key(anyLong(),anyString())).thenAnswer(a -> "oci/"+a.getArgument(1));
        when(storage.forStore(store)).thenReturn(oci);
        when(storage.forProvider(BuyerStorageProvider.OCI_OBJECT_STORAGE)).thenReturn(oci);
        doThrow(new IllegalStateException("timeout")).when(oci).put(anyString(),anyString(),any(),anyString());
        assertThatThrownBy(this::upload).isInstanceOf(BuyerUploadLedger.Rejected.class);
        assertThat(jdbc.queryForObject("select storage_provider from buyer_uploads where store_id=?",String.class,store)).isEqualTo("OCI_OBJECT_STORAGE");
        when(storage.forStore(store)).thenReturn(storage);uploads.cleanup();
        verify(oci).delete(eq("oci-bucket"),anyString());
        verify(storage,never()).delete(eq("oci-bucket"),anyString());
        assertThat(ledger.usage(store).occupiedBytes()).isZero();
    }
    @Test void unavailableBuyerUploadShowsItsActualReasonInsteadOfImageOptionsError() throws Exception {
        jdbc.update("update stores set plan='PREMIUM' where store_id=?",store);
        mvc.perform(post("/admin/products/123/fields").session(session).param("label","Arquivo")
                .param("fieldType","FILE_UPLOAD").param("maxLength","100").param("sortOrder","1"))
                .andExpect(status().isOk()).andExpect(model().attribute("error",
                        "O envio de arquivos está disponível nos planos Pro e Ultra, com armazenamento habilitado."));
        assertThat(fields.findWithRuleByStoreId(store)).hasSize(1);
    }
    @Test void invalidBuyerUploadFormatsShowFormatHelpInsteadOfImageOptionsError() throws Exception {
        mvc.perform(post("/admin/products/123/fields").session(session).param("label","Arquivo")
                .param("fieldType","FILE_UPLOAD").param("optionsText","EXE").param("maxLength","100").param("sortOrder","1"))
                .andExpect(status().isOk()).andExpect(model().attribute("error","Informe JPEG, PNG ou PDF, um por linha."));
        assertThat(fields.findWithRuleByStoreId(store)).hasSize(1);
    }
    @Test void allowedBuyerUploadFieldIsSavedWithoutImageOptions() throws Exception {
        mvc.perform(post("/admin/products/123/fields").session(session).param("label","Arquivo")
                .param("fieldType","FILE_UPLOAD").param("optionsText","PNG\nPDF").param("maxLength","100").param("sortOrder","1"))
                .andExpect(redirectedUrl("/admin/products/123/fields"));
        assertThat(fields.findWithRuleByStoreId(store)).hasSize(2);
    }
    @Test void recoversAllFilesFromAnAuthenticatedOrderWithATruncatedLegacyUrl() throws Exception {
        var owner=stores.findByStoreId(store).orElseThrow();owner.setPlan(PlanType.PREMIUM_ULTRA);owner.setScope("read_orders");stores.saveAndFlush(owner);
        var configured=fields.findById(field).orElseThrow();configured.setMaxFiles(3);fields.saveAndFlush(configured);
        var a=upload();var b=upload();var c=upload();
        for(var file:List.of(a,b,c))uploads.cart(store,token,file.id());
        var order=json.createObjectNode();order.put("id",300);order.put("created_at",Instant.now().toString());
        var item=order.putArray("products").addObject();item.put("id",301);item.put("product_id",123);
        var properties=item.putObject("properties");
        properties.put("_ncf_file_"+field,a.reference()+"; "+b.reference());
        properties.put("Arte","arquivo.png — https://app.test/admin/buyer-uploads/"+c.id().substring(0,30));
        when(api.getOrder(any(),eq(300L))).thenReturn(order);
        assertThat(orders.fileIds(store,item)).containsExactly(a.id(),b.id(),c.id());
        assertThat(orders.fileIds(store+10000,item)).isEmpty();
        orders.order(store,300);
        assertThat(jdbc.queryForObject("select count(*) from buyer_uploads where store_id=? and order_id=300 and order_item_id=301 and state='ORDERED'",Long.class,store)).isEqualTo(3);
        var name=new FieldForm();name.setLabel("Nome na camisa");name.setFieldType(FieldType.TEXT);admin.addField(store,123L,name);
        properties.put("Nome na camisa","Pedro");
        when(api.listRecentOrders(any())).thenReturn(json.createArrayNode().add(order));
        var rendered=mvc.perform(get("/admin/dashboard").session(session)).andExpect(status().isOk()).andReturn();
        var summary=(br.com.nuvemcustomfields.dto.DashboardSummary)rendered.getModelAndView().getModel().get("summary");
        var groups=summary.personalizedOrders().getFirst().items().getFirst().fieldGroups();
        assertThat(groups).extracting(br.com.nuvemcustomfields.dto.PersonalizedOrderSummary.Property::label).containsExactly("Arte","Nome na camisa");
        assertThat(groups.getFirst().files()).extracting(br.com.nuvemcustomfields.dto.PersonalizedOrderSummary.Upload::id).containsExactlyInAnyOrder(a.id(),b.id(),c.id());
        assertThat(groups.get(1).value()).isEqualTo("Pedro");
        assertThat(rendered.getResponse().getContentAsString()).contains("merchant-order-files",a.id()+"/download",b.id()+"/download",c.id()+"/download");
        // A prefix shared by two records must never be guessed.
        String ambiguous=c.id().substring(0,30)+(c.id().endsWith("000000")?"111111":"000000");
        jdbc.update("insert into buyer_uploads(id,store_id,product_id,field_id,session_hash,request_id,size_bytes,state,created_at,expires_at) select ?,store_id,product_id,field_id,session_hash,?,size_bytes,'CART',created_at,expires_at from buyer_uploads where id=?",ambiguous,UUID.randomUUID().toString(),c.id());
        assertThat(orders.fileIds(store,item)).containsExactly(a.id(),b.id());
    }

    @Test void merchantCanConfigureMultipleFilesOnlyOnUltra() {
        var form=new FieldForm();form.setLabel("Referências");form.setFieldType(FieldType.FILE_UPLOAD);form.setMaxFiles(3);
        assertThatThrownBy(()->admin.addField(store,123L,form)).isInstanceOf(IllegalArgumentException.class).hasMessage("upload.multiple.ultra");
        var owner=stores.findByStoreId(store).orElseThrow();owner.setPlan(PlanType.PREMIUM_ULTRA);stores.saveAndFlush(owner);
        admin.addField(store,123L,form);
        assertThat(fields.findWithRuleByStoreId(store)).anyMatch(f->f.getLabel().equals("Referências") && f.getMaxFiles()==3);
        form.setMaxFiles(6);
        assertThatThrownBy(()->admin.addField(store,123L,form)).isInstanceOf(IllegalArgumentException.class).hasMessage("upload.files.invalid");
    }

    @Test void ultraMultipleFilesShareOneOrderItemAndRemainDownloadableAfterDowngrade() throws Exception {
        var owner=stores.findByStoreId(store).orElseThrow();owner.setPlan(PlanType.PREMIUM_ULTRA);stores.saveAndFlush(owner);
        var configured=fields.findById(field).orElseThrow();configured.setMaxFiles(2);fields.saveAndFlush(configured);
        mvc.perform(get("/public/stores/"+store+"/personalization").param("productId","123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fields[0].maxFiles").value(2))
                .andExpect(jsonPath("$.fields[0].maxFileBytes").value(20000000));
        var a=upload();var b=upload();
        assertThatThrownBy(()->upload()).isInstanceOf(BuyerUploadLedger.Rejected.class).hasMessage("upload.files.limit");
        uploads.cart(store,token,a.id());uploads.cart(store,token,b.id());
        String timestamp=Instant.now().toString();
        when(api.getOrder(any(),eq(300L))).thenReturn(json.readTree("{\"id\":300,\"created_at\":\""+timestamp+"\",\"products\":[{\"id\":301,\"product_id\":123,\"properties\":{\"_ncf_file_"+field+"\":\""+a.reference()+"; "+b.reference()+"\"}}]}"));
        orders.order(store,300);orders.order(store,300);
        assertThat(jdbc.queryForObject("select count(*) from buyer_uploads where store_id=? and order_id=300 and order_item_id=301 and state='ORDERED'",Long.class,store)).isEqualTo(2);
        owner.setPlan(PlanType.PREMIUM_PLUS);stores.saveAndFlush(owner);
        try(var stream=uploads.download(store,a.id()).stream()){assertThat(stream.readAllBytes()).isEqualTo(png().getBytes());}
        try(var stream=uploads.download(store,b.id()).stream()){assertThat(stream.readAllBytes()).isEqualTo(png().getBytes());}
        mvc.perform(get("/public/stores/"+store+"/personalization").param("productId","123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fields[0].maxFiles").value(1))
                .andExpect(jsonPath("$.fields[0].maxFileBytes").value(10000000));
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
        var a=upload();confirm(a,301);var b=upload();confirm(b,302);orders.order(store,300);
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
    @Test void essentialPlanCannotUploadEvenUsingDirectRequests() throws Exception {
        jdbc.update("update stores set plan='PREMIUM' where store_id=?",store);
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
    @Test void batchDeletionIsConfirmedIdempotentAndReleasesSpaceOnlyAfterStorageCleanup() throws Exception {
        var first=upload();confirm(first,301);var second=upload();confirm(second,302);
        long occupied=ledger.usage(store).occupiedBytes();
        String body=json.writeValueAsString(Map.of("ids",List.of(first.id(),second.id()),"confirmed",true));
        mvc.perform(post("/admin/buyer-uploads/delete-selected").session(session).header("X-NCF-Action","delete")
                .contentType("application/json").content(body.replace("true","false"))).andExpect(status().isBadRequest());
        mvc.perform(post("/admin/buyer-uploads/delete-selected").session(session).header("X-NCF-Action","delete")
                .contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.files").value(2)).andExpect(jsonPath("$.bytes").value(occupied));
        assertThat(ledger.usage(store).occupiedBytes()).isEqualTo(occupied);
        mvc.perform(get("/admin/buyer-uploads/data").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingBytes").value(occupied));
        mvc.perform(post("/admin/buyer-uploads/delete-selected").session(session).header("X-NCF-Action","delete")
                .contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.files").value(0));
        uploads.cleanup();assertThat(ledger.usage(store).occupiedBytes()).isZero();assertThat(objects).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from buyer_uploads where store_id=? and order_id=300 and state='DELETED'",Long.class,store)).isEqualTo(2);
    }
    @Test void crossStoreSelectionCannotDeleteOrDownloadAnyFile() throws Exception {
        var own=upload();confirm(own,301);var foreign=upload();confirm(foreign,302);
        jdbc.update("update buyer_uploads set store_id=? where id=?",store+100000,foreign.id());
        var ids=List.of(own.id(),foreign.id());
        mvc.perform(post("/admin/buyer-uploads/delete-selected").session(session).header("X-NCF-Action","delete")
                .contentType("application/json").content(json.writeValueAsString(Map.of("ids",ids,"confirmed",true))))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForList("select state from buyer_uploads where id in (?,?)",String.class,own.id(),foreign.id())).containsOnly("ORDERED");
        mvc.perform(get("/admin/buyer-uploads/"+foreign.id()+"/download").session(session))
                .andExpect(status().isBadRequest());
        verify(storage,never()).read(anyString(),anyString());
        mvc.perform(post("/admin/buyer-uploads/download-zip").contentType("application/x-www-form-urlencoded").param("ids",own.id()))
                .andExpect(status().is3xxRedirection());
    }
    @Test void temporaryFileInSelectionPreventsPartialDeletion() throws Exception {
        var own=upload();confirm(own,301);var temporary=upload();
        mvc.perform(post("/admin/buyer-uploads/delete-selected").session(session).header("X-NCF-Action","delete")
                .contentType("application/json").content(json.writeValueAsString(Map.of("ids",List.of(own.id(),temporary.id()),"confirmed",true))))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select state from buyer_uploads where id=?",String.class,own.id())).isEqualTo("ORDERED");
    }
    @Test void zipDownloadIsUnavailableAndDoesNotReadStorage() throws Exception {
        var file=upload();confirm(file,301);
        mvc.perform(post("/admin/buyer-uploads/download-zip").session(session).contentType("application/x-www-form-urlencoded")
                .param("ids",file.id())).andExpect(status().isNotFound());
        verify(storage,never()).read(anyString(),anyString());
    }
    @Test void expiredSelectionFailsBeforeReadingStorageAndDownloadBudgetIsNotPoisonedByRejectedBatch() throws Exception {
        var first=upload();confirm(first,301);var second=upload();confirm(second,302);
        jdbc.update("update buyer_uploads set expires_at=? where id=?",Timestamp.from(Instant.now().minusSeconds(1)),second.id());
        assertThatThrownBy(()->uploads.archive(store,List.of(first.id(),second.id()))).hasMessage("upload.not.found");
        verify(storage,never()).read(anyString(),anyString());
        jdbc.update("update buyer_uploads set expires_at=?,size_bytes=600000000 where id in (?,?)",Timestamp.from(Instant.now().plusSeconds(100)),first.id(),second.id());
        assertThatThrownBy(()->uploads.archive(store,List.of(first.id(),second.id()))).hasMessage("upload.rate.limit");
        jdbc.update("update buyer_uploads set size_bytes=? where id in (?,?)",first.size(),first.id(),second.id());
        assertThat(uploads.archive(store,List.of(first.id(),second.id())).files()).hasSize(2);
    }
    @Test void listingPaginatesOrdersWithoutSplittingThemAndSupportsDateAndSizeFilters() throws Exception {
        Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        for(int n=1;n<=22;n++){
            jdbc.update("insert into buyer_uploads(id,store_id,product_id,field_id,session_hash,request_id,size_bytes,state,order_id,order_item_id,order_created_at,created_at,expires_at) values (?,?,123,?,'test',?,?,'ORDERED',?,1,?,?,?)",
                    "listed-"+n,store,field,"listed-"+n,n,1000+n,Timestamp.from(now.minusSeconds(n*86400L)),Timestamp.from(now),Timestamp.from(now.plusSeconds(86400)));
        }
        jdbc.update("insert into buyer_uploads(id,store_id,product_id,field_id,session_hash,request_id,size_bytes,state,order_id,order_item_id,order_created_at,created_at,expires_at) select 'listed-extra',store_id,product_id,field_id,session_hash,'listed-extra',size_bytes,state,order_id,2,order_created_at,created_at,expires_at from buyer_uploads where id='listed-22' and store_id=?",store);
        mvc.perform(get("/admin/buyer-uploads/data").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(22)).andExpect(jsonPath("$.pages").value(2))
                .andExpect(jsonPath("$.files.length()").value(21)).andExpect(jsonPath("$.files[0].order_id").value(1022));
        mvc.perform(get("/admin/buyer-uploads/data").session(session).param("page","2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.files.length()").value(2));
        mvc.perform(get("/admin/buyer-uploads/data").session(session).param("sort","newest")).andExpect(status().isOk())
                .andExpect(jsonPath("$.files[0].order_id").value(1001));
        mvc.perform(get("/admin/buyer-uploads/data").session(session).param("sort","largest").param("before",now.minusSeconds(20*86400L).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalOrders").value(3)).andExpect(jsonPath("$.files[0].order_id").value(1022));
    }

    @Test void bulkActionsRejectEmptyOrOversizedSelectionsWithoutChangingFiles() throws Exception {
        var file=upload();confirm(file,301);
        for(var ids:List.of(List.<String>of(),java.util.Collections.nCopies(201,file.id()))) {
            mvc.perform(post("/admin/buyer-uploads/delete-selected").session(session).header("X-NCF-Action","delete")
                    .contentType("application/json").content(json.writeValueAsString(Map.of("ids",ids,"confirmed",true))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("upload.selection.invalid"));
        }
        assertThat(jdbc.queryForObject("select state from buyer_uploads where id=?",String.class,file.id())).isEqualTo("ORDERED");
    }

    @Test void fileManagerRendersTranslatedActionsAndExportsIsolatedPreview() throws Exception {
        String html=mvc.perform(get("/admin/buyer-uploads").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Os downloads são individuais.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ZIP"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("??buyer.files."))))
                .andReturn().getResponse().getContentAsString();
        if(Boolean.getBoolean("ncf.files.export")) {
            for(String stylesheet:List.of("app.css","merchant.css")) {
                String css=new String(getClass().getResourceAsStream("/static/styles/"+stylesheet).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                html=html.replaceAll("<link[^>]+href=\"/styles/"+stylesheet.replace(".","\\.")+"[^\"]*\"[^>]*>",java.util.regex.Matcher.quoteReplacement("<style>"+css+"</style>"));
            }
            java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/ncf-file-manager-preview.html"),html);
        }
    }

}
