package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.payment.DodoTestGateway;
import br.com.nuvemcustomfields.repository.PaymentWebhookEventRepository;
import org.springframework.stereotype.Service;

@Service
public class DodoTestWebhookService extends DodoWebhookService {
    public DodoTestWebhookService(DodoTestGateway gateway, PaymentWebhookEventRepository events, PaymentSubscriptionService subscriptions) {
        super(gateway, events, subscriptions);
    }
}
