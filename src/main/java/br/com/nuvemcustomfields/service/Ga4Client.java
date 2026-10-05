package br.com.nuvemcustomfields.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;

@Component
public class Ga4Client {
    private final URI endpoint;
    private final RestClient client;
    public Ga4Client(@Value("${analytics.ga4.measurement-id:}") String measurementId,
                     @Value("${analytics.ga4.api-secret:}") String secret) {
        endpoint = measurementId.matches("G-[A-Z0-9]+") && !secret.isBlank()
                ? UriComponentsBuilder.fromUriString("https://www.google-analytics.com/mp/collect")
                    .queryParam("measurement_id",measurementId).queryParam("api_secret",secret).build().encode().toUri()
                : null;
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); factory.setReadTimeout(5000);
        client=RestClient.builder().requestFactory(factory).build();
    }
    public boolean configured(){return endpoint!=null;}
    public void send(String payload){
        if(!configured()) throw new IllegalStateException("GA4 Measurement Protocol não configurado.");
        client.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().toBodilessEntity();
    }
}
