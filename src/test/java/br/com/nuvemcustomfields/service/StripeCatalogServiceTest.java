package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StripeCatalogServiceTest {
    final StripeGateway gateway = mock(StripeGateway.class);
    final StripeCatalogPublicationRepository publications = mock(StripeCatalogPublicationRepository.class);
    final PaymentCatalogPriceRepository prices = mock(PaymentCatalogPriceRepository.class);
    final ObjectMapper mapper = new ObjectMapper();
    final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    StripeCatalogService service;
    StripeCatalogPublication operation;
    @BeforeEach void setup() throws Exception {
        when(manager.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        when(gateway.apiConfigured()).thenReturn(true); when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(gateway.validatedProductId(any())).thenReturn("prod_1");
        when(gateway.createProductAndPrice(anyString(),anyString(),anyString(),any(),any(),anyString()))
                .thenReturn(mapper.readTree("{\"id\":\"prod_1\",\"default_price\":\"price_1\"}"));
        when(publications.findByPublicationKey(anyString())).thenAnswer(i -> Optional.ofNullable(operation));
        when(publications.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(operation));
        when(publications.saveAndFlush(any())).thenAnswer(i -> { operation=i.getArgument(0); operation.setId(1L); return operation; });
        when(publications.claim(anyLong(),any(),any())).thenReturn(1);
        service = new StripeCatalogService(gateway,publications,prices,mapper,manager);
    }
    String create() { return service.create(PlanType.PREMIUM,PaymentEnvironment.SANDBOX,"Essencial","Mensal","ARS",new BigDecimal("5599"),"admin"); }
    @Test void commitsReservationBeforeCreationAndSavesBothIdsWithoutEnablingSales() throws Exception {
        when(gateway.createProductAndPrice(anyString(),anyString(),anyString(),any(),any(),anyString())).thenAnswer(i -> {
            assertThat(operation.getPublicationKey()).isNotBlank(); verify(manager).commit(any());
            return mapper.readTree("{\"id\":\"prod_1\",\"default_price\":\"price_1\"}");
        });
        assertThat(create()).isEqualTo("price_1"); assertThat(create()).isEqualTo("price_1");
        assertThat(operation.getProductId()).isEqualTo("prod_1"); assertThat(operation.getPriceId()).isEqualTo("price_1");
        assertThat(operation.getStatus()).isEqualTo("LINKED"); assertThat(operation.getReservationKey()).isNull();
        verify(gateway,times(1)).createProductAndPrice(anyString(),anyString(),anyString(),any(),eq(PlanType.PREMIUM),eq(operation.getPublicationKey()));
        var capture = org.mockito.ArgumentCaptor.forClass(PaymentCatalogPrice.class); verify(prices).saveAndFlush(capture.capture());
        assertThat(capture.getValue().getProviderPriceId()).isEqualTo("price_1");
        assertThat(capture.getValue().isEnabled()).isFalse(); assertThat(capture.getValue().isValidated()).isTrue();
        assertThat(capture.getValue().getCountryCode()).isEqualTo("AR");
    }
    @Test void retriesUnknownResponseWithTheOriginalIdempotencyKey() throws Exception {
        when(gateway.createProductAndPrice(anyString(),anyString(),anyString(),any(),any(),anyString()))
                .thenThrow(new PaymentGatewayException("Resposta indeterminada"))
                .thenReturn(mapper.readTree("{\"id\":\"prod_1\",\"default_price\":\"price_1\"}"));
        assertThatThrownBy(this::create).hasMessage("Resposta indeterminada");
        assertThat(operation.getStatus()).isEqualTo("UNKNOWN"); String key=operation.getPublicationKey();
        assertThat(service.resume(1L)).isEqualTo("price_1");
        verify(gateway,times(2)).createProductAndPrice(anyString(),anyString(),anyString(),any(),any(),eq(key));
    }
    @Test void remoteIdsSurviveLocalFailureAndResumeDoesNotCreateAgain() {
        when(prices.saveAndFlush(any())).thenThrow(new IllegalStateException("DB unavailable")).thenAnswer(i -> i.getArgument(0));
        assertThatThrownBy(this::create).isInstanceOf(IllegalArgumentException.class);
        assertThat(operation.getProductId()).isEqualTo("prod_1"); assertThat(operation.getPriceId()).isEqualTo("price_1");
        assertThat(operation.getStatus()).isEqualTo("CREATED");
        assertThat(service.resume(1L)).isEqualTo("price_1");
        verify(gateway,times(1)).createProductAndPrice(anyString(),anyString(),anyString(),any(),any(),anyString());
    }
    @Test void mismatchedProductCannotBeLinkedAndSavedRemoteIdsCanBeRevalidated() {
        when(gateway.validatedProductId(any())).thenReturn("prod_other").thenReturn("prod_1");
        assertThatThrownBy(this::create).hasMessageContaining("outro produto");
        assertThat(operation.getStatus()).isEqualTo("CREATED"); verify(prices,never()).saveAndFlush(any());
        assertThat(service.resume(1L)).isEqualTo("price_1");
        verify(gateway,times(1)).createProductAndPrice(anyString(),anyString(),anyString(),any(),any(),anyString());
    }
    @Test void rejectsConcurrentAndWrongEnvironmentRequestsBeforeCreation() {
        when(publications.claim(anyLong(),any(),any())).thenReturn(0);
        assertThatThrownBy(this::create).hasMessageContaining("em processamento");
        assertThatThrownBy(() -> service.create(PlanType.PREMIUM,PaymentEnvironment.PRODUCTION,"Nome","Mensal","ARS",BigDecimal.TEN,"admin"))
                .hasMessageContaining("ambiente");
        verify(gateway,never()).createProductAndPrice(anyString(),anyString(),anyString(),any(),any(),anyString());
    }
    @Test void doesNotReplayUnknownOperationAfterStripeCanExpireItsIdempotencyKey() {
        when(publications.claim(anyLong(),any(),any())).thenReturn(0);
        assertThatThrownBy(this::create).isInstanceOf(IllegalArgumentException.class);
        operation.setCreatedAt(Instant.now().minusSeconds(86400));
        when(publications.claim(anyLong(),any(),any())).thenReturn(1);
        assertThatThrownBy(() -> service.resume(1L)).hasMessageContaining("vincule o preço existente");
        verify(gateway,never()).createProductAndPrice(anyString(),anyString(),anyString(),any(),any(),anyString());
    }
    @Test void existingPriceCanResolveAnUnknownPublicationWithoutCreatingAnotherProduct() {
        when(publications.claim(anyLong(),any(),any())).thenReturn(0);
        assertThatThrownBy(this::create).isInstanceOf(IllegalArgumentException.class);
        when(publications.findByReservationKey(anyString())).thenReturn(Optional.of(operation));
        service.bind(PlanType.PREMIUM,PaymentEnvironment.SANDBOX,"ARS",new BigDecimal("5599"),"price_1");
        assertThat(operation.getStatus()).isEqualTo("LINKED"); assertThat(operation.getPriceId()).isEqualTo("price_1");
        verify(gateway,never()).createProductAndPrice(anyString(),anyString(),anyString(),any(),any(),anyString());
    }
    @Test void rejectsUnsupportedCurrencyFreePlanAndFractionalMinorUnits() {
        assertThatThrownBy(() -> service.create(PlanType.FREE,PaymentEnvironment.SANDBOX,"Nome","Mensal","ARS",BigDecimal.TEN,"admin")).hasMessageContaining("plano pago");
        assertThatThrownBy(() -> service.create(PlanType.PREMIUM,PaymentEnvironment.SANDBOX,"Nome","Mensal","EUR",BigDecimal.TEN,"admin")).hasMessageContaining("ARS, USD ou BRL");
        assertThatThrownBy(() -> service.create(PlanType.PREMIUM,PaymentEnvironment.SANDBOX,"Nome","Mensal","ARS",new BigDecimal("9.999"),"admin")).hasMessageContaining("duas casas");
        verifyNoInteractions(publications);
    }
}
