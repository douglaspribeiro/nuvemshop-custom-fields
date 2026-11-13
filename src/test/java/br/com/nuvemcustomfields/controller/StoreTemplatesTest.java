package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.dto.*;
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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"images.cleanup-delay-ms=10000000","buyer-uploads.cleanup-delay-ms=10000000"})
@AutoConfigureMockMvc
class StoreTemplatesTest {
    @Autowired StoreTemplateService templates;
    @Autowired TemplateBatchService batches;
    @Autowired StoreRepository stores;
    @Autowired PersonalizationAdminService admin;
    @Autowired PersonalizationFieldRepository fields;
    @Autowired PersonalizationRuleRepository rules;
    @Autowired StoreTemplateRepository saved;
    @Autowired PersonalizationFieldHistoryRepository history;
    @Autowired OptionImageService images;
    @Autowired StoreDataErasureService erasure;
    @Autowired NicheTemplateService catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean OrderPersonalizationCatalog fieldCatalog;
    @MockitoBean OptionImageStorage storage;
    @MockitoBean BuyerFileStorage buyerStorage;
    @MockitoBean NuvemshopApiClient api;
    private static final AtomicLong IDS=new AtomicLong(978000);
    private long store;
    private MockHttpSession session;
    @BeforeEach void setup() {
        when(buyerStorage.forStore(anyLong())).thenReturn(buyerStorage);
        store=newStore(PlanType.PREMIUM_PLUS);
        session=new MockHttpSession();session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,store);
        when(storage.enabled()).thenReturn(true);when(storage.bucket()).thenReturn("template-test");when(storage.prefix()).thenReturn("test/options");
        when(storage.readUrl(anyString(),anyString())).thenReturn("https://test.invalid/image.jpg");
        when(api.getProduct(any(),anyLong())).thenAnswer(call->new ProductSummary(call.getArgument(1),"Caderno"));
        when(api.listProducts(any(),anyInt(),anyInt(),any())).thenReturn(new ProductPage(List.of(new ProductSummary(123L,"Caderno")),1,50,1,false,null));
    }
    long newStore(PlanType plan) {long id=IDS.incrementAndGet();var s=new Store();s.setStoreId(id);s.setPlan(plan);s.setAccessToken("test");s.setStoreCountryCode("BR");stores.saveAndFlush(s);return id;}
    TemplateFieldDefinition text(String label) {return new TemplateFieldDefinition(label,FieldType.TEXT,true,40,"Exemplo","^[A-Za-z ]+$",null,null,true,null);}
    StoreTemplateService.Model model(String label) {return templates.create(store,"Meu modelo","Descrição",List.of(text(label)));}
    int apply(StoreTemplateService.Model model,long product,StoreTemplateService.Mode mode) {
        var preview=templates.preview(store,model.key(),product);
        return templates.apply(store,model.key(),product,"Caderno",mode,true,UUID.randomUUID().toString(),preview.reviewToken());
    }
    List<PersonalizationField> product(long id) {return fields.findWithRuleByStoreId(store).stream().filter(f->f.getRule().getProductId()==id).toList();}
    @Test void catalogHasOnlyThreeModelsAndOldIdentifierIsRejectedWithoutDeletingProducts() throws Exception {
        assertThat(catalog.listTemplates()).extracting(NicheTemplate::id).containsExactly("camiseta","caneca","convite");
        var own=model("Nome antigo");apply(own,123,StoreTemplateService.Mode.APPEND);
        mvc.perform(post("/admin/onboarding/apply").session(session).param("productId","123").param("productName","Caderno").param("templateId","personalizacao-infantil"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        assertThat(product(123)).hasSize(1);
    }
    @Test void crudCopiesDefinitionsAndKeepsProductsIndependent() {
        var original=model("Nome");apply(original,123,StoreTemplateService.Mode.APPEND);
        var copied=templates.saveProduct(store,123,"Salvo do produto",null);
        var duplicate=templates.duplicate(store,copied.key(),"Duplicado");
        templates.update(store,original.id(),original.revision(),"Renomeado",null,List.of(text("Outro")));
        assertThat(product(123)).extracting(PersonalizationField::getLabel).containsExactly("Nome");
        assertThat(templates.get(store,copied.key()).fields()).isEqualTo(copied.fields());
        var form=text("Editado no produto").toForm(0);admin.updateField(store,123L,product(123).getFirst().getId(),form);
        assertThat(templates.get(store,duplicate.key()).fields().getFirst().label()).isEqualTo("Nome");
        templates.delete(store,copied.id(),copied.revision(),true);
        assertThat(product(123)).extracting(PersonalizationField::getLabel).containsExactly("Editado no produto");
    }
    @Test void replacementCreatesNewIdentitiesAndPreservesHistoricalPropertyNames() {
        var initial=model("Nome antigo");apply(initial,123,StoreTemplateService.Mode.APPEND);
        long old=product(123).getFirst().getId();var next=model("Nome novo");
        var review=templates.preview(store,next.key(),123);
        assertThatThrownBy(()->templates.apply(store,next.key(),123,"Caderno",StoreTemplateService.Mode.REPLACE,false,UUID.randomUUID().toString(),review.reviewToken()))
                .hasMessage("template.confirm");
        apply(next,123,StoreTemplateService.Mode.REPLACE);
        assertThat(product(123)).hasSize(1);assertThat(product(123).getFirst().getId()).isNotEqualTo(old);
        assertThat(history.existsByStoreIdAndProductIdAndPropertyName(store,123L,"Nome antigo")).isTrue();
        assertThat(history.existsByStoreIdAndProductIdAndPropertyName(store,123L,"Nome novo")).isTrue();
    }
    @Test void requestReplayAndConcurrentSubmissionNeverDuplicateFields() throws Exception {
        var model=model("Nome");String request=UUID.randomUUID().toString();var review=templates.preview(store,model.key(),123);
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Integer> work=()->{gate.await();return templates.apply(store,model.key(),123,"Caderno",StoreTemplateService.Mode.APPEND,false,request,review.reviewToken());};
            var a=pool.submit(work);var b=pool.submit(work);gate.countDown();
            assertThat(a.get(10,TimeUnit.SECONDS)).isEqualTo(1);assertThat(b.get(10,TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(product(123)).hasSize(1);
        assertThatThrownBy(()->templates.apply(store,model.key(),124,"Outro",StoreTemplateService.Mode.APPEND,false,request,review.reviewToken())).hasMessage("template.request.conflict");
    }
    @Test void staleReviewAndStaleEditAreRejected() {
        var first=model("Primeiro");var review=templates.preview(store,first.key(),123);
        var changed=templates.update(store,first.id(),first.revision(),"Alterado",null,List.of(text("Alterado")));
        assertThatThrownBy(()->templates.update(store,first.id(),first.revision(),"Sobrescrever",null,List.of(text("Outro")))).hasMessage("template.stale");
        assertThatThrownBy(()->templates.apply(store,changed.key(),123,"Caderno",StoreTemplateService.Mode.APPEND,false,UUID.randomUUID().toString(),review.reviewToken())).hasMessage("template.stale");
        assertThat(product(123)).isEmpty();
    }
    @Test void planLimitsRejectWholeOperationInsteadOfApplyingPartially() {
        var model=templates.create(store,"Dois campos",null,List.of(text("Primeiro"),text("Segundo")));
        var s=stores.findByStoreId(store).orElseThrow();s.setPlan(PlanType.FREE);stores.saveAndFlush(s);
        assertThatThrownBy(()->apply(model,123,StoreTemplateService.Mode.APPEND)).hasMessage("template.fields.limit");
        assertThat(rules.findByStoreIdAndProductId(store,123L)).isEmpty();
    }
    @Test void appendRejectsNormalizedPropertyCollisionsAndInvalidRegex() {
        var model=model("Nome [arte]");apply(model,123,StoreTemplateService.Mode.APPEND);
        var conflicting=model("Nome (arte)");
        assertThatThrownBy(()->apply(conflicting,123,StoreTemplateService.Mode.APPEND)).hasMessage("template.labels.duplicate");
        var invalid=new TemplateFieldDefinition("Nome",FieldType.TEXT,true,40,null,"[",null,null,true,null);
        assertThatThrownBy(()->templates.create(store,"Inválido",null,List.of(invalid))).hasMessage("template.fields.invalid");
        assertThat(product(123)).hasSize(1);
    }
    @Test void templatesAndImagesCannotCrossStores() throws Exception {
        var own=model("Nome");long stranger=newStore(PlanType.PREMIUM_PLUS);
        assertThatThrownBy(()->templates.get(stranger,own.key())).hasMessage("template.not.found");
        assertThatThrownBy(()->templates.update(stranger,own.id(),0,"Outro",null,List.of(text("Nome")))).hasMessage("template.not.found");
        assertThatThrownBy(()->templates.delete(stranger,own.id(),0,true)).hasMessage("template.not.found");
        assertThatThrownBy(()->templates.preview(stranger,own.key(),123)).hasMessage("template.not.found");
        String image=image(store,123);
        assertThatThrownBy(()->templates.create(stranger,"Imagem",null,List.of(imageField(image)))).hasMessage("image.options.invalid");
    }
    String image(long store,long product) throws Exception {
        admin.ensureRule(store,product,"Caderno");var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(12,10,BufferedImage.TYPE_INT_RGB),"png",out);
        return images.upload(store,product,new MockMultipartFile("file","capa.png","image/png",out.toByteArray())).id();
    }
    TemplateFieldDefinition imageField(String id) {return new TemplateFieldDefinition("Capa",FieldType.IMAGE_SELECT,true,100,null,null,null,ImageOptions.serialize(List.of(new ImageOption(id,"Floral"))),false,3);}
    @Test void imagePinsSurviveTemplateAndSourceProductDeletionUntilLastProductIsRemoved() throws Exception {
        String image=image(store,100);admin.addField(store,100L,imageField(image).toForm(0));
        var model=templates.saveProduct(store,100,"Capa reutilizável",null);
        apply(model,123,StoreTemplateService.Mode.APPEND);apply(model,124,StoreTemplateService.Mode.APPEND);
        admin.deleteRule(store,100L);templates.delete(store,model.id(),model.revision(),true);
        assertThat(images.readUrl(store,image,true)).isNotNull();
        assertThat(product(123).getFirst().isShowImageZoom()).isFalse();assertThat(product(123).getFirst().getMobileImageColumns()).isEqualTo(3);
        admin.deleteRule(store,123L);assertThat(images.readUrl(store,image,true)).isNotNull();
        admin.deleteRule(store,124L);assertThat(jdbc.queryForObject("select state from personalization_images where id=?",String.class,image)).isEqualTo("DELETING");
    }
    @Test void templateKeepsDetachedImageAvailableForFutureApplications() throws Exception {
        String image=image(store,100);var model=templates.create(store,"Capa",null,List.of(imageField(image)));
        admin.deleteRule(store,100L);images.cleanup();
        assertThat(images.adminReadUrl(store,image,true)).isNotNull();
        apply(model,123,StoreTemplateService.Mode.APPEND);assertThat(images.readUrl(store,image,true)).isNotNull();
    }
    @Test void unavailableBuyerUploadIsRejectedAndNotOfferedByEditor() throws Exception {
        var upload=new TemplateFieldDefinition("Arte",FieldType.FILE_UPLOAD,true,100,null,null,"PDF",null,true,null);
        assertThatThrownBy(()->templates.create(store,"Upload",null,List.of(upload))).hasMessage("template.upload.unavailable");
        mvc.perform(get("/admin/templates/new").session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("value=\"FILE_UPLOAD\""))));
    }
    @Test void eligibleUploadDefinitionCopiesOnlyConfiguration() {
        when(buyerStorage.forStore(anyLong())).thenReturn(buyerStorage);
        when(buyerStorage.enabled()).thenReturn(true);
        var upload=new TemplateFieldDefinition("Arte",FieldType.FILE_UPLOAD,true,100,"Envie PDF",null,"PDF",null,true,null);
        var template=templates.create(store,"Upload",null,List.of(upload));apply(template,123,StoreTemplateService.Mode.APPEND);
        assertThat(product(123).getFirst().getFieldType()).isEqualTo(FieldType.FILE_UPLOAD);
        assertThat(jdbc.queryForObject("select count(*) from buyer_uploads where store_id=?",Long.class,store)).isZero();
    }
    @Test void reviewFailureKeepsProductSelectionVisibleInsteadOfRenderingJson() throws Exception {
        var model=model("Nome");
        when(api.getProduct(any(),eq(123L))).thenThrow(new IllegalArgumentException("unavailable"));
        mvc.perform(get("/admin/templates/review").session(session).param("key",model.key()).param("productId","123"))
                .andExpect(status().isOk()).andExpect(view().name("admin/template-choose"))
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Não foi possível verificar o produto")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Caderno")));
    }
    @Test void libraryEditorPreviewAndStorefrontRenderWithCopiedDefinitions() throws Exception {
        var model=model("Nome");apply(model,123,StoreTemplateService.Mode.APPEND);
        int index=0;
        for(String path:List.of("/admin/templates","/admin/templates/new","/admin/templates/"+model.id()+"/edit","/admin/templates/choose?key="+model.key(),"/admin/templates/review?key="+model.key()+"&productId=123")) {
            var response=mvc.perform(get(path).session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("??template.")))).andReturn();
            if(Boolean.getBoolean("ncf.templates.export")) {
                String root=java.nio.file.Path.of("src/main/resources/static").toAbsolutePath().toUri().toString();
                String html=response.getResponse().getContentAsString().replace("\"/styles/","\""+root+"styles/").replace("\"/assets/","\""+root+"assets/");
                html=html.replaceAll("(?s)<script[^>]+src=\"https?://[^\"]*\"[^>]*>.*?</script>","");
                java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/ncf-nc18-"+(index++)+".html"),html);
            }
        }
        mvc.perform(get("/public/stores/"+store+"/personalization").param("productId","123")).andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].label").value("Nome")).andExpect(jsonPath("$.fields[0].validationPattern").value("^[A-Za-z ]+$"));
    }
    @Test void authenticatedMutationsAreAuditedAndErasureRemovesAllTemplateState() throws Exception {
        String body=json.writeValueAsString(Map.of("name","Meu modelo","fields",List.of(text("Nome"))));
        mvc.perform(post("/admin/templates").session(session).header("X-NCF-Action","template-write").contentType("application/json").content(body)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from user_journey_audit where store_id=? and action='StoreTemplateController.create' and outcome='COMPLETED'",Long.class,store)).isEqualTo(1);
        var model=templates.list(store).getFirst();apply(model,123,StoreTemplateService.Mode.APPEND);erasure.erase(store);
        assertThat(saved.countByStoreId(store)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from template_applications where store_id=?",Long.class,store)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from personalization_image_references where store_id=?",Long.class,store)).isZero();
    }
    @Test void directCallsWithoutSessionOrWriteHeaderDoNotMutateLibrary() throws Exception {
        String body=json.writeValueAsString(Map.of("name","Meu modelo","fields",List.of(text("Nome"))));
        mvc.perform(post("/admin/templates").contentType("application/json").content(body)).andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/templates").session(session).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        assertThat(templates.list(store)).isEmpty();
    }
    @Test void templateImageUploadRemainsPrivateUntilAppliedAndIsIsolatedByStore() throws Exception {
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(12,10,BufferedImage.TYPE_INT_RGB),"png",out);
        var result=mvc.perform(multipart("/admin/templates/images").file(new MockMultipartFile("file","capa.png","image/png",out.toByteArray()))
                .session(session).header("X-NCF-Action","template-write")).andExpect(status().isOk()).andReturn();
        String id=json.readTree(result.getResponse().getContentAsString()).path("id").asText();
        var model=templates.create(store,"Imagem privada",null,List.of(imageField(id)));
        assertThat(images.readUrl(store,id,true)).isNull();
        long stranger=newStore(PlanType.PREMIUM_PLUS);assertThat(images.adminReadUrl(stranger,id,true)).isNull();
        apply(model,123,StoreTemplateService.Mode.APPEND);assertThat(images.readUrl(store,id,true)).isNotNull();
    }
    @Test void appendPreservesExistingIdentitiesAndAllSupportedDefinitionsRoundTrip() {
        var existing=model("Existente");apply(existing,123,StoreTemplateService.Mode.APPEND);long first=product(123).getFirst().getId();
        var number=new TemplateFieldDefinition("Número",FieldType.NUMBER,true,2,"10","^[0-9]{1,2}$",null,null,true,null);
        var select=new TemplateFieldDefinition("Cor",FieldType.SELECT,false,100,"Escolha",null,"Azul\nVerde",null,true,null);
        var check=new TemplateFieldDefinition("Extras",FieldType.CHECKBOX,true,100,null,null,"A\nB",null,true,null);
        var textarea=new TemplateFieldDefinition("Mensagem",FieldType.TEXTAREA,false,200,"Sua mensagem",null,null,null,true,null);
        var model=templates.create(store,"Conjunto",null,List.of(number,select,check,textarea));apply(model,123,StoreTemplateService.Mode.APPEND);
        assertThat(product(123)).hasSize(5);assertThat(product(123).getFirst().getId()).isEqualTo(first);
        var copied=templates.saveProduct(store,123,"Cópia completa",null);
        assertThat(copied.fields().subList(1,5)).isEqualTo(model.fields());
        assertThat(product(123).stream().filter(f->f.getFieldType()==FieldType.SELECT).findFirst().orElseThrow().isRequired()).isTrue();
    }
    @Test void concurrentDifferentProductsCannotExceedFreeProductQuota() throws Exception {
        var model=model("Nome");var owner=stores.findByStoreId(store).orElseThrow();owner.setPlan(PlanType.FREE);stores.saveAndFlush(owner);
        var first=templates.preview(store,model.key(),123);var second=templates.preview(store,model.key(),124);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{start.await();try{templates.apply(store,model.key(),123,"Um",StoreTemplateService.Mode.APPEND,false,UUID.randomUUID().toString(),first.reviewToken());return true;}catch(StoreTemplateService.Invalid e){return false;}});
            var b=pool.submit(()->{start.await();try{templates.apply(store,model.key(),124,"Dois",StoreTemplateService.Mode.APPEND,false,UUID.randomUUID().toString(),second.reviewToken());return true;}catch(StoreTemplateService.Invalid e){return false;}});
            start.countDown();assertThat(a.get(10,TimeUnit.SECONDS)).isNotEqualTo(b.get(10,TimeUnit.SECONDS));
        }
        assertThat(rules.countByStoreId(store)).isEqualTo(1);
    }
    @Test void spanishLibraryAndMutationErrorsUseSupportedLanguage() throws Exception {
        var owner=stores.findByStoreId(store).orElseThrow();owner.setStoreCountryCode("AR");stores.saveAndFlush(owner);
        mvc.perform(get("/admin/templates").session(session)).andExpect(status().isOk());
        mvc.perform(get("/admin/templates").session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Biblioteca de plantillas")));
        mvc.perform(post("/admin/templates").session(session).header("X-NCF-Action","template-write").contentType("application/json").content("{\"name\":\"\",\"fields\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("error").value(org.hamcrest.Matchers.containsString("Revisá")));
    }

    @Test void batchAppliesOnlySelectedProductsAndReplayDoesNotDuplicateFields() {
        var source=model("Nome");
        var batch=batches.review(store,source.key(),TemplateBatchService.Mode.APPEND,List.of(123L,124L));
        assertThat(batch.items()).extracting(TemplateBatchService.Item::state).containsOnly("READY");
        var result=batches.execute(store,batch.id(),123,true,false);
        assertThat(result.items()).extracting(TemplateBatchService.Item::state).containsExactly("SUCCEEDED","READY");
        batches.execute(store,batch.id(),123,true,false);
        assertThat(product(123)).hasSize(1);assertThat(product(124)).isEmpty();
        assertThat(batches.recent(store).getFirst().succeeded()).isEqualTo(1);
    }
    @Test void batchUpdateTracksIndividualApplicationsAndPreservesExtraFieldsWithExplicitLocalEditApproval() {
        var source=model("Nome");apply(source,123,StoreTemplateService.Mode.APPEND);
        var extra=templates.create(store,"Extra",null,List.of(text("Recado")));apply(extra,123,StoreTemplateService.Mode.APPEND);
        // Reapply the original source via replacement to establish the source we are updating.
        apply(source,123,StoreTemplateService.Mode.REPLACE);
        var field=product(123).getFirst();field.setPlaceholder("Alterado localmente");fields.saveAndFlush(field);
        var updated=templates.update(store,source.id(),source.revision(),"Modelo novo",null,List.of(text("Nome atualizado")));
        var batch=batches.review(store,updated.key(),TemplateBatchService.Mode.UPDATE,List.of(123L));
        assertThat(batch.items().getFirst().modified()).isTrue();
        assertThat(batches.execute(store,batch.id(),123,true,false).items().getFirst().error()).isEqualTo("batch.modified.confirm");
        assertThat(product(123).getFirst().getLabel()).isEqualTo("Nome");
        assertThat(batches.execute(store,batch.id(),123,true,true).items().getFirst().state()).isEqualTo("SUCCEEDED");
        assertThat(product(123)).extracting(PersonalizationField::getLabel).containsExactly("Nome atualizado");
    }
    @Test void batchUpdateKeepsFieldsAddedLocallyAndUnselectedProducts() {
        var source=model("Nome");apply(source,123,StoreTemplateService.Mode.APPEND);apply(source,124,StoreTemplateService.Mode.APPEND);
        var form=text("Recado").toForm(1);admin.addField(store,123L,form);
        var updated=templates.update(store,source.id(),source.revision(),source.name(),null,List.of(text("Nome novo")));
        var batch=batches.review(store,updated.key(),TemplateBatchService.Mode.UPDATE,List.of(123L));
        assertThat(batch.items().getFirst().modified()).isTrue();
        batches.execute(store,batch.id(),123,true,true);
        assertThat(product(123)).extracting(PersonalizationField::getLabel).containsExactlyInAnyOrder("Recado","Nome novo");
        assertThat(product(124)).extracting(PersonalizationField::getLabel).containsExactly("Nome");
    }
    @Test void batchRejectsStaleConfigurationAndTemplateAndHidesOtherStoresBatches() {
        var source=model("Nome");var batch=batches.review(store,source.key(),TemplateBatchService.Mode.APPEND,List.of(123L));
        templates.update(store,source.id(),source.revision(),source.name(),null,List.of(text("Outro")));
        assertThat(batches.execute(store,batch.id(),123,true,false).items().getFirst().error()).isEqualTo("template.stale");
        assertThat(product(123)).isEmpty();
        long other=newStore(PlanType.PREMIUM_PLUS);
        assertThatThrownBy(()->batches.get(other,batch.id())).hasMessage("batch.not.found");
        assertThatThrownBy(()->batches.execute(other,batch.id(),123,true,false)).hasMessage("batch.not.found");
    }
    @Test void batchEnforcesCumulativeProductQuotaAndKeepsFailuresAtomic() {
        var source=model("Nome");
        var batch=batches.review(store,source.key(),TemplateBatchService.Mode.APPEND,List.of(123L,124L));
        var owner=stores.findByStoreId(store).orElseThrow();owner.setPlan(PlanType.FREE);stores.saveAndFlush(owner);
        batches.execute(store,batch.id(),123,true,false);
        var result=batches.execute(store,batch.id(),124,true,false);
        assertThat(result.items().getLast().state()).isEqualTo("FAILED");
        assertThat(result.items().getLast().error()).isEqualTo("template.products.limit");
        assertThat(product(124)).isEmpty();assertThat(product(123)).hasSize(1);
        owner=stores.findByStoreId(store).orElseThrow();owner.setPlan(PlanType.PREMIUM_PLUS);stores.saveAndFlush(owner);
        assertThat(batches.execute(store,batch.id(),124,true,false).items().getLast().state()).isEqualTo("SUCCEEDED");
        assertThat(product(124)).hasSize(1);
    }
    @Test void batchPreviewShowsCumulativeProductLimitsBeforeExecuting() {
        var source=model("Nome");var owner=stores.findByStoreId(store).orElseThrow();owner.setPlan(PlanType.FREE);stores.saveAndFlush(owner);
        var batch=batches.review(store,source.key(),TemplateBatchService.Mode.APPEND,List.of(123L,124L));
        assertThat(batch.items().getLast().state()).isEqualTo("BLOCKED");
        assertThat(batch.items().getLast().error()).isEqualTo("template.products.limit");
        assertThat(batch.items().getLast().after()).hasSize(1);
        assertThat(rules.countByStoreId(store)).isZero();
    }
    @Test void batchFailureAfterWritingAFieldRollsBackTheEntireProductAndCanBeRetried() {
        var original=model("Original");apply(original,123,StoreTemplateService.Mode.APPEND);
        long originalId=product(123).getFirst().getId();
        var replacement=templates.create(store,"Substituição",null,List.of(text("Primeiro"),text("Segundo")));
        var batch=batches.review(store,replacement.key(),TemplateBatchService.Mode.REPLACE,List.of(123L));
        doThrow(new IllegalStateException("test failure")).when(fieldCatalog).remember(argThat(f->"Segundo".equals(f.getLabel())));
        assertThat(batches.execute(store,batch.id(),123,true,false).items().getFirst().state()).isEqualTo("FAILED");
        assertThat(product(123)).extracting(PersonalizationField::getId).containsExactly(originalId);
        reset(fieldCatalog);
        assertThat(batches.execute(store,batch.id(),123,true,false).items().getFirst().state()).isEqualTo("SUCCEEDED");
        assertThat(product(123)).extracting(PersonalizationField::getLabel).containsExactly("Primeiro","Segundo");
    }
    @Test void batchCatalogAndPageAreScopedAndLocalized() throws Exception {
        when(api.listTemplateBatchProducts(any(),eq(1))).thenReturn(json.readTree("[{\"id\":123,\"name\":{\"pt\":\"Caderno\"},\"brand\":\"Marca\",\"categories\":[{\"id\":4,\"name\":{\"pt\":\"Papelaria\"}}]}]"));
        var source=model("Nome");apply(source,123,StoreTemplateService.Mode.APPEND);
        var products=batches.products(store);assertThat(products).hasSize(1);
        assertThat(products.getFirst().configured()).isTrue();assertThat(products.getFirst().sourceKey()).isEqualTo(source.key());
        assertThat(products.getFirst().categories()).containsEntry("4","Papelaria");
        mvc.perform(get("/admin/templates/batch").session(session).param("key",source.key())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Templates em lote")));
        var owner=stores.findByStoreId(store).orElseThrow();owner.setStoreCountryCode("AR");stores.saveAndFlush(owner);
        mvc.perform(get("/admin/templates/batch").session(session).param("key",source.key())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Plantillas en lote")));
        mvc.perform(post("/admin/templates/batch/review").session(session).header("X-NCF-Action","wrong").contentType("application/json")
                .content(json.writeValueAsString(Map.of("key",source.key(),"mode","APPEND","productIds",List.of(123)))))
                .andExpect(status().isBadRequest());
    }

    @Test void namedColorsSurviveTemplatesReopenAndPublicResponse() throws Exception {
        var color=new TemplateFieldDefinition("Cor da estampa",FieldType.COLOR_SELECT,true,100,null,null,
                "[{\"label\":\" Azul marinho \",\"color\":\"#123abc\"},{\"label\":\"Branco\",\"color\":\"#ffffff\"}]",null,true,null,1,false);
        var template=templates.create(store,"Cores",null,List.of(color,text("Nome")));
        apply(template,123,StoreTemplateService.Mode.APPEND);
        var reopened=admin.requireRuleWithFields(store,123L).getFields().getFirst();
        var response=FieldResponse.from(reopened);
        assertThat(response.options()).containsExactly("Azul marinho","Branco");
        assertThat(response.colorOptions()).extracting(ColorOptions.Option::color).containsExactly("#123ABC","#FFFFFF");
        assertThat(response.required()).isTrue();
        assertThat(response.showColorNames()).isFalse();
        assertThat(templates.saveProduct(store,123,"Sem nomes",null).fields().getFirst().showColorNames()).isFalse();
        assertThat(templates.saveProduct(store,123,"Reutilizar",null).fields().getFirst().colorOptions()).isEqualTo(response.colorOptions());
        mvc.perform(get("/admin/products/123/fields").session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("color-options-editor")));
        mvc.perform(get("/admin/templates/review").session(session).param("key",template.key()).param("productId","123"))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("#123ABC")));
        var reordered=color.toForm(0);reordered.setOptionsText("[{\"label\":\"Branco\",\"color\":\"#ffffff\"},{\"label\":\"Azul marinho\",\"color\":\"#123abc\"}]");
        admin.updateField(store,123L,reopened.getId(),reordered);
        assertThat(FieldResponse.from(admin.requireRuleWithFields(store,123L).getFields().getFirst()).options()).containsExactly("Branco","Azul marinho");
        var batch=batches.review(store,template.key(),TemplateBatchService.Mode.APPEND,List.of(789L));
        assertThat(batches.execute(store,batch.id(),789,true,false).items().getFirst().state()).isEqualTo("SUCCEEDED");
        assertThat(admin.requireRuleWithFields(store,789L).getFields().getFirst().isShowColorNames()).isFalse();
        assertThat(admin.requireRuleWithFields(store,789L).getFields().getFirst().options()).containsExactly("Azul marinho","Branco");
        var invalid=color.toForm(0);invalid.setOptionsText("[]");
        assertThatThrownBy(()->admin.updateField(store,123L,reopened.getId(),invalid)).hasMessage("color.options.invalid");
        assertThat(admin.requireRuleWithFields(store,123L).getFields().getFirst().options()).containsExactly("Branco","Azul marinho");
    }
    @Test void fieldOrderIsAtomicScopedAndRejectsStaleOrDuplicateSets() throws Exception {
        var template=templates.create(store,"Ordem",null,List.of(text("Primeiro"),text("Segundo"),text("Terceiro")));
        apply(template,123,StoreTemplateService.Mode.APPEND);apply(template,456,StoreTemplateService.Mode.APPEND);
        var ids=admin.requireRuleWithFields(store,123L).getFields().stream().map(PersonalizationField::getId).toList();
        var reversed=List.of(ids.get(2),ids.get(0),ids.get(1));
        mvc.perform(post("/admin/products/123/fields/order").session(session).header("X-NCF-Action","field-order")
                .contentType("application/json").content(json.writeValueAsString(reversed))).andExpect(status().isOk());
        assertThat(admin.requireRuleWithFields(store,123L).getFields()).extracting(PersonalizationField::getLabel).containsExactly("Terceiro","Primeiro","Segundo");
        assertThat(admin.requireRuleWithFields(store,456L).getFields()).extracting(PersonalizationField::getLabel).containsExactly("Primeiro","Segundo","Terceiro");
        for(var invalid:List.of(List.of(ids.get(0)),List.of(ids.get(0),ids.get(0),ids.get(1)),List.of(1L,2L,3L))) {
            assertThatThrownBy(()->admin.reorderFields(store,123L,invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        long other=newStore(PlanType.PREMIUM_PLUS);
        assertThatThrownBy(()->admin.reorderFields(other,123L,reversed)).isInstanceOf(IllegalArgumentException.class);
        mvc.perform(post("/admin/products/123/fields/order").session(session).contentType("application/json").content(json.writeValueAsString(ids)))
            .andExpect(status().isBadRequest());
        assertThat(admin.requireRuleWithFields(store,123L).getFields()).extracting(PersonalizationField::getId).containsExactlyElementsOf(reversed);
    }
}
