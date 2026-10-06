package br.com.nuvemcustomfields.service;

import jakarta.mail.MessagingException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

import java.net.SocketTimeoutException;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class WinbackMailFailureDiagnosticTest {
    private SMTPSendFailedException smtp(String message, int code) {
        return new SMTPSendFailedException("DATA", code, message, null, null, null, null);
    }

    @Test void findsConfigurationSetRejectionInsideSpringFailedMessages() {
        var failure = new MailSendException("private-password", null, Map.of(new Object(),
                smtp("554 Configuration set private-set does not exist", 554)));
        var diagnostic = WinbackMailFailureDiagnostic.inspect(failure);
        assertThat(diagnostic.reason()).isEqualTo("CONFIGURATION_SET_NOT_FOUND");
        assertThat(diagnostic.smtpCode()).isEqualTo(554);
        assertThat(diagnostic.detail()).contains("AWS_SES_CONFIGURATION_SET", "região")
                .doesNotContain("private-password", "private-set");
    }

    @Test void findsIdentityRejectionThroughMessagingNextException() {
        var failure = new MessagingException("private-envelope");
        failure.setNextException(smtp("554 Message rejected: Email address is not verified: private@example.test", 554));
        var diagnostic = WinbackMailFailureDiagnostic.inspect(new MailSendException("failed", failure));
        assertThat(diagnostic.reason()).isEqualTo("IDENTITY_NOT_VERIFIED");
        assertThat(diagnostic.smtpCode()).isEqualTo(554);
        assertThat(diagnostic.detail()).contains("sandbox").doesNotContain("private@example.test", "private-envelope");
    }

    @Test void timeoutDoesNotClaimTheMessageWasRejectedOrAllowAutomaticRetry() {
        var diagnostic = WinbackMailFailureDiagnostic.inspect(new MailSendException("failed", new SocketTimeoutException("private-host")));
        assertThat(diagnostic.reason()).isEqualTo("TIMEOUT");
        assertThat(diagnostic.detail()).contains("não confirma se o e-mail foi aceito").doesNotContain("private-host");
    }

    @Test void unknownResponseExposesOnlyItsNumericCode() {
        var diagnostic = WinbackMailFailureDiagnostic.inspect(smtp("private-password recipient@example.test", 451));
        assertThat(diagnostic.reason()).isEqualTo("UNKNOWN");
        assertThat(diagnostic.smtpCode()).isEqualTo(451);
        assertThat(diagnostic.detail()).contains("451").doesNotContain("private-password", "recipient@example.test");
    }

    @Test void authorizationFailureDoesNotExposeAccountOrIdentity() {
        var diagnostic = WinbackMailFailureDiagnostic.inspect(smtp("554 Access denied: User private-arn is not authorized to perform ses:SendRawEmail", 554));
        assertThat(diagnostic.reason()).isEqualTo("ACCESS_DENIED");
        assertThat(diagnostic.detail()).contains("ses:SendRawEmail").doesNotContain("private-arn");
    }

    @Test void causeCyclesDoNotHangTheDiagnostic() {
        var first = new RuntimeException("private-first");
        var second = new RuntimeException("private-second", first);
        first.initCause(second);
        assertThat(WinbackMailFailureDiagnostic.inspect(first).reason()).isEqualTo("UNKNOWN");
    }
}
