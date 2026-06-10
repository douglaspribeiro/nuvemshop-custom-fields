package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.Store;

import java.util.List;
import java.util.Optional;

public interface StoreRepository {
    Optional<Store> findByStoreId(Long storeId);
    Optional<Store> findActiveByStoreId(Long storeId);
    List<Store> findAll();
    long count();
    Store save(Store store);
}
