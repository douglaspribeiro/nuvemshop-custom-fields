package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.CreemGateway;
import br.com.nuvemcustomfields.payment.PaymentGatewayException;
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

class CreemCatalogServiceTest {
    final CreemGateway gateway = mock(CreemGateway.class);
    final CreemCatalogPublicationRepository publications = mock(CreemCatalogPublicationRepository.class);
    final PaymentCatalogPriceRepository prices = mock(PaymentCatalogPriceRepository.class);
    final ObjectMapper mapper = new ObjectMapper();
    CreemCatalogService service;
    CreemCatalogPublication operation;
    @BeforeEach void setup() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(gateway.apiConfigured()).thenReturn(true); when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(publications.findByPublicationKey(anyString())).thenAnswer(i -> Optional.ofNullable(operation));
        when(publications.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(operation));
        when(publications.saveAndFlush(any())).thenAnswer(i -> { operation = i.getArgument(0); operation.setId(1L); return operation; });
        when(publications.claim(anyLong(),any(),any())).thenReturn(1);
        service = new CreemCatalogService(gateway,publications,prices,mapper,manager);
    }
    String create() { return service.create(PlanType.PREMIUM,PaymentEnvironment.SANDBOX,"mx","Essencial","Mensal","USD",new BigDecimal("9.99"),"inclusive","admin"); }
    @Test void persistsReturnedIdAndDoesNotRepublishOnRepeatedSubmission() throws Exception {
        when(gateway.createProduct(anyString(),anyString(),anyString(),any(),anyString(),anyString())).thenReturn(mapper.readTree("{\"id\":\"prod_1\"}"));
        assertThat(create()).isEqualTo("prod_1"); assertThat(create()).isEqualTo("prod_1");
        assertThat(operation.getStatus()).isEqualTo("LINKED");
        verify(gateway,times(1)).createProduct(anyString(),anyString(),anyString(),any(),anyString(),eq(operation.getPublicationKey()));
        var captor = org.mockito.ArgumentCaptor.forClass(PaymentCatalogPrice.class);
        verify(prices).saveAndFlush(captor.capture()); assertThat(captor.getValue().getProviderPriceId()).isEqualTo("prod_1");
        assertThat(captor.getValue().isEnabled()).isFalse(); assertThat(captor.getValue().getCountryCode()).isEqualTo("MX");
    }
    @Test void retriesUnknownResponseWithTheSameKey() throws Exception {
        when(gateway.createProduct(anyString(),anyString(),anyString(),any(),anyString(),anyString()))
                .thenThrow(new PaymentGatewayException("Resposta indeterminada"))
                .thenReturn(mapper.readTree("{\"id\":\"prod_1\"}"));
        assertThatThrownBy(this::create).hasMessage("Resposta indeterminada");
        String key = operation.getPublicationKey(); assertThat(operation.getStatus()).isEqualTo("UNKNOWN");
        assertThat(service.resume(1L)).isEqualTo("prod_1");
        verify(gateway,times(2)).createProduct(anyString(),anyString(),anyString(),any(),anyString(),eq(key));
    }
    @Test void productSurvivesFailedLinkAndResumeSkipsRemoteCreation() throws Exception {
        when(gateway.createProduct(anyString(),anyString(),anyString(),any(),anyString(),anyString())).thenReturn(mapper.readTree("{\"id\":\"prod_1\"}"));
        when(prices.saveAndFlush(any())).thenThrow(new IllegalStateException("DB temporarily unavailable")).thenAnswer(i -> i.getArgument(0));
        assertThatThrownBy(this::create).isInstanceOf(IllegalArgumentException.class);
        assertThat(operation.getProductId()).isEqualTo("prod_1"); assertThat(operation.getStatus()).isEqualTo("CREATED");
        assertThat(service.resume(1L)).isEqualTo("prod_1");
        verify(gateway,times(1)).createProduct(anyString(),anyString(),anyString(),any(),anyString(),anyString());
    }
    @Test void rejectsConcurrentPublicationAndWrongEnvironmentBeforeApiCall() {
        when(publications.claim(anyLong(),any(),any())).thenReturn(0);
        assertThatThrownBy(this::create).hasMessageContaining("em processamento");
        assertThatThrownBy(() -> service.create(PlanType.PREMIUM,PaymentEnvironment.PRODUCTION,"MX","Plano","Mensal","USD",BigDecimal.TEN,"inclusive","admin")).hasMessageContaining("ambiente");
        verify(gateway,never()).createProduct(anyString(),anyString(),anyString(),any(),anyString(),anyString());
    }
    @Test void oldUnknownOperationRequiresManualLinkInsteadOfBlindRetry() {
        when(publications.claim(anyLong(),any(),any())).thenReturn(0);
        assertThatThrownBy(this::create).isInstanceOf(IllegalArgumentException.class);
        operation.setCreatedAt(Instant.now().minusSeconds(86400));
        when(publications.claim(anyLong(),any(),any())).thenReturn(1);
        assertThatThrownBy(() -> service.resume(1L)).hasMessageContaining("vincule o produto existente");
        verify(gateway,never()).createProduct(anyString(),anyString(),anyString(),any(),anyString(),anyString());
    }
}
