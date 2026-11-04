package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConfigurationPlanPageTest {
    @Autowired StoreRepository stores;
    @Autowired PersonalizationRuleRepository rules;
    @Autowired PersonalizationFieldRepository fields;
    @Autowired MockMvc mvc;
    @Autowired jakarta.persistence.EntityManager em;

    @Test void displaysActualPauseBeforeReconcilerAndClearsWarningAfterPaidRecovery() throws Exception {
        var store=new Store(); store.setStoreId(9900401L); store.setStoreCountryCode("BR"); stores.saveAndFlush(store);
        for(long id=10; id<=20; id+=10) {
            var rule=new PersonalizationRule(); rule.setStoreId(store.getStoreId()); rule.setProductId(id); rules.saveAndFlush(rule);
            var field=new PersonalizationField(); field.setRule(rule); field.setLabel("Nome"); fields.saveAndFlush(field);
        }
        em.clear(); // New HTTP request observes collections loaded from the committed-shaped fixture.
        var session=new MockHttpSession(); session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,store.getStoreId());
        String html=mvc.perform(get("/admin/products/20/fields").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Personalização pausada pelo plano", "Seus campos e pedidos continuam salvos", "Reativar assinatura");
        store.setPlan(PlanType.PREMIUM); stores.saveAndFlush(store);
        em.clear();
        String recovered=mvc.perform(get("/admin/products/20/fields").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(recovered).doesNotContain("Personalização pausada pelo plano");
    }
}
