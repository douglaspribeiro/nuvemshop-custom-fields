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
    private final br.com.nuvemcustomfields.repository.PersonalizationFieldRepository fields =
            mock(br.com.nuvemcustomfields.repository.PersonalizationFieldRepository.class);

    private static List<br.com.nuvemcustomfields.entity.PersonalizationField> configuredFields() {
        var rule = new br.com.nuvemcustomfields.entity.PersonalizationRule();
        rule.setProductId(100L);
        return java.util.stream.Stream.of("Nome", "Texto").map(label -> {
            var field = new br.com.nuvemcustomfields.entity.PersonalizationField();
            field.setRule(rule);
            field.setLabel(label);
            return field;
        }).toList();
    }

    @org.junit.jupiter.api.BeforeEach
    void configureFields() {
        when(fields.findWithRuleByStoreId(123L)).thenReturn(configuredFields());
    }

    private StoreOrderSales summarize(com.fasterxml.jackson.databind.JsonNode order) {
        return BackofficeSalesService.summarizeOrder(123L, order, new OrderPersonalization(configuredFields()));
    }

    @Test
    void shutdownDrainsWorkerWithoutFinishingHistoricalImport() throws Exception {
        var stores = mock(StoreRepository.class);
        var sales = mock(StoreOrderSalesRepository.class);
        var states = mock(StoreSalesSyncRepository.class);
        var api = mock(NuvemshopApiClient.class);
        var store = new Store();
        store.setStoreId(123L);
        store.setAccessToken("token");
        store.setScope("read_orders");
        when(stores.findAll()).thenReturn(List.of(store));
        when(states.findAll()).thenReturn(List.of());
        var lifecycle = new br.com.nuvemcustomfields.config.DeploymentLifecycleConfiguration();
        var service = new BackofficeSalesService(stores, sales, states, api, fields);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "backgroundExecutor",
                lifecycle.deploymentBackgroundExecutor());
        var requested = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        when(api.listOrdersForSales(any(), anyInt(), anyInt(), any(), any(), any(), any()))
                .thenAnswer(call -> {
                    requested.countDown();
                    if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("request not released");
                    return json.readTree("[]");
                });
        try {
            service.scheduleSync();
            assertThat(requested.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            service.stopSync();
            release.countDown();
            lifecycle.shutdown();
            assertThat(lifecycle.deploymentBackgroundExecutor().isTerminated()).isTrue();
            verify(api, org.mockito.Mockito.times(1)).listOrdersForSales(any(), anyInt(), anyInt(), any(), any(), any(), any());
            verify(states, org.mockito.Mockito.times(1)).save(any());
        } finally {
            release.countDown();
            lifecycle.deploymentBackgroundExecutor().shutdownNow();
        }
    }

    @Test
    void shutdownDuringRequestPausesWithoutMarkingCompleteOrRecordingFailure() throws Exception {
        var stores = mock(StoreRepository.class);
        var sales = mock(StoreOrderSalesRepository.class);
        var states = mock(StoreSalesSyncRepository.class);
        var api = mock(NuvemshopApiClient.class);
        var store = new Store();
        store.setStoreId(123L);
        store.setAccessToken("token");
        store.setScope("read_orders");
        var state = new StoreSalesSync(123L);
        when(stores.findAll()).thenReturn(List.of(store));
        when(states.findAll()).thenReturn(List.of(state));
        var service = new BackofficeSalesService(stores, sales, states, api, fields);
        when(api.listOrdersForSales(any(), anyInt(), anyInt(), any(), any(), any(), any()))
                .thenAnswer(call -> {
                    service.stopSync();
                    return json.readTree("[]");
                });

        service.syncOnePendingStore();
        service.scheduleSync(); // Must not submit any work after the shutdown signal.

        verify(api, org.mockito.Mockito.times(1)).listOrdersForSales(any(), anyInt(), anyInt(), any(), any(), any(), any());
        verify(states, org.mockito.Mockito.times(1)).save(state);
        org.mockito.Mockito.verifyNoInteractions(sales);
        assertThat(state.isComplete()).isFalse();
        assertThat(state.isPersonalizedValueBackfilled()).isFalse();
        assertThat(state.getLastSyncedAt()).isNull();
        assertThat(state.getLastError()).isNull();
    }

    @Test
    void countsPaidItemQuantitiesAndOnlyCustomizedLineItems() throws Exception {
        var order = json.readTree("""
                {"id": 91, "created_at": "2026-09-29T10:30:00-03:00", "currency": "BRL", "payment_status": "paid", "status": "open", "products": [
                  {"product_id":100,"quantity": 3, "price": "12.50", "properties": [{"name": "Nome", "value": "Ana"}]},
                  {"product_id":100,"quantity": 2, "price": "7.00", "properties": []}
                ]}
                """);

        StoreOrderSales result = summarize(order);

        assertThat(result.getTotalItems()).isEqualTo(5);
        assertThat(result.getPersonalizedItems()).isEqualTo(3);
        assertThat(result.getPersonalizedProductValue()).isEqualByComparingTo("37.50");
        assertThat(result.getCurrency()).isEqualTo("BRL");
        assertThat(result.getCreatedAt()).isEqualTo(java.time.Instant.parse("2026-09-29T13:30:00Z"));
        assertThat(result.getId().getStoreId()).isEqualTo(123L);
        assertThat(result.getId().getOrderId()).isEqualTo(91L);
    }

    @Test
    void rejectsGiftPropertiesAndCountsOnlyNonemptyConfiguredValues() throws Exception {
        var order = json.readTree("""
                {"id":92,"payment_status":"paid","products":[
                  {"product_id":100,"quantity":2,"price":"10","properties":{"Nome":"Ana","gift":"25533"}},
                  {"product_id":100,"quantity":3,"price":"20","properties":{"gift":"25533","Nome":" "}},
                  {"product_id":101,"quantity":4,"price":"30","properties":{"Nome":"Outro app"}}
                ]}
                """);
        var result = summarize(order);
        assertThat(result.getTotalItems()).isEqualTo(9);
        assertThat(result.getPersonalizedItems()).isEqualTo(2);
        assertThat(result.getPersonalizedProductValue()).isEqualByComparingTo("20.00");
    }

    @Test
    void ignoresUnpaidAndCancelledOrders() throws Exception {
        var unpaid = json.readTree("""
                {"id": 1, "payment_status": "pending", "products": [{"product_id":100,"quantity": 2, "properties": [{"name": "Nome", "value":"Ana"}]}]}
                """);
        var cancelled = json.readTree("""
                {"id": 2, "payment_status": "paid", "status": "cancelled", "products": [{"product_id":100,"quantity": 4}]}
                """);

        assertThat(summarize(unpaid).getTotalItems()).isZero();
        assertThat(summarize(cancelled).getTotalItems()).isZero();
    }

    @Test
    void leavesPersonalizedValueUnknownWhenApiOmitsItsPrice() throws Exception {
        var order = json.readTree("""
                {"id": 91, "payment_status": "paid", "products": [{"product_id":100,"quantity": 2, "properties": [{"name": "Nome", "value":"Ana"}]}]}
                """);

        assertThat(summarize(order).getPersonalizedProductValue()).isNull();
    }

    @Test
    void ignoresMissingPriceOnUnpersonalizedProducts() throws Exception {
        var order = json.readTree("""
                {"id": 91, "payment_status": "paid", "products": [
                  {"product_id":100,"quantity": 2, "properties": [{"name": "Nome", "value":"Ana"}], "price": "12.50"},
                  {"product_id":100,"quantity": 1, "properties": []}
                ]}
                """);

        assertThat(summarize(order).getPersonalizedProductValue())
                .isEqualByComparingTo("25.00");
    }

    @Test
    void acceptsNuvemshopOrderTimestampWithoutOffsetColon() throws Exception {
        var order = json.readTree("""
                {"id": 91, "created_at": "2022-11-15T19:36:59+0000", "payment_status": "paid",
                 "products": [{"product_id":100,"quantity": "1", "price": "12.50"}]}
                """);

        assertThat(summarize(order).getCreatedAt())
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

        new BackofficeSalesService(stores, sales, states, api, fields).syncOnePendingStore();

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

        new BackofficeSalesService(stores, sales, states, api, fields).syncOnePendingStore();

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
                            {"product_id":100,"quantity": 1, "price": "10.00", "properties": [{"name": "Texto", "value":"Ana"}]}]},
                          {"id": 2, "payment_status": "paid", "products": [
                            {"product_id":100,"quantity": 1, "price": "20.00", "properties": {"gift":"25533"}}]}
                        ]
                        """));

        new BackofficeSalesService(stores, sales, states, api, fields).syncOnePendingStore();

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
