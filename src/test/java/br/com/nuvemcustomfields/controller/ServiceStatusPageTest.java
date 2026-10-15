package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.ServiceStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ServiceStatusPageTest {
    @Autowired MockMvc mvc;
    @MockitoBean ServiceStatusService service;

    @Test void publicPageRendersWithoutLoginAndHandlesNoConfiguration() throws Exception {
        when(service.current()).thenReturn(new ServiceStatusService.Snapshot("unconfigured", false, null, List.of(), List.of()));
        mvc.perform(get("/status")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/status/"));
        mvc.perform(get("/status/")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("Monitoramento em preparação")))
                .andExpect(content().string(not(containsString("Serviço operacional"))))
                .andExpect(content().string(not(containsString("??status."))));
    }

    @Test void rendersMetricsAndEventsInBothLanguagesAndDoesNotPresentStaleDataAsCurrent() throws Exception {
        var now = Instant.parse("2026-10-15T12:00:00Z");
        when(service.current()).thenReturn(new ServiceStatusService.Snapshot("up", true, now,
                List.of(new ServiceStatusService.Uptime(30, "99.9")),
                List.of(new ServiceStatusService.Event("up", now))));
        mvc.perform(get("/status/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Status indisponível no momento")))
                .andExpect(content().string(containsString("Último estado conhecido")))
                .andExpect(content().string(containsString("99.9%")))
                .andExpect(content().string(containsString("15/10/2026 09:00:00")))
                .andExpect(content().string(not(containsString("??status."))));
        mvc.perform(get("/status/").header("Accept-Language", "es-AR")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Estado del servicio")))
                .andExpect(content().string(containsString("Servicio restablecido")))
                .andExpect(content().string(not(containsString("??status."))));
    }
}
