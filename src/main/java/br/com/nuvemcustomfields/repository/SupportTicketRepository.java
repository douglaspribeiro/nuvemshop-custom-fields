package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.SupportTicket;
import br.com.nuvemcustomfields.entity.SupportTicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByStoreIdOrderByLastMessageAtDesc(Long storeId);

    List<SupportTicket> findAllByOrderByLastMessageAtDesc();

    Optional<SupportTicket> findByIdAndStoreId(Long id, Long storeId);

    long countByStatus(SupportTicketStatus status);
}
