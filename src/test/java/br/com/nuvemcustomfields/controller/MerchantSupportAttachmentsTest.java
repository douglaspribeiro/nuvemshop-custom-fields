package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.MerchantInterfacePreference.Choice;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.MerchantInterfacePreferenceRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.repository.SupportAttachmentRepository;
import br.com.nuvemcustomfields.repository.SupportMessageRepository;
import br.com.nuvemcustomfields.repository.SupportTicketRepository;
import br.com.nuvemcustomfields.service.MerchantInterfaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "features.allowed=788210:merchant-interface-preview,788210:product-complements")
@AutoConfigureMockMvc
@Transactional
class MerchantSupportAttachmentsTest {
    private static final int FILE_LIMIT = 5 * 1024 * 1024;

    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;
    @Autowired SupportTicketRepository tickets;
    @Autowired SupportMessageRepository messages;
    @Autowired SupportAttachmentRepository attachments;
    @Autowired MerchantInterfaceService preferences;
    @Autowired MerchantInterfacePreferenceRepository preferenceRepository;

    @Test
    void opensAndRepliesWithAttachmentsThatOnlyTheirStoreAndBackofficeCanDownload() throws Exception {
        long storeId = 788210L;
        MockHttpSession owner = merchantSession(storeId);
        MockHttpSession anotherStore = merchantSession(788211L);
        MockHttpSession operator = new MockHttpSession();
        operator.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
        byte[] bytes = "arquivo de diagnóstico".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        mvc.perform(get("/support/").session(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("name=\"attachment\""))));
        assertThat(preferences.choose(storeId, Choice.PREVIEW)).isTrue();
        mvc.perform(get("/support/").param("app", "complements").session(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"attachment\"")))
                .andExpect(content().string(containsString("name=\"app\" value=\"complements\"")));

        mvc.perform(multipart("/support/tickets")
                        .file(new MockMultipartFile("attachment", "..\\relatório.txt", "text/plain", bytes))
                        .param("subject", "Preciso de ajuda")
                        .param("message", "Meu chamado")
                        .param("app", "complements")
                        .session(owner))
                .andExpect(status().is3xxRedirection());
        long ticket = tickets.findByStoreIdOrderByLastMessageAtDesc(storeId).getFirst().getId();
        var opening = attachments.summaries(ticket).getFirst();
        String merchantTicket = "/support/tickets/" + ticket;
        String merchantDownload = merchantTicket + "/attachments/" + opening.getId();
        String backofficeDownload = "/backoffice/support/" + ticket + "/attachments/" + opening.getId();

        mvc.perform(get(merchantTicket).param("app", "complements").session(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"attachment\"")))
                .andExpect(content().string(containsString("relatório.txt")))
                .andExpect(content().string(containsString(merchantDownload)));
        mvc.perform(get(merchantDownload).session(owner))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(bytes))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment;")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        mvc.perform(get(merchantDownload).session(anotherStore)).andExpect(status().isNotFound());
        mvc.perform(get(merchantDownload)).andExpect(status().isNotFound());
        mvc.perform(get("/support/tickets/0/attachments/" + opening.getId()).session(owner))
                .andExpect(status().isNotFound());
        mvc.perform(get(backofficeDownload)).andExpect(redirectedUrl("/backoffice/login"));
        mvc.perform(get(backofficeDownload).session(operator))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes));

        mvc.perform(multipart(merchantTicket + "/messages")
                        .file(new MockMultipartFile("attachment", "detalhes.bin", "application/octet-stream", bytes))
                        .param("message", "Mais detalhes")
                        .session(owner))
                .andExpect(redirectedUrl(merchantTicket));
        assertThat(attachments.summaries(ticket)).extracting(SupportAttachmentRepository.Summary::getFilename)
                .containsExactly("relatório.txt", "detalhes.bin");
        mvc.perform(get("/backoffice/support/" + ticket).session(operator))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(backofficeDownload)))
                .andExpect(content().string(containsString("detalhes.bin")));

        assertThat(preferences.choose(storeId, Choice.CLASSIC)).isTrue();
        mvc.perform(get(merchantTicket).session(owner))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("name=\"attachment\""))))
                .andExpect(content().string(containsString(merchantDownload)));
        mvc.perform(get(merchantDownload).session(owner))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes));

        messages.deleteById(opening.getMessageId());
        messages.flush();
        mvc.perform(get(merchantDownload).session(owner)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"788210,", "788210,CLASSIC", "788211,PREVIEW"})
    void rejectsUploadsWithoutBothGrantAndPreviewChoice(long storeId, Choice choice) throws Exception {
        MockHttpSession owner = merchantSession(storeId);
        if (choice != null) {
            preferenceRepository.setChoice(storeId, choice.name());
        }
        MockMultipartFile upload = new MockMultipartFile("attachment", "diagnostico.txt", "text/plain", new byte[]{1});
        String error = "O envio de anexos está disponível na prévia da interface para lojas participantes.";

        mvc.perform(multipart("/support/tickets").file(upload)
                        .param("subject", "Ajuda").param("message", "Meu chamado").session(owner))
                .andExpect(redirectedUrl("/support/"))
                .andExpect(flash().attribute("error", error))
                .andExpect(flash().attribute("supportSubject", "Ajuda"))
                .andExpect(flash().attribute("supportDraft", "Meu chamado"));
        assertThat(tickets.findByStoreIdOrderByLastMessageAtDesc(storeId)).isEmpty();

        mvc.perform(post("/support/tickets").param("subject", "Ajuda")
                        .param("message", "Meu chamado").session(owner))
                .andExpect(status().is3xxRedirection());
        long ticket = tickets.findByStoreIdOrderByLastMessageAtDesc(storeId).getFirst().getId();
        String ticketUrl = "/support/tickets/" + ticket;

        mvc.perform(multipart(ticketUrl + "/messages").file(upload)
                        .param("message", "Minha resposta").session(owner))
                .andExpect(redirectedUrl(ticketUrl))
                .andExpect(flash().attribute("error", error))
                .andExpect(flash().attribute("supportDraft", "Minha resposta"));
        assertThat(messages.findByTicketIdOrderByCreatedAtAsc(ticket)).hasSize(1);

        mvc.perform(post(ticketUrl + "/messages").param("message", "Minha resposta").session(owner))
                .andExpect(redirectedUrl(ticketUrl))
                .andExpect(flash().attributeExists("message"));
        assertThat(messages.findByTicketIdOrderByCreatedAtAsc(ticket)).hasSize(2);
        assertThat(attachments.summaries(ticket)).isEmpty();
    }

    @Test
    void acceptsFiveMebibytesAndRejectsLargerFilesWithoutCreatingTicketsOrReplies() throws Exception {
        long storeId = 788210L;
        MockHttpSession owner = merchantSession(storeId);
        assertThat(preferences.choose(storeId, Choice.PREVIEW)).isTrue();
        MockMultipartFile max = new MockMultipartFile("attachment", "diagnostico.bin", null, new byte[FILE_LIMIT]);
        MockMultipartFile tooLarge = new MockMultipartFile("attachment", "diagnostico.bin", null, new byte[FILE_LIMIT + 1]);

        mvc.perform(multipart("/support/tickets").file(tooLarge)
                        .param("subject", "Ajuda").param("message", "Meu chamado").session(owner))
                .andExpect(redirectedUrl("/support/"))
                .andExpect(flash().attribute("error", "O arquivo deve ter até 5 MB."))
                .andExpect(flash().attribute("supportDraft", "Meu chamado"));
        assertThat(tickets.findByStoreIdOrderByLastMessageAtDesc(storeId)).isEmpty();

        mvc.perform(multipart("/support/tickets").file(max)
                        .param("subject", "Ajuda").param("message", "Meu chamado").session(owner))
                .andExpect(status().is3xxRedirection());
        long ticket = tickets.findByStoreIdOrderByLastMessageAtDesc(storeId).getFirst().getId();
        assertThat(attachments.summaries(ticket)).hasSize(1);

        mvc.perform(multipart("/support/tickets/" + ticket + "/messages").file(tooLarge)
                        .param("message", "Minha resposta").session(owner))
                .andExpect(redirectedUrl("/support/tickets/" + ticket))
                .andExpect(flash().attribute("error", "O arquivo deve ter até 5 MB."));
        assertThat(messages.findByTicketIdOrderByCreatedAtAsc(ticket)).hasSize(1);
        assertThat(attachments.summaries(ticket)).hasSize(1);
    }

    private MockHttpSession merchantSession(long storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        store.setAccessToken("test");
        stores.save(store);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, storeId);
        return session;
    }
}
