package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.payment.DodoTestGateway;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class DodoTestCheckoutService extends DodoCheckoutService {
    public DodoTestCheckoutService(StoreRepository stores, PaymentSubscriptionRepository subscriptions,
            PaymentAttemptRepository attempts, PaymentGatewayRouter router, DodoTestGateway gateway,
            NuvemshopProperties properties, PlatformTransactionManager manager) {
        super(stores, subscriptions, attempts, router, gateway, properties, manager);
    }
}
