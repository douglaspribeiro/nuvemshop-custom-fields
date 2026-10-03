package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.config.StorefrontTrafficMetricsFilter;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import io.micrometer.core.instrument.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/** Nomes são metadados separados: renomear loja/produto não fragmenta os contadores. */
@Service
public class StorefrontCatalogMetricsService {
    private static final Logger LOGGER=LoggerFactory.getLogger(StorefrontCatalogMetricsService.class);
    private final MeterRegistry registry;
    private final StoreRepository stores;
    private final PersonalizationRuleRepository rules;
    private final Map<Meter.Id,Meter> metadata=new HashMap<>();
    public StorefrontCatalogMetricsService(MeterRegistry registry, StoreRepository stores, PersonalizationRuleRepository rules) {
        this.registry=registry;this.stores=stores;this.rules=rules;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void ready() { refresh(); }
    @Scheduled(fixedDelayString="${monitoring.catalog-refresh-ms:60000}", initialDelayString="${monitoring.catalog-refresh-ms:60000}")
    public synchronized void refresh() {
        try {
            // Leia ambos antes de modificar gauges, preservando o último catálogo em falhas.
            var storeRows=stores.findAll(); var productRows=rules.findActiveProductMetadata();
            Set<Meter.Id> retained=new HashSet<>();
            for (var s:storeRows) {
                var gauge=Gauge.builder("ncf.store.info",()->1.0)
                        .description("Store name metadata; only registered stores")
                        .tag("store_id",s.getStoreId().toString()).tag("store_name",label(s.getStoreName(),"Loja "+s.getStoreId()))
                        .register(registry);
                retained.add(gauge.getId());metadata.put(gauge.getId(),gauge);
            }
            for (var p:productRows) {
                var gauge=Gauge.builder("ncf.personalized.product.info",()->1.0)
                        .description("Names of active configured products; only trusted database values")
                        .tag("store_id",p.storeId().toString()).tag("product_id",p.productId().toString())
                        .tag("store_name",label(p.storeName(),"Loja "+p.storeId()))
                        .tag("product_name",label(p.productName(),"Produto "+p.productId())).register(registry);
                retained.add(gauge.getId());metadata.put(gauge.getId(),gauge);
                Counter.builder(StorefrontTrafficMetricsFilter.PRODUCT_REQUEST_METRIC)
                        .tag("store_id",p.storeId().toString()).tag("product_id",p.productId().toString()).register(registry);
            }
            var obsolete=metadata.keySet().stream().filter(id->!retained.contains(id)).toList();
            for(var id:obsolete) { registry.remove(metadata.remove(id)); }
        } catch (DataAccessException ex) {
            LOGGER.warn("monitoring.catalog.refresh_failed type={}",ex.getClass().getSimpleName());
        }
    }
    private String label(String value,String fallback) { return value==null || value.isBlank()?fallback:value.strip(); }
}
