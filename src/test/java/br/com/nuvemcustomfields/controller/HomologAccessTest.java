package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.dto.FieldForm;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"homolog.allowed-store-ids=42", "buyer-uploads.cleanup-delay-ms=10000000"})
@AutoConfigureMockMvc
class HomologAccessTest {
    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;
    @Autowired StoreTemplateService templates;
    @Autowired BuyerUploadLedger uploads;
    @Autowired PersonalizationAdminService admin;
    @Autowired PlanLimitService limits;
    @Autowired PersonalizationFieldRepository fields;

    @BeforeEach void setup() {
        for (long id : new long[]{42,99}) {
            var store = stores.findByStoreId(id).orElseGet(Store::new);
            store.setStoreId(id); store.setAccessToken("test"); store.setPlan(PlanType.PREMIUM_PLUS);
            stores.saveAndFlush(store);
        }
    }
    MockHttpSession session(long id) {
        var session = new MockHttpSession();
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,id);
        return session;
    }
    @Test void onlyListedStoreCanOpenNewFeaturesAndDirectRequestsCannotBypassThePolicy() throws Exception {
        mvc.perform(get("/admin/templates").session(session(42))).andExpect(status().isOk());
        mvc.perform(get("/admin/templates").session(session(99))).andExpect(status().isNotFound());
        mvc.perform(get("/admin/buyer-uploads").session(session(99))).andExpect(status().isNotFound());
        mvc.perform(post("/admin/templates/apply").session(session(99)).contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/admin/products/123/save-template").session(session(99)).contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/public/stores/99/buyer-uploads/session")).andExpect(status().isNotFound());
        assertThatThrownBy(() -> templates.list(99)).hasMessageContaining("template.not.found");
        assertThat(uploads.usage(42).acceptsUploads()).isTrue();
        assertThat(uploads.usage(99).acceptsUploads()).isFalse();
    }
    @Test void traditionalFieldsRemainAvailableAndFileFieldsAreNotPublishedForUnlistedStore() throws Exception {
        long product=998877L;
        admin.ensureRule(99L, product, "Demo");
        var form=new FieldForm(); form.setLabel("Nome"); form.setFieldType(FieldType.TEXT);
        admin.addField(99L,product,form);
        var traditional=fields.findWithRuleByStoreId(99L).stream()
                .filter(f -> f.getRule().getProductId().equals(product)).findFirst().orElseThrow();
        var file=new PersonalizationField();file.setRule(traditional.getRule());
        file.setFieldType(FieldType.FILE_UPLOAD);file.setSortOrder(1);
        var store=stores.findByStoreId(99L).orElseThrow();
        assertThat(limits.storefrontFields(store,java.util.List.of(traditional,file))).containsExactly(traditional);
        form.setFieldType(FieldType.FILE_UPLOAD);
        assertThatThrownBy(() -> admin.addField(99L,product,form)).hasMessageContaining("upload.unavailable");
        mvc.perform(get("/admin/products/"+product+"/fields").session(session(99))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("href=\"/admin/templates\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("href=\"/admin/buyer-uploads\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("value=\"FILE_UPLOAD\""))));
    }
}
