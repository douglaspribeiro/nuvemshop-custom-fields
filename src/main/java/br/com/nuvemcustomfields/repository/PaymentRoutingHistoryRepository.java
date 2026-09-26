package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PaymentRoutingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentRoutingHistoryRepository extends JpaRepository<PaymentRoutingHistory, Long> {
    List<PaymentRoutingHistory> findTop50ByOrderByChangedAtDesc();
}
