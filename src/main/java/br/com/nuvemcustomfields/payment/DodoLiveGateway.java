package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.properties.DodoProperties;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DodoLiveGateway extends DodoGateway {
    @org.springframework.beans.factory.annotation.Autowired
    public DodoLiveGateway(DodoProperties properties, PaymentCatalogPriceRepository catalog, RestClient.Builder builder, ObjectMapper mapper) {
        this(properties, catalog, timedClient(builder, properties), mapper);
    }
    DodoLiveGateway(DodoProperties properties, PaymentCatalogPriceRepository catalog, RestClient client, ObjectMapper mapper) {
        super(properties, catalog, client, mapper);
    }
}
