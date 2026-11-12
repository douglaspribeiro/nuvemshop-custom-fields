package br.com.nuvemcustomfields.payment;

import br.com.nuvemcustomfields.properties.DodoTestProperties;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import br.com.nuvemcustomfields.properties.HomologProperties;
import org.springframework.web.client.RestClient;

@Component
public class DodoTestGateway extends DodoGateway {
    private final HomologProperties homolog;
    @org.springframework.beans.factory.annotation.Autowired
    public DodoTestGateway(DodoTestProperties properties, PaymentCatalogPriceRepository catalog, RestClient.Builder builder, ObjectMapper mapper, HomologProperties homolog) {
        this(properties, catalog, timedClient(builder, properties), mapper, homolog);
    }
    DodoTestGateway(DodoTestProperties properties, PaymentCatalogPriceRepository catalog, RestClient client, ObjectMapper mapper, HomologProperties homolog) {
        super(properties, catalog, client, mapper);
        this.homolog = homolog;
    }
    @Override public boolean allowed(br.com.nuvemcustomfields.entity.Store store) {
        return super.allowed(store) && homolog.allows(store.getStoreId());
    }
}
