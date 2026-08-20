package br.com.nuvemcustomfields.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

/**
 * Prioriza o idioma da loja em sessao (gravado pelo AdminSessionInterceptor a partir do
 * pais retornado por GET /store). Sem loja em sessao — paginas publicas — cai para o
 * Accept-Language do navegador e, por ultimo, pt-BR.
 */
public class AppLocaleResolver implements LocaleResolver {

    public static final String SESSION_KEY = "appLocale";

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SESSION_KEY) instanceof Locale locale) {
            return locale;
        }
        return StoreLocale.forLanguageHeader(request.getLocale());
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        request.getSession(true).setAttribute(SESSION_KEY, locale == null ? StoreLocale.PT_BR : locale);
    }
}
