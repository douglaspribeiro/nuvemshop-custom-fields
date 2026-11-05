package br.com.nuvemcustomfields.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrackedMailSenderTest {
    @Test void doesNotReachSmtpWhenAttemptCannotBeRecorded() throws Exception {
        var history=mock(StoreEmailHistoryService.class);var smtp=mock(Runnable.class);
        when(history.begin(any())).thenThrow(new IllegalStateException("private database details"));
        var transport=new TrackedMailSender(history) { @Override protected void deliver(MimeMessage m) { smtp.run(); } };
        assertThatThrownBy(()->transport.send(MailTestSupport.message())).isInstanceOf(MailPreparationException.class)
            .hasMessageNotContaining("private database details");
        verifyNoInteractions(smtp);verify(history,never()).finish(any(),anyBoolean());
    }
    @Test void successfulSmtpIsNotReportedAsFailureIfHistoryUpdateFails() throws Exception {
        var history=mock(StoreEmailHistoryService.class);var smtp=mock(Runnable.class);
        when(history.begin(any())).thenReturn("attempt-1");doThrow(new IllegalStateException()).when(history).finish("attempt-1",true);
        var transport=new TrackedMailSender(history) { @Override protected void deliver(MimeMessage m) { smtp.run(); } };
        assertThatCode(()->transport.send(MailTestSupport.message())).doesNotThrowAnyException();
        verify(smtp,times(1)).run();verify(history).finish("attempt-1",true);
    }
    @Test void uncertainSmtpFailureIsPropagatedOnceEvenIfHistoryUpdateFails() throws Exception {
        var history=mock(StoreEmailHistoryService.class);var smtp=mock(Runnable.class);
        when(history.begin(any())).thenReturn("attempt-2");doThrow(new IllegalStateException()).when(history).finish("attempt-2",false);
        doThrow(new MailSendException("smtp failure")).when(smtp).run();
        var transport=new TrackedMailSender(history) { @Override protected void deliver(MimeMessage m) { smtp.run(); } };
        assertThatThrownBy(()->transport.send(MailTestSupport.message())).isInstanceOf(MailSendException.class);
        verify(smtp,times(1)).run();verify(history).finish("attempt-2",false);
    }
}
