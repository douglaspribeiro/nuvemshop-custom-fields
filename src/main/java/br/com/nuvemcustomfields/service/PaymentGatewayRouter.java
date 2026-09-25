package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.payment.PaymentGateway;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentGatewayRouter {
    private final Map<PaymentProviderType, PaymentGateway> gateways;

    public PaymentGatewayRouter(List<PaymentGateway> gateways) {
        this.gateways = new EnumMap<>(PaymentProviderType.class);
        gateways.forEach(gateway -> this.gateways.put(gateway.provider(), gateway));
    }

    public Optional<PaymentGateway> forStore(Store store) {
        PaymentGateway preferred = gateways.get(PaymentProviderType.EFI);
        if (preferred != null && preferred.configured() && preferred.supports(store)) return Optional.of(preferred);
        return gateways.values().stream().filter(PaymentGateway::configured).filter(g -> g.supports(store)).findFirst();
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
}
