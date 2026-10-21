package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.StoreOrderSales;
import br.com.nuvemcustomfields.entity.StoreOrderSalesId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StoreOrderSalesRepository extends JpaRepository<StoreOrderSales, StoreOrderSalesId> {

    @Query("select new br.com.nuvemcustomfields.dto.StoreSalesCount(r.id.storeId, count(r), sum(r.personalizedItems)) "
            + "from StoreOrderSales r where r.id.storeId in (select sync.storeId from StoreSalesSync sync where sync.complete = true) and r.personalizedItems > 0 and r.createdAt >= :from and r.createdAt < :until "
            + "group by r.id.storeId")
    java.util.List<br.com.nuvemcustomfields.dto.StoreSalesCount> summarizeByStore(
            @org.springframework.data.repository.query.Param("from") java.time.Instant from,
            @org.springframework.data.repository.query.Param("until") java.time.Instant until);

    @Query("select new br.com.nuvemcustomfields.dto.StoreMetricCount(r.id.storeId, count(r)) "
            + "from StoreOrderSales r where r.id.storeId in (select sync.storeId from StoreSalesSync sync where sync.complete = true) and r.personalizedItems > 0 group by r.id.storeId")
    java.util.List<br.com.nuvemcustomfields.dto.StoreMetricCount> countPersonalizedOrdersByStore();

    @Query(value = "select r from StoreOrderSales r "
            + "where r.personalizedItems > 0 "
            + "and r.id.storeId in (select s.storeId from Store s where s.uninstalledAt is null) "
            + "and r.id.storeId in (select sync.storeId from StoreSalesSync sync where sync.complete = true) "
            + "order by r.createdAt desc, r.id.orderId desc",
            countQuery = "select count(r) from StoreOrderSales r "
                    + "where r.personalizedItems > 0 "
                    + "and r.id.storeId in (select s.storeId from Store s where s.uninstalledAt is null) "
                    + "and r.id.storeId in (select sync.storeId from StoreSalesSync sync where sync.complete = true)")
    Page<StoreOrderSales> findSales(Pageable pageable);

    @Query("select count(r) from StoreOrderSales r "
            + "where r.personalizedItems > 0 "
            + "and r.id.storeId in (select s.storeId from Store s where s.uninstalledAt is null) "
            + "and r.id.storeId in (select sync.storeId from StoreSalesSync sync where sync.complete = true)")
    long personalizedOrdersFromSyncedActiveStores();

    @Query("select coalesce(sum(r.personalizedItems), 0) from StoreOrderSales r "
            + "where r.id.storeId in (select s.storeId from Store s where s.uninstalledAt is null) "
            + "and r.id.storeId in (select sync.storeId from StoreSalesSync sync where sync.complete = true)")
    long personalizedItemsFromSyncedActiveStores();
}
