package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.BuyerStorageProvider;
import br.com.nuvemcustomfields.entity.IntegrationLog;
import br.com.nuvemcustomfields.repository.IntegrationLogRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuyerStorageConfigurationService {
    private final StoreRepository stores;
    private final IntegrationLogRepository logs;
    private final BuyerFileStorage storage;
    public BuyerStorageConfigurationService(StoreRepository stores, IntegrationLogRepository logs, BuyerFileStorage storage) {
        this.stores = stores; this.logs = logs; this.storage = storage;
    }
    @Transactional
    public void update(long storeId, BuyerStorageProvider provider) {
        var store = stores.findByStoreId(storeId).orElseThrow();
        if (provider != null && !storage.forProvider(provider).enabled())
            throw new IllegalArgumentException("O armazenamento selecionado não está configurado no servidor.");
        var previous = store.getBuyerUploadStorageProvider();
        store.setBuyerUploadStorageProvider(provider);
        stores.save(store);
        var event = new IntegrationLog();
        event.setStoreId(storeId); event.setLevel("INFO"); event.setEventType("buyer.storage.override");
        event.setMessage("Destino de novos uploads: " + (previous == null ? "DEFAULT" : previous.name())
                + " -> " + (provider == null ? "DEFAULT" : provider.name()));
        logs.save(event);
    }
}
