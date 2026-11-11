package br.com.nuvemcustomfields.service;

import java.util.List;

public record SupportReplyEvent(Long ticketId, String recipient, String subject, String message,
                                List<Attachment> attachments) {
    public SupportReplyEvent(Long ticketId, String recipient, String subject, String message) {
        this(ticketId, recipient, subject, message, List.of());
    }

    public SupportReplyEvent {
        attachments = List.copyOf(attachments);
    }

    public record Attachment(String filename, String contentType, byte[] bytes) {
    }
}
