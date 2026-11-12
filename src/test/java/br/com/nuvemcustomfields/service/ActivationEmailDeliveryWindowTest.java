package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.ActivationEmailOutbox;
import br.com.nuvemcustomfields.repository.ActivationEmailOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="app.background.enabled=false")
@Transactional
class ActivationEmailDeliveryWindowTest {
    @Autowired ActivationEmailOutboxRepository outbox;
    @Test void deliveryCountsOnlyConfirmedSendsWithinFourteenDaysIncludingTheBoundary() {
        var end=Instant.parse("2026-11-08T14:00:00Z");var since=end.minus(Duration.ofDays(14));
        int id=0;
        for(var at:java.util.List.of(since.minusSeconds(1),since,end.minusSeconds(10),end.plusSeconds(1))) {
            var row=new ActivationEmailOutbox(999100L+(id++),end.minus(Duration.ofDays(30)),"D11",end,"test");row.sent(at);outbox.saveAndFlush(row);
        }
        var rows=outbox.countsByDeliveryOutcome(since,end);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()[0]).isEqualTo("awaiting");
        assertThat(((Number)rows.getFirst()[1]).longValue()).isEqualTo(2);
    }
}
