package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.entity.SupportTicket;
import br.com.nuvemcustomfields.properties.DiscordNotificationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;

@Component
public class DiscordSupportWebhookClient {
    private final URI webhookUri;
    private final RestClient client;

    public DiscordSupportWebhookClient(DiscordNotificationProperties properties) {
        webhookUri = properties.supportConfigured() ? validWebhookUri(properties.supportWebhookUrl()) : null;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean configured() { return webhookUri != null; }

    public void sendNewTicket(Store store, SupportTicket ticket) {
        send("📩 Novo chamado\nLoja: " + storeLabel(store) + "\nTicket: #" + ticket.getId()
                + "\nAssunto: " + ticket.getSubject());
    }

    public void sendStoreReply(Store store, SupportTicket ticket) {
        send("💬 Nova resposta de loja\nLoja: " + storeLabel(store) + "\nTicket: #" + ticket.getId()
                + "\nAssunto: " + ticket.getSubject());
    }

    private void send(String content) {
        if (!configured()) return;
        client.post().uri(URI.create(webhookUri + "?wait=true")).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", content, "allowed_mentions", Map.of("parse", new String[0])))
                .retrieve().toBodilessEntity();
    }

    private static String storeLabel(Store store) {
        String name = store.getStoreName() == null || store.getStoreName().isBlank()
                ? "Loja sem nome" : store.getStoreName();
        return name + " (" + store.getStoreId() + ")";
    }

    private static URI validWebhookUri(String value) {
        try {
            URI uri = URI.create(value);
            if ("https".equals(uri.getScheme()) && "discord.com".equals(uri.getHost())
                    && uri.getRawPath().matches("/api/webhooks/\\d+/[^/]+")
                    && uri.getRawQuery() == null && uri.getRawFragment() == null && uri.getUserInfo() == null) return uri;
        } catch (IllegalArgumentException ignored) {
            // O token do webhook não pode ser incluído em mensagens de erro ou logs.
        }
        throw new IllegalArgumentException("URL do webhook de suporte do Discord inválida.");
    }
}
