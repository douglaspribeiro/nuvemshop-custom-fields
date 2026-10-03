package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.entity.SupportMessage;
import br.com.nuvemcustomfields.entity.SupportMessageAuthor;
import br.com.nuvemcustomfields.entity.SupportTicket;
import br.com.nuvemcustomfields.entity.SupportTicketStatus;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.repository.SupportMessageRepository;
import br.com.nuvemcustomfields.repository.SupportTicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

@Service
public class SupportService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SupportService.class);

    private static final int SUBJECT_MAX_LENGTH = 160;
    private static final int MESSAGE_MAX_LENGTH = 5000;

    private final SupportTicketRepository ticketRepository;
    private final SupportMessageRepository messageRepository;
    private final StoreRepository storeRepository;
    private final DiscordSupportWebhookClient discord;
    private final ApplicationEventPublisher events;

    public SupportService(
            SupportTicketRepository ticketRepository,
            SupportMessageRepository messageRepository,
            StoreRepository storeRepository,
            DiscordSupportWebhookClient discord,
            ApplicationEventPublisher events
    ) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.storeRepository = storeRepository;
        this.discord = discord;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<SupportTicket> ticketsForStore(Long storeId) {
        return ticketRepository.findByStoreIdOrderByLastMessageAtDesc(storeId);
    }

    @Transactional(readOnly = true)
    public List<SupportTicket> allTickets() {
        return ticketRepository.findAllByOrderByLastMessageAtDesc().stream()
                .sorted(java.util.Comparator.comparing((SupportTicket ticket) -> ticket.getStatus() != SupportTicketStatus.OPEN)
                        .thenComparing(ticket -> !ticket.isPriority())
                        .thenComparing(SupportTicket::getLastMessageAt, java.util.Comparator.reverseOrder()))
                .toList();
    }

    @Transactional(readOnly = true)
    public long openTickets() {
        return ticketRepository.countByStatus(SupportTicketStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public SupportTicket requireStoreTicket(Long ticketId, Long storeId) {
        return ticketRepository.findByIdAndStoreId(ticketId, storeId)
                .orElseThrow(() -> new IllegalArgumentException("Chamado nao encontrado para esta loja."));
    }

    @Transactional(readOnly = true)
    public SupportTicket requireTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Chamado nao encontrado."));
    }

    @Transactional(readOnly = true)
    public List<SupportMessage> messages(Long ticketId) {
        return messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    @Transactional(readOnly = true)
    public Store requireTicketStore(SupportTicket ticket) {
        return storeRepository.findByStoreId(ticket.getStoreId())
                .orElseThrow(() -> new IllegalArgumentException("Loja do chamado nao encontrada."));
    }

    @Transactional
    public SupportTicket openTicket(Store store, String subject, String message) {
        SupportTicket ticket = openTicket(store.getStoreId(), subject, message, store.getEffectivePlan());
        notifyDiscord(() -> discord.sendNewTicket(store, ticket), ticket.getId(), "new_ticket");
        return ticket;
    }

    @Transactional
    public SupportTicket openAutomatedTicket(Long storeId, String subject, String message) {
        if (storeId == null) {
            throw new IllegalArgumentException("Identificador da loja nao informado.");
        }
        return openTicket(storeId, subject, message,
                storeRepository.findByStoreId(storeId).map(Store::getEffectivePlan).orElse(null));
    }

    private SupportTicket openTicket(Long storeId, String subject, String message, br.com.nuvemcustomfields.entity.PlanType plan) {
        String normalizedSubject = normalize(subject, "Informe o assunto.", SUBJECT_MAX_LENGTH);
        String normalizedMessage = normalize(message, "Escreva uma mensagem.", MESSAGE_MAX_LENGTH);
        Instant now = Instant.now();

        SupportTicket ticket = new SupportTicket();
        ticket.setStoreId(storeId);
        ticket.setPlanAtOpen(plan);
        ticket.setSubject(normalizedSubject);
        ticket.setStatus(SupportTicketStatus.OPEN);
        ticket.setUpdatedAt(now);
        ticket.setLastMessageAt(now);
        SupportTicket saved = ticketRepository.save(ticket);

        saveMessage(saved.getId(), SupportMessageAuthor.STORE, normalizedMessage);
        return saved;
    }

    @Transactional
    public void replyFromStore(Long ticketId, Long storeId, String message) {
        SupportTicket ticket = requireStoreTicket(ticketId, storeId);
        addReply(ticket, SupportMessageAuthor.STORE, message);
        Store store = requireTicketStore(ticket);
        notifyDiscord(() -> discord.sendStoreReply(store, ticket), ticket.getId(), "store_reply");
    }

    @Transactional
    public void replyFromSupport(Long ticketId, String message) {
        SupportTicket ticket = requireTicket(ticketId);
        addReply(ticket, SupportMessageAuthor.SUPPORT, message);
        String recipient = storeRepository.findByStoreId(ticket.getStoreId())
                .map(Store::getStoreEmail).orElse(null);
        events.publishEvent(new SupportReplyEvent(ticket.getId(), recipient, ticket.getSubject(), message.strip()));
    }

    @Transactional
    public void updateStatus(Long ticketId, SupportTicketStatus status) {
        SupportTicket ticket = requireTicket(ticketId);
        ticket.setStatus(status);
        ticket.setUpdatedAt(Instant.now());
        ticketRepository.save(ticket);
    }

    private void addReply(SupportTicket ticket, SupportMessageAuthor author, String message) {
        if (ticket.getStatus() == SupportTicketStatus.CLOSED) {
            throw new IllegalStateException("Reabra o chamado antes de responder.");
        }
        String normalized = normalize(message, "Escreva uma mensagem.", MESSAGE_MAX_LENGTH);
        saveMessage(ticket.getId(), author, normalized);
        Instant now = Instant.now();
        ticket.setUpdatedAt(now);
        ticket.setLastMessageAt(now);
        ticketRepository.save(ticket);
    }

    private void saveMessage(Long ticketId, SupportMessageAuthor author, String message) {
        SupportMessage supportMessage = new SupportMessage();
        supportMessage.setTicketId(ticketId);
        supportMessage.setAuthorType(author);
        supportMessage.setMessage(message);
        messageRepository.save(supportMessage);
    }

    private void notifyDiscord(Runnable action, Long ticketId, String event) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            // Suporte não pode deixar de ser registrado por falha no canal de aviso.
            LOGGER.warn("support.discord_notification_failed ticket_id={} event={} type={}",
                    ticketId, event, ex.getClass().getSimpleName());
        }
    }

    private String normalize(String value, String emptyMessage, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("Texto excede o limite de " + maxLength + " caracteres.");
        }
        return normalized;
    }
}
