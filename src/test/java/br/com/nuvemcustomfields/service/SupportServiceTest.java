package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.entity.SupportMessage;
import br.com.nuvemcustomfields.entity.SupportMessageAuthor;
import br.com.nuvemcustomfields.entity.SupportTicket;
import br.com.nuvemcustomfields.entity.SupportTicketStatus;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.repository.SupportMessageRepository;
import br.com.nuvemcustomfields.repository.SupportTicketRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportServiceTest {

    private final SupportTicketRepository ticketRepository = mock(SupportTicketRepository.class);
    private final SupportMessageRepository messageRepository = mock(SupportMessageRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final SupportService service = new SupportService(ticketRepository, messageRepository, storeRepository);

    @Test
    void opensTicketWithNormalizedStoreMessage() {
        Store store = new Store();
        store.setStoreId(123L);
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupportTicket ticket = service.openTicket(store, "  Preciso de ajuda  ", "  Minha mensagem  ");

        assertThat(ticket.getStoreId()).isEqualTo(123L);
        assertThat(ticket.getSubject()).isEqualTo("Preciso de ajuda");
        assertThat(ticket.getStatus()).isEqualTo(SupportTicketStatus.OPEN);

        ArgumentCaptor<SupportMessage> messageCaptor = ArgumentCaptor.forClass(SupportMessage.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getAuthorType()).isEqualTo(SupportMessageAuthor.STORE);
        assertThat(messageCaptor.getValue().getMessage()).isEqualTo("Minha mensagem");
    }

    @Test
    void preventsStoreFromAccessingAnotherStoresTicket() {
        when(ticketRepository.findByIdAndStoreId(10L, 123L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireStoreTicket(10L, 123L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao encontrado");
    }

    @Test
    void closedTicketRejectsNewReplies() {
        SupportTicket ticket = new SupportTicket();
        ticket.setStoreId(123L);
        ticket.setStatus(SupportTicketStatus.CLOSED);
        when(ticketRepository.findByIdAndStoreId(10L, 123L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> service.replyFromStore(10L, 123L, "Nova mensagem"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reabra");
    }
}
