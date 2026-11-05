package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.SesEventsProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SesEventConsumerMetricsTest {
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final SesEventsProperties properties = new SesEventsProperties(true,"https://sqs.example.com/email-tracking-events","us-east-1","config");
    final SesEventConsumerMetrics metrics = new SesEventConsumerMetrics(registry,properties);
    final SqsClient sqs = mock(SqsClient.class);
    final WinbackSesEventService winback = mock(WinbackSesEventService.class);
    final ActivationEmailSesEventService activation = mock(ActivationEmailSesEventService.class);
    @SuppressWarnings("unchecked")
    SesEventConsumer consumer(SesEventsProperties p) {
        ObjectProvider<SqsClient> provider = mock(ObjectProvider.class); when(provider.getObject()).thenReturn(sqs);
        return new SesEventConsumer(p,provider,winback,activation,metrics,event -> {});
    }
    void queueMessages(String... bodies) {
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder().messages(
                java.util.Arrays.stream(bodies).map(s->Message.builder().body(s).receiptHandle(s).build()).toList()).build(), ReceiveMessageResponse.builder().build());
    }
    double messages(String result) { return registry.get("ncf.ses.events.messages").tag("result",result).counter().count(); }
    double failures(String stage) { return registry.get("ncf.ses.events.failures").tag("stage",stage).counter().count(); }
    double gauge(String name) { return registry.get("ncf.ses.events."+name).gauge().value(); }
    @Test void countsQueueReceiptProcessingAndAcknowledgementSeparately() throws Exception {
        queueMessages("good","bad");doThrow(new IllegalStateException()).when(activation).ingest("bad");
        consumer(properties).poll();
        assertThat(messages("received")).isEqualTo(2);assertThat(messages("processed")).isEqualTo(1);
        assertThat(messages("acknowledged")).isEqualTo(1);assertThat(failures("process")).isEqualTo(1);
        assertThat(gauge("last.poll.timestamp.seconds")).isPositive();assertThat(gauge("last.ack.timestamp.seconds")).isPositive();
    }
    @Test void failedAcknowledgementIsVisibleAndDoesNotCountAsConsumptionOrStopNextMessage() {
        queueMessages("bad-ack","good");
        doThrow(new IllegalStateException()).when(sqs).deleteMessage(argThat((DeleteMessageRequest r)->"bad-ack".equals(r.receiptHandle())));
        consumer(properties).poll();
        assertThat(messages("received")).isEqualTo(2);assertThat(messages("processed")).isEqualTo(2);
        assertThat(messages("acknowledged")).isEqualTo(1);assertThat(failures("acknowledge")).isEqualTo(1);
        assertThat(failures("process")).isZero();
    }
    @Test void emptySuccessfulPollIsHealthyWithoutInventingAcknowledgedMessages() {
        queueMessages(new String[]{});consumer(properties).poll();
        assertThat(gauge("last.poll.timestamp.seconds")).isPositive();assertThat(gauge("last.ack.timestamp.seconds")).isZero();
        assertThat(messages("received")).isZero();assertThat(messages("acknowledged")).isZero();
        assertThat(registry.get("ncf.ses.events.polls").counter().count()).isEqualTo(1);
    }
    @Test void receiveFailureDoesNotPretendTheQueueIsEmptyOrHealthy() {
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenThrow(new IllegalStateException());consumer(properties).poll();
        assertThat(failures("receive")).isEqualTo(1);assertThat(gauge("last.poll.timestamp.seconds")).isZero();
        assertThat(registry.get("ncf.ses.events.queue.depth").tag("state","visible").gauge().value()).isNaN();
    }
    @Test void disabledConsumerMakesNoAwsCallsAndExposesItsDisabledState() {
        var disabled = new SesEventsProperties(false,"queue","us-east-1","config");
        var registry = new SimpleMeterRegistry();new SesEventConsumerMetrics(registry,disabled);
        consumer(disabled).poll();consumer(disabled).sampleQueue();verifyNoInteractions(sqs,winback,activation);
        assertThat(registry.get("ncf.ses.events.consumer.enabled").gauge().value()).isZero();
    }
    @Test void samplesApproximateBacklogAndDistinguishesRealZeroFromUnknown() {
        when(sqs.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenReturn(GetQueueAttributesResponse.builder().attributes(Map.of(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,"12",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,"2",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED,"0")).build());
        consumer(properties).sampleQueue();
        assertThat(registry.get("ncf.ses.events.queue.depth").tag("state","visible").gauge().value()).isEqualTo(12);
        assertThat(registry.get("ncf.ses.events.queue.depth").tag("state","in_flight").gauge().value()).isEqualTo(2);
        assertThat(registry.get("ncf.ses.events.queue.depth").tag("state","delayed").gauge().value()).isZero();
        assertThat(gauge("last.attributes.timestamp.seconds")).isPositive();
    }
    @Test void deniedAttributesPermissionDoesNotStopConsumptionOrDisplayStaleDepthAsCurrent() {
        metrics.attributes(Map.of(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,"3",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,"1",
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED,"0"));
        when(sqs.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenThrow(new IllegalStateException());
        var consumer=consumer(properties);consumer.sampleQueue();queueMessages("good");consumer.poll();
        assertThat(failures("attributes")).isEqualTo(1);assertThat(messages("acknowledged")).isEqualTo(1);
        assertThat(registry.get("ncf.ses.events.queue.depth").tag("state","visible").gauge().value()).isNaN();
    }
    @Test void missingAttributeIsUnknownAndNeverRenderedAsZeroBacklog() {
        when(sqs.getQueueAttributes(any(GetQueueAttributesRequest.class))).thenReturn(GetQueueAttributesResponse.builder().attributes(Map.of()).build());
        consumer(properties).sampleQueue();assertThat(failures("attributes")).isEqualTo(1);
        assertThat(gauge("last.attributes.timestamp.seconds")).isZero();
        assertThat(registry.get("ncf.ses.events.queue.depth").tag("state","visible").gauge().value()).isNaN();
    }
    @Test void hourlyExecutionDrainsMultipleBatchesAndStopsWhenQueueReturnsEmpty() {
        var batch=ReceiveMessageResponse.builder().messages(java.util.stream.IntStream.range(0,10)
                .mapToObj(i->Message.builder().body("body-"+i).receiptHandle("handle-"+i).build()).toList()).build();
        var partial=ReceiveMessageResponse.builder().messages(Message.builder().body("last").receiptHandle("last").build()).build();
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(batch,partial,ReceiveMessageResponse.builder().build());
        consumer(properties).poll();assertThat(messages("received")).isEqualTo(11);assertThat(messages("acknowledged")).isEqualTo(11);
        verify(sqs,times(3)).receiveMessage(any(ReceiveMessageRequest.class));
    }
    @Test void largeBacklogIsBoundedToOneThousandDeliveriesPerHourlyExecution() {
        var batch=ReceiveMessageResponse.builder().messages(java.util.stream.IntStream.range(0,10)
                .mapToObj(i->Message.builder().body("body-"+i).receiptHandle("handle-"+i).build()).toList()).build();
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(batch);
        consumer(properties).poll();assertThat(messages("received")).isEqualTo(1000);verify(sqs,times(100)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test void prometheusExportMatchesDashboardQueriesWithoutPersonalLabels() {
        var prometheus = new io.micrometer.prometheusmetrics.PrometheusMeterRegistry(
                io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT);
        try {
            var metrics=new SesEventConsumerMetrics(prometheus,properties);metrics.received(1);metrics.processed();metrics.acknowledged();
            String exported=prometheus.scrape();
            assertThat(exported).contains("ncf_ses_events_messages_total{result=\"acknowledged\"} 1.0",
                    "ncf_ses_events_consumer_enabled 1.0", "ncf_ses_events_queue_depth{state=\"visible\"} NaN",
                    "ncf_ses_events_last_poll_timestamp_seconds", "ncf_ses_events_failures_total");
            assertThat(exported).doesNotContain("email-tracking-events", "example.com", "store_id", "message_id");
        } finally { prometheus.close(); }
    }

}
