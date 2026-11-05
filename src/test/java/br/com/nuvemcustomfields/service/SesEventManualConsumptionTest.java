package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.SesEventsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SesEventManualConsumptionTest {
    final SqsClient sqs=mock(SqsClient.class);
    final WinbackSesEventService winback=mock(WinbackSesEventService.class);
    final ActivationEmailSesEventService activation=mock(ActivationEmailSesEventService.class);
    final SesEventConsumerMetrics metrics=mock(SesEventConsumerMetrics.class);
    @SuppressWarnings("unchecked")
    SesEventConsumer consumer(boolean enabled,String url) {
        ObjectProvider<SqsClient> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(sqs);
        return new SesEventConsumer(new SesEventsProperties(enabled,url,"us-east-2","config"),provider,winback,activation,metrics);
    }
    SesEventConsumer consumer() {return consumer(true,"https://sqs.us-east-2.amazonaws.com/123/email-tracking-events");}
    void emptyQueue() {
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder().build());
        when(sqs.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenReturn(GetQueueAttributesResponse.builder().build());
    }
    @Test void rejectsDisabledAndIncompleteConfigurationBeforeStartingAnyWork() {
        assertThat(consumer(false,"queue").startManual(Runnable::run)).isEqualTo(SesEventConsumer.ManualStart.DISABLED);
        var invalid=consumer(true,"email-tracking-events");
        assertThat(invalid.startManual(Runnable::run)).isEqualTo(SesEventConsumer.ManualStart.MISCONFIGURED);
        assertThat(invalid.configurationProblem()).contains("URL HTTPS completa");verifyNoInteractions(sqs);
    }
    @Test void queuedManualRunPreventsDoubleClickAndScheduledOverlapThenReleasesTheLock() {
        emptyQueue();var consumer=consumer();var queued=new AtomicReference<Runnable>();
        assertThat(consumer.startManual(queued::set)).isEqualTo(SesEventConsumer.ManualStart.STARTED);
        assertThat(consumer.status().running()).isTrue();assertThat(consumer.status().source()).isEqualTo("Manual");
        assertThat(consumer.startManual(queued::set)).isEqualTo(SesEventConsumer.ManualStart.RUNNING);
        consumer.poll();verifyNoInteractions(sqs);
        queued.get().run();
        assertThat(consumer.status().running()).isFalse();assertThat(consumer.status().received()).isZero();
        assertThat(consumer.status().finishedAt()).isNotNull();assertThat(consumer.status().attributesOk()).isTrue();
        verify(sqs).receiveMessage(any(ReceiveMessageRequest.class));verify(sqs).getQueueAttributes(any(GetQueueAttributesRequest.class));
        assertThat(consumer.startManual(Runnable::run)).isEqualTo(SesEventConsumer.ManualStart.STARTED);
    }
    @Test void activeScheduledConsumptionAlsoBlocksManualRequests() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var consumer=consumer();
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(call->{entered.countDown();release.await(5,TimeUnit.SECONDS);return ReceiveMessageResponse.builder().build();});
        try(var worker=Executors.newSingleThreadExecutor()) {
            var run=worker.submit(consumer::poll);
            try {
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                assertThat(consumer.startManual(Runnable::run)).isEqualTo(SesEventConsumer.ManualStart.RUNNING);
            } finally {release.countDown();}
            run.get(5,TimeUnit.SECONDS);
        }
        assertThat(consumer.status().source()).isEqualTo("Automática");assertThat(consumer.status().running()).isFalse();
    }
    @Test void executorShutdownIsReportedAndDoesNotLeaveTheButtonLocked() {
        var consumer=consumer();
        assertThat(consumer.startManual(task->{throw new RejectedExecutionException();})).isEqualTo(SesEventConsumer.ManualStart.UNAVAILABLE);
        assertThat(consumer.status().running()).isFalse();assertThat(consumer.status().error()).contains("encerrando");
        emptyQueue();assertThat(consumer.startManual(Runnable::run)).isEqualTo(SesEventConsumer.ManualStart.STARTED);
    }
    @Test void reportsAcknowledgementFailureWithoutPretendingMessagesWereRemoved() {
        emptyQueue();var consumer=consumer();
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder().messages(
                Message.builder().body("private-event-body").receiptHandle("private-receipt").build()).build(),ReceiveMessageResponse.builder().build());
        doThrow(new IllegalStateException("private AWS diagnostics")).when(sqs).deleteMessage(any(DeleteMessageRequest.class));
        consumer.startManual(Runnable::run);var run=consumer.status();
        assertThat(run.received()).isEqualTo(1);assertThat(run.processed()).isEqualTo(1);assertThat(run.acknowledged()).isZero();
        assertThat(run.failures()).isEqualTo(1);assertThat(run.toString()).doesNotContain("private");
        verify(metrics).failed(SesEventConsumerMetrics.Failure.ACKNOWLEDGE);
    }
    @Test void receiveAndAttributesFailuresRemainVisibleWithoutLeakingDiagnostics() {
        var consumer=consumer();
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenThrow(new IllegalStateException("private-token"));
        when(sqs.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenThrow(new IllegalStateException("private-token"));
        consumer.startManual(Runnable::run);var run=consumer.status();
        assertThat(run.running()).isFalse();assertThat(run.failures()).isEqualTo(1);assertThat(run.attributesOk()).isFalse();
        assertThat(run.error()).contains("URL, região").doesNotContain("private-token");
        assertThat(run.limited()).isFalse();
    }
}
