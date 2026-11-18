package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.properties.HomologProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class HomologAppsMenuModelAdvice {
    private final HomologProperties homolog;

    public HomologAppsMenuModelAdvice(HomologProperties homolog) {
        this.homolog = homolog;
    }

    @ModelAttribute("appsMenuVisible")
    public boolean appsMenuVisible(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute(AdminSessionInterceptor.STORE_SESSION_KEY) instanceof Long storeId)) return false;
        return homolog.allows(storeId);
    }
}
