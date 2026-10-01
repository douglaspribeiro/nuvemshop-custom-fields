package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.WinbackEmailEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WinbackEmailEventRepository extends JpaRepository<WinbackEmailEvent, String> {
    List<WinbackEmailEvent> findByEmailIdInOrderByOccurredAtAsc(List<String> emailIds);
}
