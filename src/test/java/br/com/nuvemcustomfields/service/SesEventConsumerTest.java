package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.SesEventsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import static org.mockito.Mockito.*;

class SesEventConsumerTest {
    @Test
    @SuppressWarnings("unchecked")
    void acknowledgesOnlyAfterProcessingAndKeepsFailedMessages() throws Exception {
        var sqs = mock(SqsClient.class);
        var service = mock(WinbackSesEventService.class);
        var activation = mock(ActivationEmailSesEventService.class);
        ObjectProvider<SqsClient> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(sqs);
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
                .messages(Message.builder().body("valid").receiptHandle("receipt-valid").build(),
                        Message.builder().body("invalid").receiptHandle("receipt-invalid").build()).build(), ReceiveMessageResponse.builder().build());
        doThrow(new IllegalStateException("simulated")).when(service).ingest("invalid");
        var consumer = new SesEventConsumer(
                new SesEventsProperties(true, "https://queue.test", "us-east-1", "config"), provider, service, activation, mock(SesEventConsumerMetrics.class));
        consumer.poll();
        var order = inOrder(service, activation, sqs);
        order.verify(sqs).receiveMessage(any(ReceiveMessageRequest.class));
        order.verify(service).ingest("valid");
        order.verify(activation).ingest("valid");
        order.verify(sqs).deleteMessage(argThat((DeleteMessageRequest r) -> "receipt-valid".equals(r.receiptHandle())));
        order.verify(service).ingest("invalid");
        verify(sqs, times(1)).deleteMessage(any(DeleteMessageRequest.class));
    }
    @Test
    @SuppressWarnings("unchecked")
    void activationFailurePreventsAcknowledgementAndOtherMessagesContinue() throws Exception {
        var sqs=mock(SqsClient.class);var winback=mock(WinbackSesEventService.class);
        var activation=mock(ActivationEmailSesEventService.class);
        ObjectProvider<SqsClient> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(sqs);
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder().messages(
                Message.builder().body("failed-nc39").receiptHandle("failed").build(),
                Message.builder().body("good").receiptHandle("good").build()).build(), ReceiveMessageResponse.builder().build());
        doThrow(new IllegalStateException("simulated")).when(activation).ingest("failed-nc39");
        new SesEventConsumer(new SesEventsProperties(true,"queue","us-east-1","config"),provider,winback,activation,mock(SesEventConsumerMetrics.class)).poll();
        verify(activation).ingest("failed-nc39");verify(activation).ingest("good");
        verify(sqs,times(1)).deleteMessage(argThat((DeleteMessageRequest r)->"good".equals(r.receiptHandle())));
    }

}
