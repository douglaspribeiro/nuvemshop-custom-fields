package br.com.nuvemcustomfields.service;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** Shared, email-client-friendly visual system for merchant-facing messages. */
@Component
public class BrandedEmailTemplate {
    public record Rendered(String html, String text) { }

    public Rendered render(String headline, String body, String actionLabel, String actionUrl, String footer) {
        String safeHeadline = escape(headline);
        String safeBody = escape(body).replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>\n");
        StringBuilder html = new StringBuilder(1600);
        html.append("<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>")
            .append("<body style=\"margin:0;padding:0;background:#f3f6f8;font-family:Arial,Helvetica,sans-serif;color:#243447\">")
            .append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0\">").append(safeHeadline).append("</div>")
            .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f3f6f8;padding:32px 12px\"><tr><td align=\"center\">")
            .append("<table role=\"presentation\" width=\"600\" cellspacing=\"0\" cellpadding=\"0\" style=\"width:100%;max-width:600px;background:#fff;border-radius:16px;overflow:hidden\">")
            .append("<tr><td style=\"padding:24px 32px;background:#123b4a\"><span style=\"font-size:18px;font-weight:bold;letter-spacing:.2px;color:#fff\">Campos <span style=\"color:#86e0c2\">Personalizados</span></span></td></tr>")
            .append("<tr><td style=\"padding:36px 36px 20px\"><h1 style=\"margin:0 0 20px;font-size:25px;line-height:1.3;color:#123b4a\">").append(safeHeadline).append("</h1>")
            .append("<div style=\"font-size:16px;line-height:1.75;color:#405465\">").append(safeBody).append("</div>");
        if (actionLabel != null && !actionLabel.isBlank() && actionUrl != null && !actionUrl.isBlank()) {
            html.append("<div style=\"padding:28px 0 12px\"><a href=\"").append(escape(actionUrl))
                .append("\" style=\"display:inline-block;padding:14px 24px;background:#087e70;color:#fff;text-decoration:none;font-size:16px;font-weight:bold;border-radius:8px\">")
                .append(escape(actionLabel)).append("</a></div>");
        }
        if (footer != null && !footer.isBlank()) html.append("<p style=\"margin:28px 0 0;padding-top:18px;border-top:1px solid #e8eef0;font-size:13px;line-height:1.6;color:#71808b\">")
            .append(escape(footer).replace("\n", "<br>")).append("</p>");
        html.append("</td></tr><tr><td style=\"padding:18px 36px;background:#f8fafb;color:#83919a;font-size:12px;line-height:1.5\">Uma mensagem da equipe Campos Personalizados</td></tr>")
            .append("</table></td></tr></table></body></html>");

        StringBuilder text = new StringBuilder();
        if (headline != null && !headline.isBlank()) text.append(headline.strip()).append("\n\n");
        if (body != null) text.append(body.strip());
        if (actionLabel != null && !actionLabel.isBlank() && actionUrl != null && !actionUrl.isBlank())
            text.append("\n\n").append(actionLabel).append(": ").append(actionUrl);
        if (footer != null && !footer.isBlank()) text.append("\n\n").append(footer.strip());
        text.append("\n\nEquipe Campos Personalizados");
        return new Rendered(html.toString(), text.toString());
    }

    private static String escape(String value) { return HtmlUtils.htmlEscape(value == null ? "" : value); }
}
