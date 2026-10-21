package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PaymentNotificationOutbox;
import br.com.nuvemcustomfields.repository.StoreRepository;
import java.math.BigDecimal;
import java.util.TreeMap;
import java.util.stream.Collectors;
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
    private final StoreRepository stores;
    private final ManagementReportService reports;

    public DiscordPaymentWebhookClient(DiscordNotificationProperties properties, StoreRepository stores,
                                       ManagementReportService reports) {
        this.stores = stores;
        this.reports = reports;
        webhookUri = properties.configured() ? validWebhookUri(properties.paymentWebhookUrl()) : null;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean configured() { return webhookUri != null; }

    public void send(PaymentNotificationOutbox notification) {
        if (!configured()) throw new IllegalStateException("Webhook de pagamentos do Discord não configurado.");
        client.post().uri(URI.create(webhookUri + "?wait=true")).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", content(notification), "allowed_mentions", Map.of("parse", new String[0])))
                .retrieve().toBodilessEntity();
    }

    String content(PaymentNotificationOutbox notification) {
        String amount = money(notification.getCurrency(), notification.getAmountValue());
        String plan = notification.getPlan().getDisplayName();
        String storeName = notification.getStoreName();
        if (storeName == null || storeName.isBlank()) {
            storeName = stores.findByStoreId(notification.getStoreId())
                    .map(br.com.nuvemcustomfields.entity.Store::getStoreName).orElse("Nome indisponível");
        }
        String store = "🏪 **Loja:** " + safe(storeName) + " · **ID:** " + notification.getStoreId() + "\n";
        String mrr = new TreeMap<>(reports.report().mrrByCurrency()).entrySet().stream()
                .map(entry -> money(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(" + "));
        String footer = "\n📈 **MRR total estimado:** " + (mrr.isEmpty() ? "Sem receita recorrente elegível" : mrr + "/mês");
        if (notification.getEventType() == PaymentNotificationOutbox.EventType.UPGRADE) {
            return "🚀 **Upgrade concluído**\n" + store
                    + "📦 **Planos:** " + notification.getSourcePlan().getDisplayName() + " → " + plan + "\n"
                    + "💰 **Ajuste único:** " + amount + "\n"
                    + "🎟️ **Cupom:** " + (notification.getCouponCode() == null ? "Não utilizado" : safe(notification.getCouponCode())) + "\n"
                    + "🔁 **Nova mensalidade:** " + money(notification.getCurrency(), notification.getRecurringAmount()) + "\n"
                    + "**Assinatura mantida:** " + notification.getSubscriptionId() + "\n"
                    + "💳 **Gateway:** " + notification.getProvider() + " · **Cobrança do ajuste:** "
                    + (notification.getChargeId() == null ? "Sem cobrança (ajuste zerado)" : notification.getChargeId())
                    + footer;
        }
        return "✅ **Pagamento confirmado**\n" + store
                + "📦 **Plano:** " + plan + "\n"
                + "💰 **Valor:** " + amount + "\n"
                + "💳 **Gateway:** " + notification.getProvider() + " · **Cobrança:** " + notification.getPaymentId()
                + footer;
    }

    private static String money(String currency, BigDecimal amount) {
        return "BRL".equals(currency)
                ? NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(amount)
                : currency + " " + amount.toPlainString();
    }

    private static String safe(String value) {
        return (value == null || value.isBlank() ? "Nome indisponível" : value).replaceAll("[\\r\\n]", " ").replaceAll("([\\\\`*_{}\\[\\]()<>~|])", "\\\\$1");
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
