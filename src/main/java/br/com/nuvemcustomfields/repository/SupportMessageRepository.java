package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    List<SupportMessage> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
