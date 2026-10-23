package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DlocalGoOperationsRoutingTest {
    @Test void quoteUsesCurrentSessionStoreAndDedicatedView() throws Exception {
        var upgrades=mock(DlocalGoUpgradeService.class);var refunds=mock(DlocalGoRefundService.class);var stores=mock(AdminStoreService.class);
        var store=new Store();store.setStoreId(42L);when(stores.requireCurrentStore(any())).thenReturn(store);
        var op=new DlocalGoOperation();when(upgrades.quote(42L,PlanType.PREMIUM_PLUS)).thenReturn(op);
        var mvc=MockMvcBuilders.standaloneSetup(new DlocalGoOperationsController(upgrades,refunds,stores)).build();
        mvc.perform(get("/admin/billing/upgrade/dlocal-go").param("plan","PREMIUM_PLUS"))
                .andExpect(status().isOk()).andExpect(view().name("admin/billing-upgrade-dlocal-go")).andExpect(model().attribute("operation",op));
        verifyNoInteractions(refunds);
    }
    @Test void invalidProviderCannotStartAHostedUpgrade() throws Exception {
        var upgrades=mock(DlocalGoUpgradeService.class);var refunds=mock(DlocalGoRefundService.class);var stores=mock(AdminStoreService.class);
        var store=new Store();store.setStoreId(42L);when(stores.requireCurrentStore(any())).thenReturn(store);
        when(upgrades.quote(42L,PlanType.PREMIUM_PLUS)).thenThrow(new IllegalArgumentException("Assinatura indisponível"));
        var mvc=MockMvcBuilders.standaloneSetup(new DlocalGoOperationsController(upgrades,refunds,stores)).build();
        mvc.perform(get("/admin/billing/upgrade/dlocal-go").param("plan","PREMIUM_PLUS"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/billing"));
        verify(upgrades,never()).pay(any(),any());verifyNoInteractions(refunds);
    }
}
