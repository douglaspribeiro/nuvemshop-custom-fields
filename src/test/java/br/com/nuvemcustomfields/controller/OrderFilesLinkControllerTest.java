package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderFilesLinkControllerTest {
    final StoreRepository stores=mock(StoreRepository.class);
    final OrderFilesLinkController controller=new OrderFilesLinkController(stores);
    @Test void authenticatedOwnerGoesDirectlyToTheSelectedOrderAndCannotSwitchStores(){
        var session=new MockHttpSession();session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,42L);
        when(stores.findActiveByStoreId(42L)).thenReturn(Optional.of(new Store()));
        assertThat(controller.open(42,300,session)).isEqualTo("redirect:/admin/buyer-uploads?orderId=300");
        assertThatThrownBy(()->controller.open(43,300,session)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void parametersDoNotAuthenticateAndPendingNavigationRequiresTheSameVerifiedStore(){
        var session=new MockHttpSession();
        assertThat(controller.open(42,300,session)).isEqualTo("redirect:/admin/embedded");
        assertThat(session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY)).isNull();
        assertThat(OrderFilesLinkController.afterAuthentication(session,42)).isEqualTo("/admin/buyer-uploads?orderId=300");
        assertThat(OrderFilesLinkController.afterAuthentication(session,42)).isEqualTo("/admin");
        controller.open(42,300,session);
        assertThat(OrderFilesLinkController.afterAuthentication(session,43)).isEqualTo("/admin");
        assertThatThrownBy(()->controller.open(42,-1,session)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void verifiedNexoAndOauthSessionsResumeTheOrderLink(){
        var session=new MockHttpSession();var store=new Store();store.setStoreId(42L);
        when(stores.findActiveByStoreId(42L)).thenReturn(Optional.of(store));
        var nexo=mock(br.com.nuvemcustomfields.service.NexoSessionTokenService.class);
        when(nexo.requireStoreId("verified-token")).thenReturn(42L);
        controller.open(42,300,session);
        var embedded=new AdminEmbeddedController(null,stores,nexo);
        assertThat(embedded.createSession(new AdminEmbeddedController.NexoSessionRequest("verified-token"),session).getBody())
                .containsEntry("redirect","/admin/buyer-uploads?orderId=300");
        session=new MockHttpSession();controller.open(42,300,session);
        var oauth=mock(br.com.nuvemcustomfields.service.NuvemshopAuthService.class);
        when(oauth.exchangeCodeAndUpsertStore("verified-code")).thenReturn(store);
        assertThat(new AuthController(oauth).callback("verified-code",null,session).getUrl()).isEqualTo("/admin/buyer-uploads?orderId=300");
    }
}
