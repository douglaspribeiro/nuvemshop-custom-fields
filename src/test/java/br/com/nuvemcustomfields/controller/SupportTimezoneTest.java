package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.service.SupportReplyEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Base64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class SupportTimezoneTest {
    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;
    @Autowired SupportTicketRepository tickets;
    @Autowired SupportMessageRepository messages;

    @Test
    void backofficeReplyPublishesBothUploadedImages(ApplicationEvents events) throws Exception {
        Store store = new Store();
        store.setStoreId(991231L);
        store.setStoreEmail("loja@example.com");
        stores.save(store);
        SupportTicket ticket = new SupportTicket();
        ticket.setStoreId(store.getStoreId());
        ticket.setSubject("Carrinho");
        tickets.saveAndFlush(ticket);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jqCkAAAAASUVORK5CYII=");
        String url = "/backoffice/support/" + ticket.getId() + "/messages";

        mvc.perform(multipart(url)
                        .file(new MockMultipartFile("attachments", "carrito.png", "image/png", png))
                        .file(new MockMultipartFile("attachments", "checkout.png", "image/png", png))
                        .param("message", "Capturas adjuntas").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "Resposta registrada."));

        assertThat(events.stream(SupportReplyEvent.class).toList()).singleElement().satisfies(reply -> {
            assertThat(reply.recipient()).isEqualTo("loja@example.com");
            assertThat(reply.attachments()).hasSize(2).allSatisfy(attachment -> {
                assertThat(attachment.contentType()).isEqualTo("image/png");
                assertThat(attachment.bytes()).containsExactly(png);
            });
        });
    }

    @Test void exposesOriginalInstantsForBrowserConversionInBothSupportViews() throws Exception {
        Instant instant = Instant.parse("2026-10-03T15:30:00Z");
        Store store = new Store(); store.setStoreId(991230L); store.setAccessToken("test"); stores.save(store);
        SupportTicket ticket = new SupportTicket(); ticket.setStoreId(store.getStoreId()); ticket.setSubject("Fuso horário");
        ticket.setLastMessageAt(instant); tickets.save(ticket);
        SupportMessage message = new SupportMessage(); message.setTicketId(ticket.getId());
        message.setAuthorType(SupportMessageAuthor.STORE); message.setMessage("Mensagem de teste");
        ReflectionTestUtils.setField(message, "createdAt", instant); messages.saveAndFlush(message);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, store.getStoreId());
        session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
        for (String url : new String[]{"/support/", "/support/tickets/" + ticket.getId(),
                "/backoffice/support", "/backoffice/support/" + ticket.getId()}) {
            String html = mvc.perform(get(url).session(session)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(html).contains("data-local-time=\"2026-10-03T15:30:00Z\"", "/assets/local-time.js");
        }
        assertThat(messages.findById(message.getId()).orElseThrow().getCreatedAt()).isEqualTo(instant);
    }
}
