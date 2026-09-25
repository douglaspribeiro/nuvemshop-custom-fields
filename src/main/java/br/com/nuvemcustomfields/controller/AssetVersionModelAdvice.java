package br.com.nuvemcustomfields.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class AssetVersionModelAdvice {

    private final String assetVersion;
    private final String ga4MeasurementId;

    public AssetVersionModelAdvice(@Value("${app.version:dev}") String assetVersion,
                                   @Value("${analytics.ga4.measurement-id:}") String ga4MeasurementId) {
        this.assetVersion = assetVersion;
        this.ga4MeasurementId = ga4MeasurementId;
    }

    @ModelAttribute("assetVersion")
    public String assetVersion() {
        return assetVersion;
    }

    @ModelAttribute("ga4MeasurementId")
    public String ga4MeasurementId() {
        return ga4MeasurementId;
    }
}
