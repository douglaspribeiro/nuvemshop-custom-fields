package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.service.NuvemshopAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O revisor da Nuvemshop instala pelo admin da loja (`/apps/{app_id}/authorize`), que nao
 * passa pelo nosso /install: o callback chega sem `state`. Se a validacao inverter a ordem
 * do equals, isso volta a ser NullPointerException e a instalacao cai em tela de erro.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OauthCallbackTest {

    private static final long STORE_ID = 5538394L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NuvemshopAuthService authService;

    @BeforeEach
    void setUp() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        when(authService.exchangeCodeAndUpsertStore(anyString())).thenReturn(store);
    }

    @Test
    void completesInstallWhenCallbackArrivesWithoutStateButSessionHasOne() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("oauthState", "state-gerado-por-install");

        mockMvc.perform(get("/oauth/callback").param("code", "auth-code").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin"));

        verify(authService).exchangeCodeAndUpsertStore("auth-code");
        assertThat(session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY)).isEqualTo(STORE_ID);
    }

    @Test
    void completesInstallWhenSessionHasNoStateAtAll() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/oauth/callback").param("code", "auth-code").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin"));

        verify(authService).exchangeCodeAndUpsertStore("auth-code");
    }

    @Test
    void completesInstallWhenStateMatches() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("oauthState", "abc123");

        mockMvc.perform(get("/oauth/callback")
                        .param("code", "auth-code")
                        .param("state", "abc123")
                        .session(session))
                .andExpect(status().is3xxRedirection());

        verify(authService).exchangeCodeAndUpsertStore("auth-code");
        assertThat(session.getAttribute("oauthState")).as("state deve ser consumido").isNull();
    }

    /** A propriedade que importa: state divergente nunca troca o code por token. */
    @Test
    void neverExchangesCodeWhenStateDiverges() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("oauthState", "abc123");

        try {
            mockMvc.perform(get("/oauth/callback")
                    .param("code", "auth-code")
                    .param("state", "state-de-outra-origem")
                    .session(session));
        } catch (Exception expected) {
            // Rejeicao pode subir como excecao ou virar 500; o que importa e nao trocar o code.
        }

        verify(authService, never()).exchangeCodeAndUpsertStore(anyString());
    }
}
