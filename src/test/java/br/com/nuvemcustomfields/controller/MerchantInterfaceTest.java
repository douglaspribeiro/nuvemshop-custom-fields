package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.MerchantInterfacePreference.Choice;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.FeatureAccessProperties;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.MerchantInterfaceService;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static br.com.nuvemcustomfields.properties.FeatureAccessProperties.Feature.MERCHANT_INTERFACE_PREVIEW;
import static br.com.nuvemcustomfields.properties.FeatureAccessProperties.Feature.PRODUCT_COMPLEMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MerchantInterfaceTest {
    private static final AtomicLong STORE_IDS = new AtomicLong(987000L);
    private static final String CSRF = "merchantUiCsrf";

    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;
    @Autowired MerchantInterfaceService preferences;
    @MockitoBean FeatureAccessProperties features;

    @Test void eligibleMerchantCanDeclineThenOptInAndReturnToClassic() throws Exception {
        long storeId = createStore();
        var session = session(storeId);
        when(features.allows(storeId, MERCHANT_INTERFACE_PREVIEW)).thenReturn(true);

        overview(session, true, false, true).andExpect(model().attributeExists(CSRF));
        assertThat(preferences.getChoice(storeId)).isEmpty();
        String csrf = (String) session.getAttribute(CSRF);

        mvc.perform(choose(session, "decline").param("csrf", csrf)).andExpect(redirectedUrl("/admin"));
        assertThat(preferences.getChoice(storeId)).contains(Choice.DECLINE);
        overview(session, true, false, false);

        mvc.perform(choose(session, "preview").param("csrf", csrf)).andExpect(redirectedUrl("/admin"));
        var nextSession = session(storeId);
        overview(nextSession, true, true, false);
        mvc.perform(get("/admin/help").session(nextSession)).andExpect(status().isOk())
                .andExpect(model().attribute("newMerchantUi", true));
        mvc.perform(get("/support/").session(nextSession)).andExpect(status().isOk())
                .andExpect(model().attribute("newMerchantUi", true));
        mvc.perform(get("/support/")).andExpect(status().isOk())
                .andExpect(model().attribute("newMerchantUi", false))
                .andExpect(model().attributeDoesNotExist(CSRF));

        mvc.perform(choose(session, "classic").param("csrf", csrf)).andExpect(redirectedUrl("/admin"));
        assertThat(preferences.getChoice(storeId)).contains(Choice.CLASSIC);
        overview(nextSession, true, false, false);
    }

    @Test void revokingGrantUsesClassicWithoutChangingTheRecordedChoice() throws Exception {
        long storeId = createStore();
        var session = session(storeId);
        when(features.allows(storeId, MERCHANT_INTERFACE_PREVIEW)).thenReturn(true);
        overview(session, true, false, true);
        String csrf = (String) session.getAttribute(CSRF);
        mvc.perform(choose(session, "preview").param("csrf", csrf)).andExpect(redirectedUrl("/admin"));

        when(features.allows(storeId, MERCHANT_INTERFACE_PREVIEW)).thenReturn(false);
        when(features.allows(storeId, PRODUCT_COMPLEMENTS)).thenReturn(true);
        overview(session, false, false, false).andExpect(model().attributeDoesNotExist(CSRF));
        mvc.perform(choose(session, "classic").param("csrf", csrf)).andExpect(status().isForbidden());
        assertThat(preferences.getChoice(storeId)).contains(Choice.PREVIEW);
    }

    @Test void onlyValidRequestsForTheSessionStoreCanChangeItsChoice() throws Exception {
        long first = createStore();
        long second = createStore();
        var session = session(first);
        when(features.allows(first, MERCHANT_INTERFACE_PREVIEW)).thenReturn(true);
        when(features.allows(second, MERCHANT_INTERFACE_PREVIEW)).thenReturn(true);
        overview(session, true, false, true);
        String csrf = (String) session.getAttribute(CSRF);

        mvc.perform(get("/admin/interface").session(session)).andExpect(status().isMethodNotAllowed());
        mvc.perform(choose(session, "preview")).andExpect(status().isForbidden());
        mvc.perform(choose(session, "preview").param("csrf", "invalid")).andExpect(status().isForbidden());
        mvc.perform(choose(session, "yes").param("csrf", csrf)).andExpect(status().isBadRequest());
        assertThat(preferences.getChoice(first)).isEmpty();

        var anotherSession = session(second);
        overview(anotherSession, true, false, true);
        mvc.perform(choose(anotherSession, "preview").param("csrf", csrf)).andExpect(status().isForbidden());
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, second);
        mvc.perform(choose(session, "preview").param("csrf", csrf)).andExpect(status().isForbidden());
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, first);

        mvc.perform(choose(session, "preview").param("csrf", csrf).param("storeId", Long.toString(second))
                .param("returnTo", "https://example.test/")).andExpect(redirectedUrl("/admin"));
        assertThat(preferences.getChoice(first)).contains(Choice.PREVIEW);
        assertThat(preferences.getChoice(second)).isEmpty();

        session.setAttribute(BackofficeSessionInterceptor.STORE_MODE_SESSION_KEY, true);
        overview(session, true, true, false).andExpect(model().attributeDoesNotExist(CSRF));
        mvc.perform(choose(session, "classic").param("csrf", csrf)).andExpect(status().isForbidden());
        assertThat(preferences.getChoice(first)).contains(Choice.PREVIEW);
    }

    private ResultActions overview(MockHttpSession session, boolean eligible, boolean preview, boolean invite) throws Exception {
        return mvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("merchantUiEligible", eligible))
                .andExpect(model().attribute("newMerchantUi", preview))
                .andExpect(model().attribute("merchantUiInvite", invite));
    }

    private MockHttpServletRequestBuilder choose(MockHttpSession session, String choice) {
        return post("/admin/interface").session(session).param("choice", choice);
    }

    private long createStore() {
        var store = new Store();
        store.setStoreId(STORE_IDS.incrementAndGet());
        store.setAccessToken("test");
        store.setPlan(PlanType.FREE);
        store.setStoreCountryCode("BR");
        stores.saveAndFlush(store);
        return store.getStoreId();
    }

    private MockHttpSession session(long storeId) {
        var session = new MockHttpSession();
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, storeId);
        return session;
    }
}
