package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.dto.*;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.service.*;
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
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="images.cleanup-delay-ms=10000000") @AutoConfigureMockMvc
class OptionImagesTest {
    @Autowired OptionImageService images;
    @Autowired PersonalizationAdminService admin;
    @Autowired StoreRepository stores;
    @Autowired PersonalizationFieldRepository fields;
    @Autowired StoreDataErasureService erasure;
    @Autowired WebhookLifecycleService webhooks;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean OptionImageStorage storage;
    private static final AtomicLong IDS=new AtomicLong(985000);
    private Long store;
    private final Long product=123L;
    private MockHttpSession session;
    @BeforeEach void setup() {
        store=IDS.incrementAndGet();var s=new Store();s.setStoreId(store);s.setAccessToken("test");s.setStoreCountryCode("BR");
        s.setPlan(PlanType.PREMIUM_PLUS);stores.saveAndFlush(s);admin.ensureRule(store,product,"Caderno");
        session=new MockHttpSession();session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,store);
        when(storage.enabled()).thenReturn(true);when(storage.bucket()).thenReturn("test-images");when(storage.prefix()).thenReturn("test/options");
        when(storage.readUrl(anyString(),anyString())).thenReturn("https://assets.example/image.jpg");
    }
    static MockMultipartFile png() throws Exception {
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(20,10,BufferedImage.TYPE_INT_RGB),"png",out);
        return new MockMultipartFile("file","capa.png","image/png",out.toByteArray());
    }
    String upload() throws Exception {return images.upload(store,product,png()).id();}
    FieldForm form(String... ids) {
        var f=new FieldForm();f.setLabel("Capa");f.setFieldType(FieldType.IMAGE_SELECT);f.setRequired(true);
        var opts=new java.util.ArrayList<ImageOption>();for(int i=0;i<ids.length;i++)opts.add(new ImageOption(ids[i],"Capa "+i));
        f.setImageOptionsJson(ImageOptions.serialize(opts));return f;
    }
    Long save(String... ids) {admin.addField(store,product,form(ids));return admin.requireRuleWithFields(store,product).getFields().getFirst().getId();}
    String state(String id) {return jdbc.queryForObject("select state from personalization_images where id=?",String.class,id);}
    void due() {jdbc.update("update personalization_images set retry_at=? where state='DELETING'",java.sql.Timestamp.from(Instant.now().minusSeconds(1)));}
    @Test void uploadsValidatedImagesToS3AndReturnsLabelsToCartConfiguration() throws Exception {
        String id=upload();verify(storage,times(2)).put(eq("test-images"),contains("/"+store+"/"+product+"/"+id+"/"),any(byte[].class));
        mvc.perform(get("/public/stores/"+store+"/images/"+id)).andExpect(status().isNotFound());
        mvc.perform(get("/admin/images/"+id).session(session)).andExpect(status().isFound());
        save(id);
        mvc.perform(get("/public/stores/"+store+"/personalization").param("productId",product.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fields[0].options[0]").value("Capa 0"))
                .andExpect(jsonPath("$.fields[0].imageOptions[0].label").value("Capa 0"));
        mvc.perform(get("/public/stores/"+store+"/images/"+id)).andExpect(status().isFound()).andExpect(header().string("Cache-Control","no-store"));
        var rendered=mvc.perform(get("/admin/products/"+product+"/fields").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/images-editor-rendered.html"),rendered);
        var owner=stores.findByStoreId(store).orElseThrow();owner.setErasureRequestedAt(Instant.now());stores.saveAndFlush(owner);
        mvc.perform(get("/public/stores/"+store+"/images/"+id)).andExpect(status().isNotFound());
    }
    @Test void rejectsInvalidFilesAndUploadsWithoutAuthentication() throws Exception {
        var bad=new MockMultipartFile("file","fake.png","image/png","not an image".getBytes());
        mvc.perform(multipart("/admin/products/"+product+"/images").file(bad).session(session)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/admin/products/"+product+"/images").file(png())).andExpect(status().isFound());
        verify(storage,never()).put(anyString(),anyString(),any());
    }
    @Test void cannotBindAnotherStoresOrFieldsImagesAndRollsBackConfiguration() throws Exception {
        String id=upload();Long ownerField=save(id);
        admin.ensureRule(store+1,product,"Outro");
        assertThatThrownBy(()->admin.addField(store+1,product,form(id))).isInstanceOf(IllegalArgumentException.class);
        assertThat(admin.requireRuleWithFields(store+1,product).getFields()).isEmpty();
        assertThatThrownBy(()->admin.addField(store,product,form(id))).isInstanceOf(IllegalArgumentException.class);
        assertThat(admin.requireRuleWithFields(store,product).getFields()).hasSize(1);
        assertThat(jdbc.queryForObject("select field_id from personalization_images where id=?",Long.class,id)).isEqualTo(ownerField);
        mvc.perform(get("/public/stores/"+(store+1)+"/images/"+id)).andExpect(status().isNotFound());
    }
    @Test void replacementQueuesOldImageAndRetriesFailedS3Deletion() throws Exception {
        String old=upload();Long field=save(old);String replacement=upload();
        admin.updateField(store,product,field,form(replacement));assertThat(state(old)).isEqualTo("DELETING");assertThat(state(replacement)).isEqualTo("READY");
        due();doThrow(new RuntimeException("S3 offline")).when(storage).delete(anyString(),anyString());images.cleanup();
        assertThat(state(old)).isEqualTo("DELETING");
        reset(storage);when(storage.enabled()).thenReturn(true);due();images.cleanup();
        assertThat(jdbc.queryForObject("select count(*) from personalization_images where id=?",Integer.class,old)).isZero();
        assertThat(state(replacement)).isEqualTo("READY");
    }
    @Test void removingAnOptionOrChangingTypeQueuesOnlyUnusedImages() throws Exception {
        String first=upload(),second=upload();Long field=save(first,second);
        admin.updateField(store,product,field,form(second));assertThat(state(first)).isEqualTo("DELETING");assertThat(state(second)).isEqualTo("READY");
        var text=new FieldForm();text.setLabel("Nome");admin.updateField(store,product,field,text);
        assertThat(state(second)).isEqualTo("DELETING");
    }
    @Test void deletesFieldProductWebhookAndStoreIncludingTemporaryUploads() throws Exception {
        String first=upload();Long field=save(first);admin.deleteField(store,product,field);assertThat(state(first)).isEqualTo("DELETING");
        String second=upload();save(second);admin.deleteRule(store,product);assertThat(state(second)).isEqualTo("DELETING");
        admin.ensureRule(store,product,"Caderno");String third=upload();save(third);
        webhooks.handle(new WebhookPayload(store,"product/deleted",product));assertThat(state(third)).isEqualTo("DELETING");
        admin.ensureRule(store,product,"Caderno");String abandoned=upload();erasure.erase(store);
        assertThat(stores.findByStoreId(store)).isEmpty();assertThat(state(abandoned)).isEqualTo("DELETING");
        due();images.cleanup();assertThat(jdbc.queryForObject("select count(*) from personalization_images where store_id=?",Integer.class,store)).isZero();
    }
    @Test void cleansPartialUploadsAbandonedUploadsAndSqlOrphans() throws Exception {
        doThrow(new RuntimeException()).when(storage).put(anyString(),contains("thumbnail"),any());
        assertThatThrownBy(()->images.upload(store,product,png())).hasMessage("image.upload.failed");
        assertThat(jdbc.queryForObject("select state from personalization_images where store_id=?",String.class,store)).isEqualTo("DELETING");
        doNothing().when(storage).put(anyString(),anyString(),any());String id=upload();Long field=save(id);
        jdbc.update("delete from personalization_fields where id=?",field);images.cleanup();assertThat(state(id)).isEqualTo("DELETING");
        String orphan=upload();jdbc.update("update personalization_images set retry_at=? where id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),orphan);
        images.cleanup();assertThat(state(orphan)).isEqualTo("DELETING");due();images.cleanup();
        assertThat(jdbc.queryForObject("select count(*) from personalization_images where store_id=?",Integer.class,store)).isZero();
    }
    @Test void retainsOldImageWhenInvalidReplacementFailsAndRendersSubmittedValues() throws Exception {
        String id=upload();Long field=save(id);var invalid=form(java.util.UUID.randomUUID().toString());
        invalid.setLabel("Nova capa");
        mvc.perform(post("/admin/products/"+product+"/fields/"+field).session(session)
                .param("label",invalid.getLabel()).param("fieldType","IMAGE_SELECT").param("imageOptionsJson",invalid.getImageOptionsJson()))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Nova capa")));
        assertThat(state(id)).isEqualTo("READY");assertThat(admin.requireRuleWithFields(store,product).getFields().getFirst().getLabel()).isEqualTo("Capa");
    }
}
