package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.SesEventsProperties;
import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/** Bounded process metrics: no message IDs, recipients, bodies, queue URLs or store labels. */
@Component
public class SesEventConsumerMetrics {
    public enum Failure { RECEIVE, PROCESS, ACKNOWLEDGE, ATTRIBUTES }
    private final Counter received;
    private final Counter processed;
    private final Counter acknowledged;
    private final Counter polls;
    private final Map<Failure, Counter> failures = new EnumMap<>(Failure.class);
    private final AtomicLong lastPoll = new AtomicLong();
    private final AtomicLong lastAck = new AtomicLong();
    private final AtomicLong lastAttributes = new AtomicLong();
    private volatile double visible = Double.NaN;
    private volatile double inFlight = Double.NaN;
    private volatile double delayed = Double.NaN;

    public SesEventConsumerMetrics(MeterRegistry registry, SesEventsProperties properties) {
        received = registry.counter("ncf.ses.events.messages", "result", "received");
        processed = registry.counter("ncf.ses.events.messages", "result", "processed");
        acknowledged = registry.counter("ncf.ses.events.messages", "result", "acknowledged");
        polls = registry.counter("ncf.ses.events.polls");
        for (Failure stage : Failure.values()) failures.put(stage,
                registry.counter("ncf.ses.events.failures", "stage", stage.name().toLowerCase(java.util.Locale.ROOT)));
        Gauge.builder("ncf.ses.events.consumer.enabled", properties, p -> p.enabled() ? 1 : 0).register(registry);
        registry.gauge("ncf.ses.events.last.poll.timestamp.seconds", lastPoll);
        registry.gauge("ncf.ses.events.last.ack.timestamp.seconds", lastAck);
        registry.gauge("ncf.ses.events.last.attributes.timestamp.seconds", lastAttributes);
        Gauge.builder("ncf.ses.events.queue.depth", this, m -> m.visible).tag("state", "visible").register(registry);
        Gauge.builder("ncf.ses.events.queue.depth", this, m -> m.inFlight).tag("state", "in_flight").register(registry);
        Gauge.builder("ncf.ses.events.queue.depth", this, m -> m.delayed).tag("state", "delayed").register(registry);
    }
    public void received(int count) {
        received.increment(count); polls.increment(); lastPoll.set(Instant.now().getEpochSecond());
    }
    public void processed() { processed.increment(); }
    public void acknowledged() { acknowledged.increment(); lastAck.set(Instant.now().getEpochSecond()); }
    public void failed(Failure stage) { failures.get(stage).increment(); }
    public void attributes(Map<QueueAttributeName, String> attributes) {
        // Parse every value before exposing a successful sample. Missing values are unknown, not zero.
        double v = number(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
        double f = number(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
        double d = number(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED));
        visible = v; inFlight = f; delayed = d;
        lastAttributes.set(Instant.now().getEpochSecond());
    }
    public void attributesUnavailable() { visible = inFlight = delayed = Double.NaN; }
    private double number(String value) {
        if (value == null) throw new IllegalArgumentException("Missing SQS attribute");
        long result = Long.parseLong(value);
        if (result < 0) throw new IllegalArgumentException("Invalid SQS attribute");
        return result;
    }
}
