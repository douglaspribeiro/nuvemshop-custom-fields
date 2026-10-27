package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.StoreAdminEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BackofficeStoreEmailPageTest {
    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;
    @MockitoBean StoreAdminEmailService emails;

    @Test void rendersFormAndProtectsSendingWithAuthenticationAndToken() throws Exception {
        var store = new Store();
        store.setStoreId(987654321L);
        store.setStoreName("Loja de teste");
        store.setStoreEmail("owner@example.com");
        stores.saveAndFlush(store);
        String path = "/backoffice/stores/987654321";
        mvc.perform(post(path + "/email").param("actionToken", "bad")
                .param("subject", "Aviso").param("body", "Olá"))
                .andExpect(redirectedUrl("/backoffice/login"));
        var session = new MockHttpSession();
        session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
        mvc.perform(get(path).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("owner@example.com")))
                .andExpect(content().string(containsString("name=\"subject\"")))
                .andExpect(model().attributeExists("storeEmailToken"));
        mvc.perform(post(path + "/email").session(session).param("actionToken", "bad")
                .param("subject", "Aviso").param("body", "Olá")).andExpect(status().isForbidden());
        verifyNoInteractions(emails);
        mvc.perform(post(path + "/email").session(session)
                .param("actionToken", BackofficeStoreEmailController.token(session))
                .param("subject", "Aviso").param("body", "Olá")
                .param("recipient", "other@example.com"))
                .andExpect(redirectedUrl(path + "#store-email")).andExpect(flash().attributeExists("message"));
        verify(emails).send(987654321L, "Aviso", "Olá");
    }
}
