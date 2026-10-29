package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.dto.FieldForm;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class VariantPersonalizationTest {
    @Autowired StoreRepository stores;
    @Autowired PersonalizationAdminService admin;
    @Autowired MockMvc mvc;
    @Autowired jakarta.persistence.EntityManager em;
    @MockitoBean NuvemshopApiClient api;

    @Test void savesReloadsPublishesConditionAndWarnsAboutDeletedVariants() throws Exception {
        var store=new Store();store.setStoreId(99002021L);store.setStoreCountryCode("BR");store.setPlan(PlanType.PREMIUM_PLUS);
        stores.saveAndFlush(store);admin.ensureRule(store.getStoreId(),456L,"Camiseta");
        var field=new FieldForm();field.setLabel("Nome");field.setRequired(true);admin.addField(store.getStoreId(),456L,field);
        when(api.listProductVariants(any(Store.class),eq(456L))).thenReturn(List.of(
            new NuvemshopApiClient.ProductVariant(101L,"P / Sem personalização","50.00"),
            new NuvemshopApiClient.ProductVariant(102L,"P / Com personalização","60.00")));
        var session=new MockHttpSession();session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,store.getStoreId());
        mvc.perform(post("/admin/products/456/variants").session(session).param("restricted","true").param("variantIds","102"))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("message"));
        em.flush();em.clear();
        var rule=admin.requireRuleWithFields(store.getStoreId(),456L);
        assertThat(rule.isVariantRestricted()).isTrue();assertThat(rule.getVariantIds()).containsExactly(102L);
        mvc.perform(get("/public/stores/99002021/personalization").param("productId","456"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.variantRestricted").value(true)).andExpect(jsonPath("$.variantIds[0]").value(102));
        when(api.listProductVariants(any(Store.class),eq(456L))).thenReturn(List.of(new NuvemshopApiClient.ProductVariant(101L,"Sem","50.00")));
        var html=mvc.perform(get("/admin/products/456/fields").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("102", "Variantes selecionadas foram removidas");
        mvc.perform(post("/admin/products/456/variants").session(session).param("restricted","true").param("variantIds","102"))
            .andExpect(flash().attributeExists("error"));
        assertThat(admin.requireRuleWithFields(store.getStoreId(),456L).getVariantIds()).containsExactly(102L);
    }
}
