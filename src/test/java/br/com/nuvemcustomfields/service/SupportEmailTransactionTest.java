package br.com.nuvemcustomfields.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.Mockito.*;

@SpringBootTest
class SupportEmailTransactionTest {
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean SupportEmailNotifier notifier;

    @Test
    void notificationWaitsForCommit() {
        var event = new SupportReplyEvent(10L, "store@example.com", "Ajuda", "Resposta");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            events.publishEvent(event);
            verifyNoInteractions(notifier);
        });
        verify(notifier).onReply(event);
    }

    @Test
    void rollbackDoesNotNotify() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            events.publishEvent(new SupportReplyEvent(10L, "store@example.com", "Ajuda", "Resposta"));
            status.setRollbackOnly();
        });
        verifyNoInteractions(notifier);
    }
}
