package br.com.nuvemcustomfields.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class AssetVersionModelAdvice {

    private final String assetVersion;
    private final String ga4MeasurementId;
    private final String companyLegalName;
    private final String companyTaxId;
    private final String supportEmail;

    public AssetVersionModelAdvice(@Value("${app.version:dev}") String assetVersion,
                                   @Value("${analytics.ga4.measurement-id:}") String ga4MeasurementId,
                                   @Value("${legal.operator-name:}") String companyLegalName,
                                   @Value("${legal.document:}") String companyTaxId,
                                   @Value("${legal.support-email:}") String supportEmail) {
        this.assetVersion = assetVersion;
        this.ga4MeasurementId = ga4MeasurementId;
        this.companyLegalName = companyLegalName;
        this.companyTaxId = companyTaxId;
        this.supportEmail = supportEmail;
    }

    @ModelAttribute("assetVersion")
    public String assetVersion() {
        return assetVersion;
    }

    @ModelAttribute("ga4MeasurementId")
    public String ga4MeasurementId() {
        return ga4MeasurementId;
    }

    @ModelAttribute("companyLegalName")
    public String companyLegalName() { return companyLegalName; }

    @ModelAttribute("companyTaxId")
    public String companyTaxId() { return companyTaxId; }

    @ModelAttribute("supportEmail")
    public String supportEmail() { return supportEmail; }
}
