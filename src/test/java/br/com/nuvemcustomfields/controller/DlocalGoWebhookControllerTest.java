package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.DlocalGoWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DlocalGoWebhookControllerTest {
    @Test void forwardsOriginalBodyAuthorizationAndReferenceOnBothPaths() throws Exception {
        var service=mock(DlocalGoWebhookService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new DlocalGoWebhookController(service)).build();
        for (String path:new String[]{"/webhooks/dlocal-go","/prod/webhooks/dlocal-go"})
            mvc.perform(post(path).queryParam("reference","ref").header("Authorization","signature")
                    .contentType("application/json").content("{ \"payment_id\": \"DP-1\" }"))
                    .andExpect(status().isOk());
        verify(service,times(2)).receive("{ \"payment_id\": \"DP-1\" }","signature","ref");
    }
    @Test void rejectsInvalidSignatureAndMissingReference() throws Exception {
        var service=mock(DlocalGoWebhookService.class);
        doThrow(new IllegalArgumentException("Invalid signature")).when(service).receive("{}",null,"ref");
        var mvc=MockMvcBuilders.standaloneSetup(new DlocalGoWebhookController(service)).build();
        mvc.perform(post("/webhooks/dlocal-go").queryParam("reference","ref").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/webhooks/dlocal-go").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }
}
