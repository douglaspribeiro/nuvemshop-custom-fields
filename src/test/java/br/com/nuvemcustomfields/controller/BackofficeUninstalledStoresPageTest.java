package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.entity.StoreOrderSales;
import br.com.nuvemcustomfields.entity.StoreOrderSalesId;
import br.com.nuvemcustomfields.entity.StoreSalesSync;
import br.com.nuvemcustomfields.repository.StoreOrderSalesRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.repository.StoreSalesSyncRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BackofficeUninstalledStoresPageTest {

    private static final long ACTIVE_ID = 998877661L;
    private static final long UNINSTALLED_ID = 998877662L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StoreRepository stores;

    @Autowired
    private StoreOrderSalesRepository orderSales;

    @Autowired
    private StoreSalesSyncRepository syncStates;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        cleanUp();
        Store active = new Store();
        active.setStoreId(ACTIVE_ID);
        active.setStoreName("Teste Menu Ativa");
        active.setStoreCurrency("BRL");
        stores.save(active);

        Store uninstalled = new Store();
        uninstalled.setStoreId(UNINSTALLED_ID);
        uninstalled.setStoreName("Teste Menu Desinstalada");
        uninstalled.setUninstalledAt(Instant.parse("2026-09-28T12:00:00Z"));
        stores.save(uninstalled);

        session = new MockHttpSession();
        session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
    }

    @AfterEach
    void cleanUp() {
        orderSales.deleteById(new StoreOrderSalesId(ACTIVE_ID, 101L));
        orderSales.deleteById(new StoreOrderSalesId(ACTIVE_ID, 103L));
        orderSales.deleteById(new StoreOrderSalesId(UNINSTALLED_ID, 102L));
        syncStates.deleteById(ACTIVE_ID);
        syncStates.deleteById(UNINSTALLED_ID);
        stores.findByStoreId(ACTIVE_ID).ifPresent(stores::delete);
        stores.findByStoreId(UNINSTALLED_ID).ifPresent(stores::delete);
    }

    @Test
    void menuShowsOnlyUninstalledStoresAndKeepsFilterWhenSearching() throws Exception {
        mockMvc.perform(get("/backoffice/stores")
                        .param("status", "uninstalled")
                        .param("q", "Teste Menu")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Teste Menu Desinstalada")))
                .andExpect(content().string(not(containsString("Teste Menu Ativa"))))
                .andExpect(content().string(containsString("name=\"status\" value=\"uninstalled\"")))
                .andExpect(content().string(containsString("28/09/2026")));
    }

    @Test
    void menuRequiresBackofficeSession() throws Exception {
        mockMvc.perform(get("/backoffice/stores").param("status", "uninstalled"))
                .andExpect(redirectedUrl("/backoffice/login"));
    }

    @Test
    void backofficeDashboardShowsSalesSynchronizationCoverage() throws Exception {
        mockMvc.perform(get("/backoffice").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Itens vendidos")))
                .andExpect(content().string(containsString("Itens personalizados vendidos")))
                .andExpect(content().string(containsString("Os totais exibidos são parciais")));
    }

    @Test
    void dashboardSumsOnlyCompletedActiveStores() throws Exception {
        orderSales.save(new StoreOrderSales(ACTIVE_ID, 101L, 5, 3));
        orderSales.save(new StoreOrderSales(UNINSTALLED_ID, 102L, 20, 10));
        StoreSalesSync active = new StoreSalesSync(ACTIVE_ID);
        active.setComplete(true);
        syncStates.save(active);
        StoreSalesSync uninstalled = new StoreSalesSync(UNINSTALLED_ID);
        uninstalled.setComplete(true);
        syncStates.save(uninstalled);

        mockMvc.perform(get("/backoffice").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<strong>5</strong>")))
                .andExpect(content().string(containsString("<strong>3</strong>")));
    }

    @Test
    void salesPageShowsStoreAndProductValueOnlyForActiveSyncedOrders() throws Exception {
        orderSales.save(new StoreOrderSales(ACTIVE_ID, 101L, 3, 2,
                Instant.parse("2026-09-28T12:00:00Z"), new BigDecimal("37.50")));
        orderSales.save(new StoreOrderSales(ACTIVE_ID, 103L, 4, 0,
                Instant.parse("2026-09-29T12:00:00Z"), new BigDecimal("99.00")));
        orderSales.save(new StoreOrderSales(UNINSTALLED_ID, 102L, 4, 0,
                Instant.parse("2026-09-27T12:00:00Z"), new BigDecimal("99.00")));
        StoreSalesSync active = new StoreSalesSync(ACTIVE_ID);
        active.setComplete(true);
        active.setPersonalizedValueBackfilled(true);
        syncStates.save(active);
        StoreSalesSync uninstalled = new StoreSalesSync(UNINSTALLED_ID);
        uninstalled.setComplete(true);
        syncStates.save(uninstalled);

        mockMvc.perform(get("/backoffice").session(session))
                .andExpect(content().string(containsString("href=\"/backoffice/sales\"")));
        mockMvc.perform(get("/backoffice/sales").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Teste Menu Ativa")))
                .andExpect(content().string(containsString("BRL")))
                .andExpect(content().string(containsString("37,50")))
                .andExpect(content().string(not(containsString("#103"))))
                .andExpect(content().string(not(containsString("99,00"))))
                .andExpect(content().string(not(containsString("Teste Menu Desinstalada"))));
        mockMvc.perform(get("/backoffice/sales"))
                .andExpect(redirectedUrl("/backoffice/login"));
    }
}
