package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.dto.FieldForm;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.NuvemshopApiClient;
import br.com.nuvemcustomfields.service.PersonalizationAdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Opt-in export of production templates with isolated, fictional demo data. */
@EnabledIfSystemProperty(named = "marketing.export", matches = "true")
@SpringBootTest @AutoConfigureMockMvc
class MarketingScreensExportTest {
    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;
    @Autowired PersonalizationAdminService admin;
    @Autowired ObjectMapper mapper;
    @MockitoBean NuvemshopApiClient api;

    @Test void exportActualMerchantScreens() throws Exception {
        var store = new Store(); store.setStoreId(990077001L); store.setAccessToken("demo");
        store.setStoreCountryCode("BR"); store.setStoreName("Ateliê Aurora · Demonstração");
        store.setPlan(PlanType.PREMIUM_PLUS); store.setScope("read_products,read_orders");
        store.setProductTextColor("#176b57"); store.setCartTextColor("#176b57"); store.setCheckoutTextColor("#176b57");
        stores.saveAndFlush(store);
        admin.ensureRule(store.getStoreId(), 7001L, "Caderno personalizado");
        var name = new FieldForm(); name.setLabel("Nome na capa"); name.setPlaceholder("Ex.: Ana Clara");
        name.setMaxLength(30); name.setRequired(true); admin.addField(store.getStoreId(), 7001L, name);
        var message = new FieldForm(); message.setLabel("Mensagem para presente"); message.setFieldType(FieldType.TEXTAREA);
        message.setPlaceholder("Escreva uma mensagem especial"); message.setMaxLength(180); message.setSortOrder(1);
        admin.addField(store.getStoreId(), 7001L, message);
        var finish = new FieldForm(); finish.setLabel("Acabamento"); finish.setFieldType(FieldType.SELECT);
        finish.setOptionsText("Espiral dourado\nEspiral branco\nEspiral preto"); finish.setSortOrder(2);
        admin.addField(store.getStoreId(), 7001L, finish);
        when(api.listRecentOrders(any())).thenReturn(mapper.readTree("""
                [{"id":1042,"number":"1042","created_at":"2026-10-05T13:30:00Z","products":[{"properties":{"Nome na capa":"Ana Clara","Capa":"Floral","Acabamento":"Espiral dourado"}}]},
                 {"id":1041,"number":"1041","created_at":"2026-10-05T12:15:00Z","products":[{"properties":{"Nome na capa":"Marina","Capa":"Geométrica","Mensagem":"Feito com carinho!"}}]},
                 {"id":1040,"number":"1040","created_at":"2026-10-04T18:00:00Z","products":[{"properties":{"Nome na capa":"Pedro","Acabamento":"Espiral preto"}}]}]
                """));
        var session = new MockHttpSession(); session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, store.getStoreId());
        var output = Path.of("docs/marketing/loja-aplicativos/fontes/telas"); Files.createDirectories(output);
        for (var page : new String[][]{{"campos", "/admin/products/7001/fields"},
                {"aparencia", "/admin/settings/style"}, {"pedidos", "/admin/dashboard"}}) {
            var html = mvc.perform(get(page[1]).session(session)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            Files.writeString(output.resolve(page[0] + ".html"), html);
        }
    }
}
