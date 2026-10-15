package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.payment.DodoLiveGateway;
import br.com.nuvemcustomfields.repository.PaymentWebhookEventRepository;
import org.springframework.stereotype.Service;

@Service
public class DodoLiveWebhookService extends DodoWebhookService {
    public DodoLiveWebhookService(DodoLiveGateway gateway, PaymentWebhookEventRepository events, PaymentSubscriptionService subscriptions) {
        super(gateway, events, subscriptions);
    }
}
