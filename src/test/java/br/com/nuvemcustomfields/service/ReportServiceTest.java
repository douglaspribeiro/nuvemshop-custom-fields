package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReportServiceTest {
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
