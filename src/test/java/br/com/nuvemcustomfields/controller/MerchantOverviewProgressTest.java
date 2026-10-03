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
class MerchantOverviewProgressTest {
    @Autowired StoreRepository stores;
    @Autowired PersonalizationRuleRepository rules;
    @Autowired PersonalizationFieldRepository fields;
    @Autowired MockMvc mvc;
    @Test void completedProductRemovesWelcomeHeroAndSuggestionsAreAtBottomAndInShortcuts() throws Exception {
        var s=new Store();s.setStoreId(990088772L);s.setStoreCountryCode("BR");stores.saveAndFlush(s);
        var session=new MockHttpSession();session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,s.getStoreId());
        String initial=mvc.perform(get("/admin").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(initial).contains("Comece pelo primeiro produto");
        var r=new PersonalizationRule();r.setStoreId(s.getStoreId());r.setProductId(101L);r.setProductName("Produto pronto");rules.saveAndFlush(r);
        var f=new PersonalizationField();f.setRule(r);f.setLabel("Seu nome");fields.saveAndFlush(f);
        String html=mvc.perform(get("/admin").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("Comece pelo primeiro produto","hero-primary","Configure seu primeiro produto");
        assertThat(html).contains("Confira na loja","merchant-quicklinks","merchant-suggestion-callout");
        assertThat(html.indexOf("merchant-suggestion-callout")).isGreaterThan(html.indexOf("merchant-quicklinks"));
        String nav=html.substring(html.indexOf("<nav"),html.indexOf("</nav>"));
        assertThat(nav).doesNotContain("/admin/suggestions");
        String form=mvc.perform(get("/admin/suggestions").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(form).contains("merchant-suggestions-grid","merchant-suggestion-field","aria-describedby=\"suggestion-privacy\"");
    }
}
