package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReportServiceTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {401, 403, 404, 500})
    void apiErrorsAreUnavailableInsteadOfZeroOrders(int status) {
        var rules = mock(PersonalizationRuleRepository.class);
        var fields = mock(PersonalizationFieldRepository.class);
        var api = mock(NuvemshopApiClient.class);
        var store = new Store(); store.setStoreId(1L); store.setScope("read_orders");
        when(api.listRecentOrders(store)).thenThrow(new org.springframework.web.client.HttpClientErrorException(
                org.springframework.http.HttpStatusCode.valueOf(status)));
        var summary = new ReportService(rules, fields, api).dashboard(store);
        assertThat(summary.ordersAvailable()).isFalse();
        assertThat(summary.ordersUnavailableReason()).isEqualTo("load_failed");
    }

    @Test void malformedResponseIsUnavailableButEmptyArrayIsKnownEmpty() throws Exception {
        var rules = mock(PersonalizationRuleRepository.class);
        var fields = mock(PersonalizationFieldRepository.class);
        var api = mock(NuvemshopApiClient.class);
        var store = new Store(); store.setStoreId(1L); store.setScope("read_orders");
        var service = new ReportService(rules, fields, api);
        when(api.listRecentOrders(store)).thenReturn(new ObjectMapper().readTree("{}"));
        assertThat(service.dashboard(store).ordersAvailable()).isFalse();
        when(api.listRecentOrders(store)).thenReturn(new ObjectMapper().readTree("[]"));
        assertThat(service.dashboard(store).ordersAvailable()).isTrue();
        assertThat(service.dashboard(store).personalizedOrders()).isEmpty();
    }

    @Test void nullAndMetadataAreNotCustomerTextButMultipleChoicesAndZeroArePreserved() throws Exception {
        var rule=new PersonalizationRule();rule.setProductId(100L);
        var field=new PersonalizationField();field.setRule(rule);field.setLabel("Resposta");
        var matcher=new OrderPersonalization(List.of(field));var json=new ObjectMapper();
        assertThat(matcher.productProperties(json.readTree("{\"product_id\":100,\"properties\":{\"Resposta\":null}}"))).isEmpty();
        assertThat(matcher.productProperties(json.readTree("{\"product_id\":100,\"properties\":{\"Resposta\":{\"id\":123}}}"))).isEmpty();
        assertThat(matcher.productProperties(json.readTree("{\"product_id\":100,\"properties\":{\"Resposta\":[\"Azul\",\"Verde\"]}}")))
                .containsExactly("Resposta: Azul, Verde");
        assertThat(matcher.productProperties(json.readTree("{\"product_id\":100,\"properties\":[{\"name\":\"Resposta\",\"value\":0}]}")))
                .containsExactly("Resposta: 0");
    }

    @Test
    void displaysCustomerTextOnlyForConfiguredProductFields() throws Exception {
        var rules = mock(PersonalizationRuleRepository.class);
        var fields = mock(PersonalizationFieldRepository.class);
        var api = mock(NuvemshopApiClient.class);
        var store = new Store();
        store.setStoreId(1163485L);
        store.setScope("read_orders");
        var rule = new PersonalizationRule();
        rule.setProductId(375508189L);
        var field = new PersonalizationField();
        field.setRule(rule);
        field.setLabel("Nome ou Frase para gravar");
        when(fields.findWithRuleByStoreId(1163485L)).thenReturn(List.of(field));
        when(api.listRecentOrders(store)).thenReturn(new ObjectMapper().readTree("""
            [
              {"number":64800,"products":[{"product_id":373650805,"properties":{"gift":"25533"}}]},
              {"number":64801,"products":[{"product_id":375508189,"properties":{"gift":"25533","Nome ou Frase para gravar":"Ana Maria"}}]},
              {"number":64802,"products":[{"product_id":375508189,"properties":[{"name":"Nome ou Frase para gravar","value":"Feliz aniversário"}]}]},
              {"number":64803,"products":[{"product_id":373650805,"properties":{"Nome ou Frase para gravar":"Outro app"}}]}
            ]
            """));
        var result = new ReportService(rules, fields, api).dashboard(store).personalizedOrders();
        assertThat(result).extracting(o -> o.number()).containsExactly("64801", "64802");
        assertThat(result.get(0).properties()).containsExactly("Nome ou Frase para gravar: Ana Maria");
        assertThat(result.get(1).properties()).containsExactly("Nome ou Frase para gravar: Feliz aniversário");
    }
}
