package br.com.nuvemcustomfields.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private static final org.slf4j.Logger REQUEST_LOGGER = LoggerFactory.getLogger("request-mdc-test");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void addsOneGeneratedUuidToEveryLogInsideTheRequest() throws Exception {
        ListAppender<ILoggingEvent> appender = attachAppender();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestLoggingFilter().doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            REQUEST_LOGGER.info("controller.work");
            REQUEST_LOGGER.info("service.work");
        });

        String requestId = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(UUID.fromString(requestId)).isEqualTo(UUID.fromString(requestId));
        assertThat(request.getAttribute(RequestLoggingFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo(requestId);
        assertThat(appender.list)
                .extracting(event -> event.getMDCPropertyMap().get(RequestLoggingFilter.REQUEST_ID_MDC_KEY))
                .containsExactly(requestId, requestId);
        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    void propagatesOnlyValidIncomingUuidAndRestoresNestedContext() throws Exception {
        ListAppender<ILoggingEvent> appender = attachAppender();
        String incomingId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, incomingId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(RequestLoggingFilter.REQUEST_ID_MDC_KEY, "outer-request");

        new RequestLoggingFilter().doFilter(request, response, (ignoredRequest, ignoredResponse) -> REQUEST_LOGGER.info("inside"));

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo(incomingId);
        assertThat(appender.list.getFirst().getMDCPropertyMap())
                .containsEntry(RequestLoggingFilter.REQUEST_ID_MDC_KEY, incomingId);
        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY)).isEqualTo("outer-request");
    }

    @Test
    void replacesAnInvalidIncomingRequestIdWithAGeneratedUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "not-a-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestLoggingFilter().doFilter(request, response, (ignoredRequest, ignoredResponse) -> REQUEST_LOGGER.info("inside"));

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isNotEqualTo("not-a-uuid");
        assertThat(UUID.fromString(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER))).isNotNull();
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger("request-mdc-test");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
