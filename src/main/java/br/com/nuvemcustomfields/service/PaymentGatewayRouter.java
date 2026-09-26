package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PaymentEnvironment;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.payment.PaymentGateway;
import br.com.nuvemcustomfields.repository.PaymentRoutingRuleRepository;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentGatewayRouter {
    private final Map<PaymentProviderType, PaymentGateway> gateways;
    private final PaymentRoutingRuleRepository routingRules;

    public PaymentGatewayRouter(List<PaymentGateway> gateways, PaymentRoutingRuleRepository routingRules) {
        this.gateways = new EnumMap<>(PaymentProviderType.class);
        this.routingRules = routingRules;
        gateways.forEach(gateway -> this.gateways.put(gateway.provider(), gateway));
    }

    public Optional<PaymentGateway> forStore(Store store) {
        if (store == null || store.getStoreCountryCode() == null) return Optional.empty();
        return routingRules.findByCountryCodeIgnoreCase(store.getStoreCountryCode())
                .filter(rule -> rule.isEnabled())
                .map(rule -> gateways.get(rule.getProvider()))
                .filter(gateway -> gateway != null && gateway.configured() && gateway.supports(store))
                .filter(gateway -> routingRules.findByCountryCodeIgnoreCase(store.getStoreCountryCode())
                        .map(rule -> rule.getEnvironment() == gateway.environment()).orElse(false));
    }

    public PaymentGateway requireForStore(Store store) {
        return forStore(store).orElseThrow(() -> new IllegalArgumentException(
                "Pagamento ainda nao esta disponivel para o pais desta loja."
        ));
    }

    public PaymentGateway require(PaymentProviderType provider) {
        PaymentGateway gateway = gateways.get(provider);
        if (gateway == null || !gateway.configured()) {
            throw new IllegalStateException("Gateway de pagamento indisponivel: " + provider);
        }
        return gateway;
    }

    public boolean configured(PaymentProviderType provider) {
        PaymentGateway gateway = gateways.get(provider);
        return gateway != null && gateway.configured();
    }

    public PaymentEnvironment environment(PaymentProviderType provider) {
        PaymentGateway gateway = gateways.get(provider);
        return gateway == null ? null : gateway.environment();
    }
}
