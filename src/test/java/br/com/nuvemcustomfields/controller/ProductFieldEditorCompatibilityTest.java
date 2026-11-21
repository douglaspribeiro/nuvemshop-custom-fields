package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.entity.MerchantInterfacePreference.Choice;
import br.com.nuvemcustomfields.properties.FeatureAccessProperties;
import br.com.nuvemcustomfields.service.MerchantInterfaceService;
import br.com.nuvemcustomfields.dto.FieldForm;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.PersonalizationAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class ProductFieldEditorCompatibilityTest {
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired StoreRepository stores;
    @Autowired PersonalizationAdminService admin;
    @Autowired MockMvc mvc;
    @Autowired MerchantInterfaceService merchantInterface;
    @org.springframework.test.context.bean.override.mockito.MockitoBean FeatureAccessProperties grants;
    private void useNewEditor(long storeId) {
        org.mockito.Mockito.when(grants.allows(storeId, FeatureAccessProperties.Feature.MERCHANT_INTERFACE_PREVIEW)).thenReturn(true);
        assertThat(merchantInterface.choose(storeId, Choice.PREVIEW)).isTrue();
    }
    @Test void duplicatesOnlyWithinTheProductAndPersistsAsAnIndependentField() throws Exception {
        long storeId=990088781L, productId=124L;
        var store=new Store();store.setStoreId(storeId);store.setStoreCountryCode("BR");store.setPlan(PlanType.PREMIUM_PLUS);stores.saveAndFlush(store);
        useNewEditor(storeId);
        admin.ensureRule(storeId,productId,"Produto");admin.ensureRule(storeId,productId+1,"Outro");
        var source=new FieldForm();source.setLabel("Nome");source.setMaxLength(32);source.setPlaceholder("Digite");source.setRequired(true);source.setSortOrder(0);admin.addField(storeId,productId,source);
        entityManager.flush();entityManager.clear();
        var original=admin.requireRuleWithFields(storeId,productId).getFields().getFirst();var session=new MockHttpSession();session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,storeId);
        var html=mvc.perform(get("/admin/products/"+productId+"/fields").param("duplicateFieldId",original.getId().toString()).session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Nome c&oacute;pia", "data-initial-dirty=\"true\"");
        assertThat(admin.requireRuleWithFields(storeId,productId).getFields()).hasSize(1);
        mvc.perform(post("/admin/products/"+productId+"/fields").session(session).param("label","Nome (cópia)").param("fieldType","TEXT").param("required","true").param("maxLength","32").param("placeholder","Digite").param("sortOrder","1")).andExpect(status().is3xxRedirection());
        entityManager.flush();entityManager.clear();
        var saved=admin.requireRuleWithFields(storeId,productId).getFields();assertThat(saved).hasSize(2);assertThat(saved.get(0).getLabel()).isEqualTo("Nome");assertThat(saved.get(1).getLabel()).isEqualTo("Nome (cópia)");
        var other=mvc.perform(get("/admin/products/"+(productId+1)+"/fields").param("duplicateFieldId",original.getId().toString()).session(session)).andExpect(status().isNotFound());
    }
    @Test void editingExistingConfigurationPreservesItsRuleAndSeparatesCreation() throws Exception {
        long storeId = 990088779L, productId = 123L;
        var store = new Store(); store.setStoreId(storeId); store.setStoreCountryCode("BR"); store.setPlan(PlanType.PREMIUM_PLUS);
        stores.saveAndFlush(store); admin.ensureRule(storeId, productId, "Produto já configurado");
        var text = new FieldForm(); text.setLabel("Código para gravar"); text.setRequired(true);
        text.setValidationPattern("^[A-Z]{2}[0-9]{4}$"); text.setPlaceholder("AB1234"); text.setMaxLength(6); text.setSortOrder(3);
        admin.addField(storeId, productId, text);
        entityManager.flush(); entityManager.clear();
        var session = new MockHttpSession(); session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, storeId);
        var html = mvc.perform(get("/admin/products/" + productId + "/fields").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("^[A-Z]{2}[0-9]{4}$", "AB1234", "Adicionar novo campo", "class=\"field-position\" value=\"4\"");
        assertThat(html.indexOf("field-summary")).isLessThan(html.indexOf("field-create-panel"));
        var createTag = java.util.regex.Pattern.compile("<details\\b[^>]*\\bid=\"new-field\"[^>]*>").matcher(html);
        assertThat(createTag.find()).as("O formulário de novo campo deve existir").isTrue();
        assertThat(createTag.group()).doesNotContain(" open");
        var id = admin.requireRuleWithFields(storeId, productId).getFields().getFirst().getId();
        mvc.perform(post("/admin/products/" + productId + "/fields/" + id).session(session)
                .param("label", "Código para gravar").param("fieldType", "TEXT").param("required", "true")
                .param("validationPattern", "^[A-Z]{2}[0-9]{4}$").param("placeholder", "AB1234")
                .param("maxLength", "6").param("sortOrder", "3"))
                .andExpect(status().is3xxRedirection());
        entityManager.flush(); entityManager.clear();
        var saved = admin.requireRuleWithFields(storeId, productId).getFields();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getValidationPattern()).isEqualTo("^[A-Z]{2}[0-9]{4}$");
        assertThat(saved.getFirst().getPlaceholder()).isEqualTo("AB1234");
        assertThat(saved.getFirst().getMaxLength()).isEqualTo(6);
        assertThat(saved.getFirst().isRequired()).isTrue();
        assertThat(saved.getFirst().getSortOrder()).isEqualTo(3);
    }
    @Test void offersAnotherFieldAfterSavingOnlyWhenThereIsCapacity() throws Exception {
        for (var plan : java.util.List.of(PlanType.FREE, PlanType.PREMIUM_PLUS)) {
            long storeId = 990088910L + plan.ordinal();
            var store = new Store();
            store.setStoreId(storeId);
            store.setStoreCountryCode("BR");
            store.setPlan(plan);
            stores.saveAndFlush(store);
            useNewEditor(storeId);
            admin.ensureRule(storeId, 123L, "Agenda");
            var session = new MockHttpSession();
            session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, storeId);
            String path = "/admin/products/123/fields";
            var result = mvc.perform(post(path).session(session)
                            .param("label", "Nome na capa").param("fieldType", "TEXT"))
                    .andExpect(status().is3xxRedirection()).andReturn();
            assertThat(result.getFlashMap().get("fieldSaved")).isEqualTo(true);
            var html = mvc.perform(get(path).session(session).flashAttrs(result.getFlashMap()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            if (plan == PlanType.FREE) {
                assertThat(html).contains("Seu plano atual atingiu o limite de campos por produto")
                        .doesNotContain(">Adicionar outro campo</a>");
            } else {
                assertThat(html).contains(">Adicionar outro campo</a>", "id=\"new-field\"");
            }
        }
    }

    @Test void fieldSelectorsExplainAndDisableUnavailableTypesForEveryPlan() throws Exception {
        long nextId = 990088850L;
        for (var plan : PlanType.values()) {
            long id = nextId++;
            var store = new Store(); store.setStoreId(id); store.setStoreCountryCode("BR"); store.setPlan(plan);
            stores.saveAndFlush(store); admin.ensureRule(id, 140L, "Personalizado");
            // Paid plans render both the creation and editing selectors.
            if (plan.isBillable()) {
                var text = new FieldForm(); text.setLabel("Nome"); admin.addField(id, 140L, text);
            }
            entityManager.flush(); entityManager.clear();
            var session = new MockHttpSession(); session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, id);
            var html = mvc.perform(get("/admin/products/140/fields").session(session))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            for (var type : FieldType.values()) {
                boolean blocked = switch (type) {
                    case QUANTITY_SELECT, FILE_UPLOAD -> !plan.supportsQuantityOptions();
                    case IMAGE_SELECT -> !plan.isBillable();
                    default -> false;
                };
                var options = java.util.regex.Pattern.compile("<option[^>]*value=\"" + type + "\"[^>]*>").matcher(html);
                int count = 0;
                while (options.find()) {
                    assertThat(options.group().contains("disabled=\"disabled\""))
                            .as("%s on %s", type, plan).isEqualTo(blocked);
                    count++;
                }
                assertThat(count).as("Selectors for %s on %s", type, plan).isEqualTo(plan.isBillable() ? 2 : 1);
            }
            if (!plan.supportsQuantityOptions()) assertThat(html).contains("disponíveis nos planos Pro e Ultra", "O envio de arquivos está disponível");
            if (!plan.isBillable()) assertThat(html).contains("Seu plano não inclui opções com imagem");
        }
    }

    @Test void imageProductLimitAlsoDisablesSelectionBeforeSaving() throws Exception {
        long id = 990088860L;
        var store = new Store(); store.setStoreId(id); store.setStoreCountryCode("BR"); store.setPlan(PlanType.PREMIUM);
        stores.saveAndFlush(store);
        var rule = admin.ensureRule(id, 141L, "Produto com imagem");
        var image = new PersonalizationField(); image.setRule(rule); image.setLabel("Modelo"); image.setFieldType(FieldType.IMAGE_SELECT);
        entityManager.persist(image); entityManager.flush();
        admin.ensureRule(id, 142L, "Outro produto");
        entityManager.flush(); entityManager.clear();
        var session = new MockHttpSession(); session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, id);
        var html = mvc.perform(get("/admin/products/142/fields").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).containsPattern("<option(?=[^>]*disabled=\"disabled\")(?=[^>]*value=\"IMAGE_SELECT\")[^>]*>");
        assertThat(html).contains("Seu plano permite imagens em até 1 produto(s)");
        // The product already using the image allowance remains configurable.
        html = mvc.perform(get("/admin/products/141/fields").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var options = java.util.regex.Pattern.compile("<option[^>]*value=\"IMAGE_SELECT\"[^>]*>").matcher(html);
        while (options.find()) assertThat(options.group()).doesNotContain("disabled");
    }

    @Test void quantityOptionsPersistOnlyForProAndUltraAndRejectDowngradedEdits() throws Exception {
        long nextId=990088800L;
        for (var plan : new PlanType[]{PlanType.FREE,PlanType.PREMIUM,PlanType.PREMIUM_PLUS,PlanType.PREMIUM_ULTRA}) {
            long id=nextId++;var store=new Store();store.setStoreId(id);store.setStoreCountryCode("BR");store.setPlan(plan);
            stores.saveAndFlush(store);admin.ensureRule(id,130L,"Buquê");
            var session=new MockHttpSession();session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,id);
            var response=mvc.perform(post("/admin/products/130/fields").session(session).param("label","Flores")
                    .param("fieldType","QUANTITY_SELECT").param("optionsText","Rosa\nMargarida")
                    .param("minSelections","7").param("maxSelections","7").param("quantityColumns","3")).andReturn().getResponse();
            if (!plan.supportsQuantityOptions()) {
                assertThat(response.getStatus()).isEqualTo(200);
                assertThat(response.getContentAsString()).contains("disponíveis nos planos Pro e Ultra");
                assertThat(admin.requireRuleWithFields(id,130L).getFields()).isEmpty();
                continue;
            }
            assertThat(response.getStatus()).isEqualTo(302);entityManager.flush();entityManager.clear();
            var field=admin.requireRuleWithFields(id,130L).getFields().getFirst();
            assertThat(field.getFieldType()).isEqualTo(FieldType.QUANTITY_SELECT);
            assertThat(field.getQuantityColumns()).isEqualTo(3);
            assertThat(br.com.nuvemcustomfields.dto.FieldResponse.from(field).quantityColumns()).isEqualTo(3);
            assertThat(field.getMinSelections()).isEqualTo(7);assertThat(field.getMaxSelections()).isEqualTo(7);
            var html=mvc.perform(get("/admin/products/130/fields").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(html).contains("QUANTITY_SELECT","Opções com quantidade");
            var persisted=stores.findByStoreId(id).orElseThrow();persisted.setPlan(PlanType.PREMIUM);stores.saveAndFlush(persisted);
            var form=new FieldForm();form.setLabel("Alterada");form.setFieldType(FieldType.QUANTITY_SELECT);form.setOptionsText("Rosa");
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(()->admin.updateField(id,130L,field.getId(),form))
                    .withMessage("quantity.plan.unavailable");
            assertThat(field.getOptionsText()).isEqualTo("Rosa\nMargarida");
        }
    }
    @Test void checkboxSelectionLimitsPersistReloadAndClearOnTypeChange() throws Exception {
        long storeId=990088789L, productId=129L;
        var store=new Store();store.setStoreId(storeId);store.setStoreCountryCode("BR");store.setPlan(PlanType.PREMIUM_PLUS);
        stores.saveAndFlush(store);admin.ensureRule(storeId,productId,"Buquê");
        var session=new MockHttpSession();session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,storeId);
        String path="/admin/products/"+productId+"/fields";
        mvc.perform(post(path).session(session).param("label","Flores").param("fieldType","CHECKBOX")
                .param("optionsText","1\n2\n3").param("minSelections","2").param("maxSelections","2"))
                .andExpect(status().is3xxRedirection());
        entityManager.flush();entityManager.clear();
        var field=admin.requireRuleWithFields(storeId,productId).getFields().getFirst();
        assertThat(field.getMinSelections()).isEqualTo(2);assertThat(field.getMaxSelections()).isEqualTo(2);
        String html=mvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("name=\"minSelections\"", "name=\"maxSelections\"", "Mínimo de seleções", "Máximo de seleções");
        mvc.perform(post(path+"/"+field.getId()).session(session).param("label","Flores").param("fieldType","CHECKBOX")
                .param("optionsText","1\n2\n3").param("minSelections","3").param("maxSelections","2"))
                .andExpect(status().isOk());
        entityManager.flush();entityManager.clear();
        field=admin.requireRuleWithFields(storeId,productId).getFields().getFirst();
        assertThat(field.getMinSelections()).isEqualTo(2);
        mvc.perform(post(path+"/"+field.getId()).session(session).param("label","Flores").param("fieldType","TEXT"))
                .andExpect(status().is3xxRedirection());
        entityManager.flush();entityManager.clear();
        field=admin.requireRuleWithFields(storeId,productId).getFields().getFirst();
        assertThat(field.getMinSelections()).isNull();assertThat(field.getMaxSelections()).isNull();
    }
    @Test void selectPromptIsSavedAndListsRequireAChoiceIncludingExistingOptionalFields() throws Exception {
        long storeId = 990088780L, productId = 124L;
        var store = new Store(); store.setStoreId(storeId); store.setStoreCountryCode("BR"); store.setPlan(PlanType.PREMIUM_PLUS);
        stores.saveAndFlush(store); admin.ensureRule(storeId, productId, "Café");
        var session = new MockHttpSession(); session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, storeId);
        mvc.perform(post("/admin/products/" + productId + "/fields").session(session)
                .param("label", "Moagem").param("fieldType", "SELECT")
                .param("placeholder", "Escolha a moagem").param("optionsText", "Grano\nMolido")
                .param("maxLength", "100").param("sortOrder", "0"))
                .andExpect(status().is3xxRedirection());
        entityManager.flush(); entityManager.clear();
        var saved = admin.requireRuleWithFields(storeId, productId).getFields().getFirst();
        assertThat(saved.getPlaceholder()).isEqualTo("Escolha a moagem");
        assertThat(saved.isRequired()).isTrue();
        var response = br.com.nuvemcustomfields.dto.FieldResponse.from(saved);
        assertThat(response.placeholder()).isEqualTo("Escolha a moagem");
        assertThat(response.options()).containsExactly("Grano", "Molido");
        saved.setRequired(false);
        assertThat(br.com.nuvemcustomfields.dto.FieldResponse.from(saved).required()).isTrue();
    }
    @Test void checkboxCanBeCreatedReloadedAndMadeRequiredWithoutTextConstraints() throws Exception {
        long storeId = 990088781L, productId = 125L;
        var store = new Store(); store.setStoreId(storeId); store.setStoreCountryCode("BR"); store.setPlan(PlanType.PREMIUM_PLUS);
        stores.saveAndFlush(store); admin.ensureRule(storeId, productId, "Presente");
        var session = new MockHttpSession(); session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, storeId);
        mvc.perform(post("/admin/products/" + productId + "/fields").session(session)
                .param("label", "Embrulhar para presente").param("fieldType", "CHECKBOX")
                .param("validationPattern", "[").param("placeholder", "Texto antigo"))
                .andExpect(status().is3xxRedirection());
        entityManager.flush(); entityManager.clear();
        var saved = admin.requireRuleWithFields(storeId, productId).getFields().getFirst();
        assertThat(saved.getFieldType()).isEqualTo(FieldType.CHECKBOX);
        assertThat(saved.isRequired()).isFalse();
        assertThat(saved.getValidationPattern()).isNull();
        assertThat(saved.getPlaceholder()).isNull();
        assertThat(saved.options()).isEmpty();
        var response = br.com.nuvemcustomfields.dto.FieldResponse.from(saved);
        assertThat(response.fieldType()).isEqualTo(FieldType.CHECKBOX);
        assertThat(response.required()).isFalse();
        var html = mvc.perform(get("/admin/products/" + productId + "/fields").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("value=\"CHECKBOX\"", "Caixas de seleção (checkbox)");
        mvc.perform(post("/admin/products/" + productId + "/fields/" + saved.getId()).session(session)
                .param("label", "Adicionais").param("fieldType", "CHECKBOX").param("required", "true")
                .param("optionsText", "Laço\nCartão\nLaço\nEmbalagem"))
                .andExpect(status().is3xxRedirection());
        entityManager.flush(); entityManager.clear();
        saved = admin.requireRuleWithFields(storeId, productId).getFields().getFirst();
        assertThat(br.com.nuvemcustomfields.dto.FieldResponse.from(saved).required()).isTrue();
        assertThat(br.com.nuvemcustomfields.dto.FieldResponse.from(saved).options()).containsExactly("Laço", "Cartão", "Embalagem");
        assertThat(saved.getLabel()).isEqualTo("Adicionais");
        mvc.perform(get("/admin/products/" + productId + "/fields").session(session))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Cartão")));
    }

}
