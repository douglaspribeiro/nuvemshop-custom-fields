package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.properties.DodoTestProperties;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DodoTestGateway extends DodoGateway {
    @org.springframework.beans.factory.annotation.Autowired
    public DodoTestGateway(DodoTestProperties properties, PaymentCatalogPriceRepository catalog, RestClient.Builder builder, ObjectMapper mapper) {
        this(properties, catalog, timedClient(builder, properties), mapper);
    }
    DodoTestGateway(DodoTestProperties properties, PaymentCatalogPriceRepository catalog, RestClient client, ObjectMapper mapper) {
        super(properties, catalog, client, mapper);
    }
}
