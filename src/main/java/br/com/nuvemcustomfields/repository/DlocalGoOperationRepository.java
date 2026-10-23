package br.com.nuvemcustomfields.repository;
import br.com.nuvemcustomfields.entity.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface DlocalGoOperationRepository extends JpaRepository<DlocalGoOperation,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from DlocalGoOperation o where o.id=:id")
    Optional<DlocalGoOperation> lockById(String id);
    List<DlocalGoOperation> findByStoreIdOrderByCreatedAtDesc(Long storeId);
    List<DlocalGoOperation> findTop100ByOrderByCreatedAtDesc();
    List<DlocalGoOperation> findByKindAndStateIn(DlocalGoOperation.Kind kind, Collection<DlocalGoOperation.State> states);
}
