package br.com.nuvemcustomfields.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class AssetVersionModelAdvice {

    private final String assetVersion;

    public AssetVersionModelAdvice(@Value("${app.version:dev}") String assetVersion) {
        this.assetVersion = assetVersion;
    }

    @ModelAttribute("assetVersion")
    public String assetVersion() {
        return assetVersion;
    }
}
