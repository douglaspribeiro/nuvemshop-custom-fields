package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.ShopifyShop;

import java.util.Optional;

public interface ShopifyShopRepository {
    Optional<ShopifyShop> findByShopDomain(String shopDomain);
    Optional<ShopifyShop> findActiveById(Long id);
    Optional<ShopifyShop> findActiveByShopDomain(String shopDomain);
    ShopifyShop save(ShopifyShop shop);
}
