package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.entity.StoreOrderSales;
import br.com.nuvemcustomfields.entity.StoreSalesSync;
import br.com.nuvemcustomfields.repository.StoreOrderSalesRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.repository.StoreSalesSyncRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackofficeSalesServiceTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void countsPaidItemQuantitiesAndOnlyCustomizedLineItems() throws Exception {
        var order = json.readTree("""
                {"id": 91, "payment_status": "paid", "status": "open", "products": [
                  {"quantity": 3, "properties": [{"name": "Nome", "value": "Ana"}]},
                  {"quantity": 2, "properties": []}
                ]}
                """);

        StoreOrderSales result = BackofficeSalesService.summarizeOrder(123L, order);

        assertThat(result.getTotalItems()).isEqualTo(5);
        assertThat(result.getPersonalizedItems()).isEqualTo(3);
        assertThat(result.getId().getStoreId()).isEqualTo(123L);
        assertThat(result.getId().getOrderId()).isEqualTo(91L);
    }

    @Test
    void ignoresUnpaidAndCancelledOrders() throws Exception {
        var unpaid = json.readTree("""
                {"id": 1, "payment_status": "pending", "products": [{"quantity": 2, "properties": [{"name": "Nome"}]}]}
                """);
        var cancelled = json.readTree("""
                {"id": 2, "payment_status": "paid", "status": "cancelled", "products": [{"quantity": 4}]}
                """);

        assertThat(BackofficeSalesService.summarizeOrder(123L, unpaid).getTotalItems()).isZero();
        assertThat(BackofficeSalesService.summarizeOrder(123L, cancelled).getTotalItems()).isZero();
    }

    @Test
    void importsHistoryAndMarksStoreCompleteOnlyAfterAllRanges() throws Exception {
        StoreRepository stores = mock(StoreRepository.class);
        StoreOrderSalesRepository sales = mock(StoreOrderSalesRepository.class);
        StoreSalesSyncRepository states = mock(StoreSalesSyncRepository.class);
        NuvemshopApiClient api = mock(NuvemshopApiClient.class);
        Store store = new Store();
        store.setStoreId(123L);
        store.setAccessToken("token");
        store.setScope("read_products,read_orders");
        when(stores.findAll()).thenReturn(List.of(store));
        when(states.findAll()).thenReturn(List.of());
        when(api.listOrdersForSales(any(), anyInt(), anyInt(), any(), any(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(json.readTree("[]"));

        new BackofficeSalesService(stores, sales, states, api).syncOnePendingStore();

        ArgumentCaptor<StoreSalesSync> state = ArgumentCaptor.forClass(StoreSalesSync.class);
        verify(states, org.mockito.Mockito.atLeastOnce()).save(state.capture());
        assertThat(state.getValue().isComplete()).isTrue();
        assertThat(state.getValue().getLastSyncedAt()).isNotNull();
    }
}
