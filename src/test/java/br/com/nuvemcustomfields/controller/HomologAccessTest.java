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
    @org.springframework.test.context.bean.override.mockito.MockitoBean BuyerFileStorage storage;

    @BeforeEach void setup() {
        for (long id : new long[]{42,99}) {
            var store = stores.findByStoreId(id).orElseGet(Store::new);
            store.setStoreId(id); store.setAccessToken("test"); store.setPlan(PlanType.PREMIUM_PLUS);
            store.setManualPlanOverride(false);stores.saveAndFlush(store);
        }
        org.mockito.Mockito.when(storage.forStore(org.mockito.ArgumentMatchers.anyLong())).thenReturn(storage);
        org.mockito.Mockito.when(storage.enabled()).thenReturn(true);
    }
    MockHttpSession session(long id) {
        var session = new MockHttpSession();
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,id);
        return session;
    }
    @Test void templatesAndPaidUploadsAreAvailableOutsideTheFormerAllowlist() throws Exception {
        mvc.perform(get("/admin/templates").session(session(99))).andExpect(status().isOk());
        mvc.perform(get("/admin/buyer-uploads").session(session(99))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/admin/buyer-uploads\"")));
        assertThat(templates.list(99)).isNotNull();
        var definition=new br.com.nuvemcustomfields.dto.TemplateFieldDefinition("Nome",FieldType.TEXT,true,40,"Exemplo",null,null,null,true,null);
        var model=templates.create(99,"Modelo liberado",null,java.util.List.of(definition));
        assertThat(templates.get(99,model.key()).name()).isEqualTo("Modelo liberado");
        assertThatThrownBy(()->templates.get(42,model.key())).hasMessageContaining("template.not.found");
        assertThat(uploads.usage(99).acceptsUploads()).isTrue();
        long product=998878L;admin.ensureRule(99L,product,"Personalizado");
        var form=new FieldForm();form.setLabel("Arte");form.setFieldType(FieldType.FILE_UPLOAD);admin.addField(99L,product,form);
        var file=fields.findWithRuleByStoreId(99L).stream().filter(f->f.getRule().getProductId().equals(product)).findFirst().orElseThrow();
        assertThat(limits.storefrontFields(stores.findByStoreId(99L).orElseThrow(),java.util.List.of(file))).containsExactly(file);
        mvc.perform(get("/public/stores/99/personalization").param("productId",Long.toString(product)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fields[0].fieldType").value("FILE_UPLOAD"));
        mvc.perform(get("/admin/products/"+product+"/fields").session(session(99))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"FILE_UPLOAD\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-save-product-template")));
        mvc.perform(post("/public/stores/99/buyer-uploads/session")).andExpect(status().isOk());
    }
    @Test void ultraOverrideHasTheSameUploadAccessAndFreePlanStillCannotUpload() throws Exception {
        var store=stores.findByStoreId(99L).orElseThrow();store.setPlan(PlanType.PREMIUM_ULTRA);store.setManualPlanOverride(true);stores.saveAndFlush(store);
        assertThat(uploads.usage(99).acceptsUploads()).isTrue();
        mvc.perform(get("/admin/buyer-uploads").session(session(99))).andExpect(status().isOk());
        store.setPlan(PlanType.FREE);store.setManualPlanOverride(false);stores.saveAndFlush(store);
        assertThat(uploads.usage(99).acceptsUploads()).isFalse();
        mvc.perform(get("/admin/templates").session(session(99))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("href=\"/admin/buyer-uploads\""))));
        mvc.perform(post("/public/stores/99/buyer-uploads/session")).andExpect(status().isBadRequest());
    }
}
