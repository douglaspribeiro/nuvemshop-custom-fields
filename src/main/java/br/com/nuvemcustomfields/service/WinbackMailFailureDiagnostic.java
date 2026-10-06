package br.com.nuvemcustomfields.service;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;

/** Reads nested SMTP failures while exposing only fixed messages and numeric response codes. */
public record WinbackMailFailureDiagnostic(String reason, Integer smtpCode, String detail) {
    public static WinbackMailFailureDiagnostic inspect(Throwable failure) {
        var pending = new ArrayDeque<Throwable>();
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        pending.add(failure);
        Integer code = null;
        WinbackMailFailureDiagnostic known = null;
        while (!pending.isEmpty() && visited.size() < 64) {
            var current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (current instanceof SMTPSendFailedException smtp) code = smtp.getReturnCode();
            if (current instanceof SMTPAddressFailedException smtp) code = smtp.getReturnCode();
            if (code != null && (code < 400 || code > 599)) code = null;
            var classified = classify(current);
            if (classified != null) known = classified;
            if (current.getCause() != null) pending.add(current.getCause());
            if (current instanceof MessagingException mail && mail.getNextException() != null)
                pending.add(mail.getNextException());
            if (current instanceof MailSendException mail)
                for (var nested : mail.getMessageExceptions()) if (nested != null) pending.add(nested);
        }
        if (known != null) return new WinbackMailFailureDiagnostic(known.reason(), code, known.detail());
        return new WinbackMailFailureDiagnostic("UNKNOWN", code,
                code == null ? "" : "O servidor SMTP retornou o código " + code + ". Consulte o diagnóstico nos logs.");
    }

    private static WinbackMailFailureDiagnostic classify(Throwable failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage().toLowerCase(Locale.ROOT);
        if ((message.contains("configuration set") && message.contains("does not exist"))
                || message.contains("configurationsetdoesnotexist"))
            return reason("CONFIGURATION_SET_NOT_FOUND", "O SES não encontrou o Configuration Set. Confira AWS_SES_CONFIGURATION_SET na mesma conta e região do SMTP.");
        if (message.contains("email address is not verified"))
            return reason("IDENTITY_NOT_VERIFIED", "O SES rejeitou uma identidade de e-mail não verificada. Confira o remetente e, se a conta estiver em sandbox, o destinatário na região do SMTP.");
        if (message.contains("access denied") || message.contains("not authorized to perform"))
            return reason("ACCESS_DENIED", "O SES negou a permissão de envio. Confira ses:SendRawEmail nas permissões das credenciais SMTP.");
        if (message.contains("daily message quota exceeded") || message.contains("maximum sending rate exceeded"))
            return reason("SENDING_LIMIT", "O SES informou que o limite de envio foi atingido.");
        if (failure instanceof MailAuthenticationException || failure instanceof AuthenticationFailedException)
            return reason("AUTHENTICATION_FAILED", "A autenticação SMTP falhou. Confira as credenciais SMTP e a região do SES.");
        if (failure instanceof SocketTimeoutException)
            return reason("TIMEOUT", "A conexão SMTP excedeu o tempo de espera; isso não confirma se o e-mail foi aceito.");
        if (failure instanceof ConnectException || failure instanceof UnknownHostException)
            return reason("CONNECTION_FAILED", "Não foi possível conectar ao SMTP. Confira o host, a porta e a conectividade.");
        if (failure instanceof SSLException)
            return reason("TLS_FAILED", "A conexão segura com o SMTP falhou. Confira a configuração TLS e os certificados.");
        return null;
    }

    private static WinbackMailFailureDiagnostic reason(String reason, String detail) {
        return new WinbackMailFailureDiagnostic(reason, null, detail);
    }
}
