package br.com.nuvemcustomfields.service;

public record SupportReplyEvent(Long ticketId, String recipient, String subject, String message) {
}
