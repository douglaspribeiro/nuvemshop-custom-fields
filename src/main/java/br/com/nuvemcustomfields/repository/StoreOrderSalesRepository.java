package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.StoreOrderSales;
import br.com.nuvemcustomfields.entity.StoreOrderSalesId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StoreOrderSalesRepository extends JpaRepository<StoreOrderSales, StoreOrderSalesId> {

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

    @Query("select coalesce(sum(r.totalItems), 0) from StoreOrderSales r "
            + "where r.id.storeId in (select s.storeId from Store s where s.uninstalledAt is null) "
            + "and r.id.storeId in (select sync.storeId from StoreSalesSync sync where sync.complete = true)")
    long totalItemsFromSyncedActiveStores();

    @Query("select coalesce(sum(r.personalizedItems), 0) from StoreOrderSales r "
            + "where r.id.storeId in (select s.storeId from Store s where s.uninstalledAt is null) "
            + "and r.id.storeId in (select sync.storeId from StoreSalesSync sync where sync.complete = true)")
    long personalizedItemsFromSyncedActiveStores();
}
