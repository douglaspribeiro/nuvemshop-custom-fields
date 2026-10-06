package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.properties.DiscordNotificationProperties;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class DiscordPaymentWebhookClientTest {
    private final DiscordPaymentWebhookClient client=new DiscordPaymentWebhookClient(new DiscordNotificationProperties(null,null));
    @Test void upgradeMessageDistinguishesAdjustmentFromRecurringPayment(){
        var n=notification();n.setEventType(PaymentNotificationOutbox.EventType.UPGRADE);n.setStoreName("Dark Hunter");
        n.setSourcePlan(PlanType.PREMIUM_PLUS);n.setPlan(PlanType.PREMIUM_ULTRA);n.setCouponCode("DARK");
        n.setSubscriptionId("123");n.setChargeId("456");n.setRecurringAmount(new BigDecimal("59.90"));
        assertThat(client.content(n)).contains("Upgrade concluído","Dark Hunter · ID 7744400","Pro → Ultra", "8,97", "DARK",
                "Nova mensalidade: R$", "59,90", "Assinatura mantida: 123","Cobrança do ajuste: 456");
        n.setCouponCode(null);n.setChargeId(null);n.setAmountValue(BigDecimal.ZERO);
        assertThat(client.content(n)).contains("Não utilizado","Sem cobrança (ajuste zerado)","0,00");
    }
    @Test void legacyPaymentMessageRemainsUnchanged(){
        var n=notification();n.setPlan(PlanType.PREMIUM_PLUS);
        assertThat(client.content(n)).contains("Pagamento confirmado","Plano: Pro","Cobrança: payment-1").doesNotContain("Upgrade concluído");
    }
    private PaymentNotificationOutbox notification(){
        var n=new PaymentNotificationOutbox();n.setStoreId(7744400L);n.setProvider(PaymentProviderType.EFI);
        n.setPaymentId("payment-1");n.setCurrency("BRL");n.setAmountValue(new BigDecimal("8.97"));return n;
    }
    @Test void creemMessageShowsActualPaidCurrencyAndGateway(){
        var n=notification();n.setProvider(PaymentProviderType.CREEM);n.setCurrency("USD");n.setPlan(PlanType.PREMIUM_PLUS);
        assertThat(client.content(n)).contains("Pagamento confirmado", "Valor: USD 8.97", "Gateway: CREEM");
        n.setEventType(PaymentNotificationOutbox.EventType.UPGRADE);n.setSourcePlan(PlanType.PREMIUM);
        n.setRecurringAmount(new BigDecimal("19.99"));n.setSubscriptionId("sub_1");n.setChargeId("tran_upgrade");
        assertThat(client.content(n)).contains("Upgrade concluído", "Essencial → Pro", "Ajuste único: USD 8.97",
                "Nova mensalidade: USD 19.99", "Gateway: CREEM", "Cobrança do ajuste: tran_upgrade");
    }
}
