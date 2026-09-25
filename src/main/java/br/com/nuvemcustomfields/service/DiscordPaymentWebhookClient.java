package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PaymentNotificationOutbox;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.properties.DiscordNotificationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

@Component
public class DiscordPaymentWebhookClient {
    private final URI webhookUri;
    private final RestClient client;

    public DiscordPaymentWebhookClient(DiscordNotificationProperties properties) {
        webhookUri = properties.configured() ? validWebhookUri(properties.paymentWebhookUrl()) : null;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean configured() { return webhookUri != null; }

    public void send(PaymentNotificationOutbox notification) {
        if (!configured()) throw new IllegalStateException("Webhook de pagamentos do Discord não configurado.");
        String amount = "BRL".equals(notification.getCurrency())
                ? NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(notification.getAmountValue())
                : notification.getCurrency() + " " + notification.getAmountValue();
        String plan = notification.getPlan() == PlanType.PREMIUM ? "Essencial" : "Pro";
        String content = "✅ Pagamento confirmado\n"
                + "Loja: " + notification.getStoreId() + "\n"
                + "Plano: " + plan + "\n"
                + "Valor: " + amount + "\n"
                + "Gateway: " + notification.getProvider() + "\n"
                + "Cobrança: " + notification.getPaymentId();
        client.post().uri(URI.create(webhookUri + "?wait=true")).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", content, "allowed_mentions", Map.of("parse", new String[0])))
                .retrieve().toBodilessEntity();
    }

    private static URI validWebhookUri(String value) {
        try {
            URI uri = URI.create(value);
            if ("https".equals(uri.getScheme()) && "discord.com".equals(uri.getHost())
                    && uri.getRawPath().matches("/api/webhooks/\\d+/[^/]+")
                    && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && uri.getUserInfo() == null) return uri;
        } catch (IllegalArgumentException ignored) {
            // A URL pode conter o token do webhook. Nunca a inclua em exceções ou logs.
        }
        throw new IllegalArgumentException("URL do webhook de pagamentos do Discord inválida.");
    }
}
