package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.SesEventsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import static org.mockito.Mockito.*;

class WinbackSesEventConsumerTest {
    @Test
    @SuppressWarnings("unchecked")
    void acknowledgesOnlyAfterProcessingAndKeepsFailedMessages() throws Exception {
        var sqs = mock(SqsClient.class);
        var service = mock(WinbackSesEventService.class);
        ObjectProvider<SqsClient> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(sqs);
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
                .messages(Message.builder().body("valid").receiptHandle("receipt-valid").build(),
                        Message.builder().body("invalid").receiptHandle("receipt-invalid").build()).build());
        doThrow(new IllegalStateException("simulated")).when(service).ingest("invalid");
        var consumer = new WinbackSesEventConsumer(
                new SesEventsProperties(true, "https://queue.test", "us-east-1", "config"), provider, service);
        consumer.poll();
        var order = inOrder(service, sqs);
        order.verify(sqs).receiveMessage(any(ReceiveMessageRequest.class));
        order.verify(service).ingest("valid");
        order.verify(sqs).deleteMessage(argThat((DeleteMessageRequest r) -> "receipt-valid".equals(r.receiptHandle())));
        order.verify(service).ingest("invalid");
        verify(sqs, times(1)).deleteMessage(any(DeleteMessageRequest.class));
    }
}
