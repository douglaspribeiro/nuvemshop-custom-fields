package br.com.nuvemcustomfields.service;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

final class MailTestSupport {
    private MailTestSupport() { }

    static MimeMessage message() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    static String body(MimeMessage message, String contentType) throws Exception {
        message.saveChanges();
        String body = findBody(message, contentType);
        if (body == null) throw new AssertionError("Missing email part: " + contentType);
        return body;
    }

    private static String findBody(Part part, String contentType) throws Exception {
        if (part.isMimeType(contentType)) return (String) part.getContent();
        if (part.isMimeType("multipart/*")) {
            var multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String body = findBody(multipart.getBodyPart(i), contentType);
                if (body != null) return body;
            }
        }
        return null;
    }
}
