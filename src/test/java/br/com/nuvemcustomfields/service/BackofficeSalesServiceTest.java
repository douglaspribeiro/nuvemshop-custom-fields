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
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

class BackofficeSalesServiceTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void countsPaidItemQuantitiesAndOnlyCustomizedLineItems() throws Exception {
        var order = json.readTree("""
                {"id": 91, "created_at": "2026-09-29T10:30:00-03:00", "currency": "BRL", "payment_status": "paid", "status": "open", "products": [
                  {"quantity": 3, "price": "12.50", "properties": [{"name": "Nome", "value": "Ana"}]},
                  {"quantity": 2, "price": "7.00", "properties": []}
                ]}
                """);

        StoreOrderSales result = BackofficeSalesService.summarizeOrder(123L, order);

        assertThat(result.getTotalItems()).isEqualTo(5);
        assertThat(result.getPersonalizedItems()).isEqualTo(3);
        assertThat(result.getPersonalizedProductValue()).isEqualByComparingTo("37.50");
        assertThat(result.getCurrency()).isEqualTo("BRL");
        assertThat(result.getCreatedAt()).isEqualTo(java.time.Instant.parse("2026-09-29T13:30:00Z"));
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
    void leavesPersonalizedValueUnknownWhenApiOmitsItsPrice() throws Exception {
        var order = json.readTree("""
                {"id": 91, "payment_status": "paid", "products": [{"quantity": 2, "properties": [{"name": "Nome"}]}]}
                """);

        assertThat(BackofficeSalesService.summarizeOrder(123L, order).getPersonalizedProductValue()).isNull();
    }

    @Test
    void ignoresMissingPriceOnUnpersonalizedProducts() throws Exception {
        var order = json.readTree("""
                {"id": 91, "payment_status": "paid", "products": [
                  {"quantity": 2, "properties": [{"name": "Nome"}], "price": "12.50"},
                  {"quantity": 1, "properties": []}
                ]}
                """);

        assertThat(BackofficeSalesService.summarizeOrder(123L, order).getPersonalizedProductValue())
                .isEqualByComparingTo("25.00");
    }

    @Test
    void acceptsNuvemshopOrderTimestampWithoutOffsetColon() throws Exception {
        var order = json.readTree("""
                {"id": 91, "created_at": "2022-11-15T19:36:59+0000", "payment_status": "paid",
                 "products": [{"quantity": "1", "price": "12.50"}]}
                """);

        assertThat(BackofficeSalesService.summarizeOrder(123L, order).getCreatedAt())
                .isEqualTo(Instant.parse("2022-11-15T19:36:59Z"));
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
        assertThat(state.getValue().isPersonalizedValueBackfilled()).isTrue();
        assertThat(state.getValue().getLastSyncedAt()).isNotNull();
    }

    @Test
    void revisitsHistoryForStoresSyncedBeforeProductValuesWereAdded() throws Exception {
        StoreRepository stores = mock(StoreRepository.class);
        StoreOrderSalesRepository sales = mock(StoreOrderSalesRepository.class);
        StoreSalesSyncRepository states = mock(StoreSalesSyncRepository.class);
        NuvemshopApiClient api = mock(NuvemshopApiClient.class);
        Store store = new Store();
        store.setStoreId(123L);
        store.setAccessToken("token");
        store.setScope("read_orders");
        StoreSalesSync existing = new StoreSalesSync(123L);
        existing.setComplete(true);
        existing.setLastSyncedAt(Instant.parse("2026-09-28T10:00:00Z"));
        when(stores.findAll()).thenReturn(List.of(store));
        when(states.findAll()).thenReturn(List.of(existing));
        when(api.listOrdersForSales(any(), anyInt(), anyInt(), any(), any(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(json.readTree("[]"));

        new BackofficeSalesService(stores, sales, states, api).syncOnePendingStore();

        verify(api, atLeastOnce()).listOrdersForSales(any(), anyInt(), anyInt(), any(), any(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
        assertThat(existing.isPersonalizedValueBackfilled()).isTrue();
    }

    @Test
    void retainsOnlyOrdersWithPersonalizedItems() throws Exception {
        StoreRepository stores = mock(StoreRepository.class);
        StoreOrderSalesRepository sales = mock(StoreOrderSalesRepository.class);
        StoreSalesSyncRepository states = mock(StoreSalesSyncRepository.class);
        NuvemshopApiClient api = mock(NuvemshopApiClient.class);
        Store store = new Store();
        store.setStoreId(123L);
        store.setAccessToken("token");
        store.setScope("read_orders");
        when(stores.findAll()).thenReturn(List.of(store));
        when(states.findAll()).thenReturn(List.of());
        when(api.listOrdersForSales(any(), anyInt(), anyInt(), any(), any(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(json.readTree("""
                        [
                          {"id": 1, "payment_status": "paid", "products": [
                            {"quantity": 1, "price": "10.00", "properties": [{"name": "Texto"}]}]},
                          {"id": 2, "payment_status": "paid", "products": [
                            {"quantity": 1, "price": "20.00", "properties": []}]}
                        ]
                        """));

        new BackofficeSalesService(stores, sales, states, api).syncOnePendingStore();

        verify(sales, atLeastOnce()).saveAll(org.mockito.ArgumentMatchers.argThat(rows -> {
            for (StoreOrderSales row : rows) {
                if (row.getId().getOrderId() != 1L) return false;
            }
            return true;
        }));
        verify(sales, atLeastOnce()).deleteAllByIdInBatch(org.mockito.ArgumentMatchers.argThat(ids -> {
            for (var id : ids) {
                if (id.getOrderId() == 2L) return true;
            }
            return false;
        }));
    }
}
