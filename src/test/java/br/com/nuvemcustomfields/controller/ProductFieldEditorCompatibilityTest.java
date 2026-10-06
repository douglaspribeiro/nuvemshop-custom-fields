package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
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
        var createTag = html.substring(html.indexOf("<details class=\"panel field-create-panel\""));
        assertThat(createTag.substring(0, createTag.indexOf('>'))).doesNotContain("open");
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
}
