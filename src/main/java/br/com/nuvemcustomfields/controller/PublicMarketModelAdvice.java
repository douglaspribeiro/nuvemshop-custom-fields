package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.PublicMarketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = {AuthController.class, PublicPagesController.class})
public class PublicMarketModelAdvice {
    private final PublicMarketService markets;

    public PublicMarketModelAdvice(PublicMarketService markets) { this.markets = markets; }

    @ModelAttribute
    public void publicMarket(HttpServletRequest request, HttpServletResponse response, Model model) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!(path.equals("/") || path.equals("/precos/") || path.equals("/termos/"))) return;
        model.addAttribute("publicMarket", markets.resolve(request));
        model.addAttribute("countryOptions", markets.countryOptions());
        response.setHeader("Cache-Control", "private, no-store");
    }
}
