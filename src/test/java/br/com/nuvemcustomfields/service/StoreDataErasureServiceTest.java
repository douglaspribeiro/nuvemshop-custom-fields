package br.com.nuvemcustomfields.service;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoreDataErasureServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final StoreDataErasureService service = new StoreDataErasureService(jdbcTemplate);

    @Test
    void deletesEveryStoreOwnedRecordInForeignKeySafeOrder() {
        when(jdbcTemplate.update("delete from stores where store_id = ?", 123L)).thenReturn(1);

        service.erase(123L);

        InOrder deletion = inOrder(jdbcTemplate);
        deletion.verify(jdbcTemplate).update(
                "delete from support_messages where ticket_id in (select id from support_tickets where store_id = ?)",
                123L
        );
        deletion.verify(jdbcTemplate).update("delete from support_tickets where store_id = ?", 123L);
        deletion.verify(jdbcTemplate).update(
                "delete from personalization_fields where rule_id in (select id from personalization_rules where store_id = ?)",
                123L
        );
        deletion.verify(jdbcTemplate).update("delete from personalization_rules where store_id = ?", 123L);
        deletion.verify(jdbcTemplate).update("delete from integration_logs where store_id = ?", 123L);
        deletion.verify(jdbcTemplate).update("delete from plan_events where store_id = ?", 123L);
        deletion.verify(jdbcTemplate).update("delete from stores where store_id = ?", 123L);
    }

    @Test
    void remainsIdempotentWhenStoreWasAlreadyErased() {
        service.erase(123L);
        service.erase(123L);
    }
}
