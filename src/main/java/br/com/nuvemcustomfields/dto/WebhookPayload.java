package br.com.nuvemcustomfields.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record WebhookPayload(
        @JsonAlias("store_id") Long storeId,
        String event,
        Long id
) {
}
