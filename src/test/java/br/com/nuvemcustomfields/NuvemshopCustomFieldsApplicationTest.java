package br.com.nuvemcustomfields;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NuvemshopCustomFieldsApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void actuatorHealthIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")));
    }

    @Test
    void rootRedirectUsesRelativeLocation() throws Exception {
        mockMvc.perform(get("/")
                        .header("Host", "campos-personalizados.wzhub.pro"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/admin/embedded"));
    }

    @Test
    void adminRedirectWithoutSessionUsesEmbeddedBootstrapLocation() throws Exception {
        mockMvc.perform(get("/admin")
                        .header("Host", "campos-personalizados.wzhub.pro"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/admin/embedded"));
    }

    @Test
    void embeddedAdminBootstrapIsAvailableWithoutSession() throws Exception {
        mockMvc.perform(get("/admin/embedded"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-mode=\"bootstrap\"")));
    }
}
