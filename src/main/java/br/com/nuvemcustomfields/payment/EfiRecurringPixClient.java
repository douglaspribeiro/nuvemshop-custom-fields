package br.com.nuvemcustomfields.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.nuvemcustomfields.properties.FeatureAccessProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
public class EfiRecurringPixClient {
    public static final int MONTHLY_DEBITS = 24;
    @ConfigurationProperties("payments.efi.open-finance")
    public record Settings(boolean enabled, String clientId, String clientSecret, String certificate,
                           String beneficiaryKey, String cityCode, Boolean sandbox) {
        public boolean ready() {
            return enabled && sandbox != null && filled(clientId) && filled(clientSecret) && filled(certificate)
                    && filled(beneficiaryKey) && cityCode != null && cityCode.matches("[0-9]{7}");
        }
        private static boolean filled(String value) { return value != null && !value.isBlank(); }
        @Override public String toString() { return "Efí Open Finance"; }
    }
    public record Consent(String paymentId, URI redirect) {}
    public static final class RequestRejected extends PaymentGatewayException {
        RequestRejected() { super("A Efí recusou o agendamento. Confira os dados antes de tentar novamente."); }
    }
    private final Settings settings;
    private final ObjectMapper mapper;
    private final FeatureAccessProperties features;

    public EfiRecurringPixClient(Settings settings, ObjectMapper mapper, FeatureAccessProperties features) {
        this.settings = settings;
        this.mapper = mapper;
        this.features = features;
    }

    public Consent start(Long storeId, UUID requestId, String cpf, String participant, BigDecimal amount,
                         LocalDate firstDebit) {
        if (!features.allows(storeId, FeatureAccessProperties.Feature.SCHEDULED_PIX))
            throw new PaymentGatewayException("Pix agendado indisponível para esta loja.");
        if (amount.signum() <= 0 || !cpf.matches("[0-9]{11}"))
            throw new IllegalArgumentException("Dados do agendamento inválidos.");
        var schedule = Map.of("tipo", "mensal", "dataInicio", firstDebit.toString(),
                "diaDoMes", firstDebit.getDayOfMonth(), "quantidade", MONTHLY_DEBITS);
        var payment = Map.of("valor", amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                "codigoCidadeIBGE", settings.cityCode(), "idProprio", requestId.toString(), "recorrencia", schedule);
        var result = call(HttpMethod.POST, "/v1/pagamentos-recorrentes/pix", Map.of(), Map.of(
                "pagador", Map.of("cpf", cpf, "idParticipante", participant),
                "favorecido", Map.of("chave", settings.beneficiaryKey()), "pagamento", payment), requestId);
        String id = result.path("identificadorPagamento").asText("");
        URI redirect = URI.create(result.path("redirectURI").asText(""));
        if (id.isBlank() || !"https".equalsIgnoreCase(redirect.getScheme()) || redirect.getHost() == null
                || redirect.getUserInfo() != null) throw new PaymentGatewayException("Resposta de consentimento inválida.");
        return new Consent(id, redirect);
    }

    public JsonNode list(LocalDate start, LocalDate end) {
        var items = call(HttpMethod.GET, "/v1/pagamentos-recorrentes/pix?inicio={inicio}&fim={fim}", Map.of("inicio", start.toString(), "fim", end.toString()),
                Map.of(), null).path("pagamentos");
        if (!items.isArray()) throw new PaymentGatewayException("Resposta de consulta Pix inválida.");
        return items;
    }

    public JsonNode participants() {
        return call(HttpMethod.GET, "/v1/participantes?modalidade=pagamentos", Map.of(), Map.of(), null);
    }

    public JsonNode cancel(String paymentId) {
        return call(HttpMethod.PATCH, "/v1/pagamentos-recorrentes/pix/{id}/cancelar", Map.of("id", paymentId), Map.of(), null);
    }

    private JsonNode call(HttpMethod method, String path, Map<String,String> params, Map<String,Object> body, UUID requestId) {
        if (!settings.ready()) throw new PaymentGatewayException("Open Finance indisponível.");
        try {
            var keys = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(Path.of(settings.certificate()))) {
                keys.load(input, new char[0]);
            }
            var km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            km.init(keys, new char[0]);
            var tls = SSLContext.getInstance("TLS");
            tls.init(km.getKeyManagers(), null, null);
            try (var http = HttpClient.newBuilder().sslContext(tls).connectTimeout(Duration.ofSeconds(10)).build()) {
                var factory = new JdkClientHttpRequestFactory(http);
                factory.setReadTimeout(Duration.ofSeconds(20));
                var api = RestClient.builder().requestFactory(factory).baseUrl(settings.sandbox()
                        ? "https://openfinance-h.api.efipay.com.br" : "https://openfinance.api.efipay.com.br").build();
                var token = mapper.readTree(api.post().uri("/v1/oauth/token")
                        .headers(h -> h.setBasicAuth(settings.clientId(), settings.clientSecret()))
                        .contentType(MediaType.APPLICATION_JSON).body(Map.of("grant_type","client_credentials"))
                        .retrieve().body(String.class)).required("access_token").asText();
                var request = api.method(method).uri(path, params).headers(h -> h.setBearerAuth(token));
                if (requestId != null) request.header("x-idempotency-key", requestId.toString());
                if (!body.isEmpty()) request.contentType(MediaType.APPLICATION_JSON).body(body);
                return mapper.readTree(request.retrieve().body(String.class));
            }
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            if (method == HttpMethod.POST && java.util.Set.of(400,401,403,422).contains(ex.getStatusCode().value()))
                throw new RequestRejected();
            throw new PaymentGatewayException("A Efí não confirmou a operação Pix.");
        } catch (Exception ex) {
            throw new PaymentGatewayException("A Efí não confirmou a operação Pix.");
        }
    }
}
