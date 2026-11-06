package br.com.nuvemcustomfields.service;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;
import java.util.*;

/** Confirmed transport returns only; dry runs and uncertain SMTP results are excluded. */
@Component
public class ActivationEmailDeliveryMetrics {
    private final Map<String, Counter> sent = new HashMap<>();
    public ActivationEmailDeliveryMetrics(MeterRegistry registry) {
        for (String step : List.of("D3", "D7", "D11"))
            sent.put(step, registry.counter("ncf.activation.email.sent", "step", step));
    }
    public void sent(String step) { sent.get(step).increment(); }
}
