package br.com.nuvemcustomfields.repository;
import br.com.nuvemcustomfields.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface DlocalGoReversalRepository extends JpaRepository<DlocalGoReversal,String> {
    List<DlocalGoReversal> findByEnvironmentAndOrderId(PaymentEnvironment environment,String orderId);
}
