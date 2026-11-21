package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.SupportAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SupportAttachmentRepository extends JpaRepository<SupportAttachment, Long> {

    interface Summary {
        Long getId();
        Long getMessageId();
        String getFilename();
    }

    @Query("""
            select a.id as id, a.message.id as messageId, a.filename as filename
            from SupportAttachment a where a.message.ticketId = :ticketId order by a.id
            """)
    List<Summary> summaries(Long ticketId);

    Optional<SupportAttachment> findByIdAndMessageTicketId(Long id, Long ticketId);

    @Query("""
            select a from SupportAttachment a where a.id = :id and a.message.ticketId = :ticketId
                and exists (select t.id from SupportTicket t where t.id = :ticketId and t.storeId = :storeId)
            """)
    Optional<SupportAttachment> findForStore(Long id, Long ticketId, Long storeId);
}
