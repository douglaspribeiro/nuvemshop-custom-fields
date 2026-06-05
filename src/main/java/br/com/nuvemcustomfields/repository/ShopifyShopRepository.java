package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.ShopifyShop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ShopifyShopRepository extends JpaRepository<ShopifyShop, Long> {

    Optional<ShopifyShop> findByShopDomain(String shopDomain);

    @Query("select s from ShopifyShop s where s.id = :id and s.uninstalledAt is null")
    Optional<ShopifyShop> findActiveById(Long id);

    @Query("select s from ShopifyShop s where s.shopDomain = :shopDomain and s.uninstalledAt is null")
    Optional<ShopifyShop> findActiveByShopDomain(String shopDomain);
}
