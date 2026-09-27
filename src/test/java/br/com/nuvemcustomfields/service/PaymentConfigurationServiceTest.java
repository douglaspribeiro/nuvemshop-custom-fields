package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PaymentCatalogPrice;
import br.com.nuvemcustomfields.entity.PaymentProviderType;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.payment.PaddleGateway;
import br.com.nuvemcustomfields.repository.PaymentCatalogPriceRepository;
import br.com.nuvemcustomfields.repository.PaymentRoutingHistoryRepository;
import br.com.nuvemcustomfields.repository.PaymentRoutingRuleRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentConfigurationServiceTest {
    private final PaymentCatalogPriceRepository catalog = mock(PaymentCatalogPriceRepository.class);
    private final PaddleGateway paddle = mock(PaddleGateway.class);
    private final PaymentConfigurationService service = new PaymentConfigurationService(
            mock(PaymentRoutingRuleRepository.class), mock(PaymentRoutingHistoryRepository.class), catalog,
            mock(PaymentGatewayRouter.class), paddle);

    @Test
    void keepsPriceIdAndExplainsValidationFailure() {
        PaymentCatalogPrice price = new PaymentCatalogPrice();
        price.setProvider(PaymentProviderType.PADDLE);
        price.setPlan(PlanType.PREMIUM);
        when(catalog.findById(12L)).thenReturn(Optional.of(price));
        doThrow(new IllegalArgumentException("Preço Paddle divergente.")).when(paddle).validateCatalog(price);

        var result = service.savePrice(12L, "ars", new BigDecimal("5599"), "pri_example", true);

        assertThat(result.validated()).isFalse();
        assertThat(result.message()).isEqualTo("Preço Paddle divergente.");
        assertThat(price.getProviderPriceId()).isEqualTo("pri_example");
        assertThat(price.isEnabled()).isFalse();
        assertThat(price.getValidationError()).isEqualTo("Preço Paddle divergente.");
        assertThat(price.getValidatedAt()).isNull();
        verify(catalog).save(price);
    }
}
