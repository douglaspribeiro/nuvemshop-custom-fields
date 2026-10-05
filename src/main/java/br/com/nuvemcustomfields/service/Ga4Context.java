package br.com.nuvemcustomfields.service;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Only anonymous Google tag identifiers, never payer or OAuth parameters. */
public record Ga4Context(String clientId, String sessionId) {
    public static Ga4Context current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes))
            return new Ga4Context(null, null);
        return validated(attributes.getRequest().getParameter("gaClientId"),
                attributes.getRequest().getParameter("gaSessionId"));
    }
    static Ga4Context validated(String client, String session) {
        if (client == null || !client.matches("[0-9]{1,20}\\.[0-9]{1,20}")) return new Ga4Context(null, null);
        return new Ga4Context(client, session != null && session.matches("[0-9]{1,20}") ? session : null);
    }
}
