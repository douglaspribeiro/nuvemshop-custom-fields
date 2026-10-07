package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "legal.support-email=contato@wzhub.pro",
        "nuvemshop.billing.prices.BR.currency=BRL", "nuvemshop.billing.prices.BR.premium-amount=19.99",
        "nuvemshop.billing.prices.BR.premium-plus-amount=29.99", "nuvemshop.billing.prices.BR.premium-ultra-amount=59.90",
        "nuvemshop.billing.prices.AR.currency=ARS", "nuvemshop.billing.prices.AR.premium-amount=5599",
        "nuvemshop.billing.prices.AR.premium-plus-amount=8399", "nuvemshop.billing.prices.AR.premium-ultra-amount=16799",
        "nuvemshop.billing.prices.CL.currency=CLP", "nuvemshop.billing.prices.CL.premium-amount=4199",
        "nuvemshop.billing.prices.CL.premium-plus-amount=6299", "nuvemshop.billing.prices.CL.premium-ultra-amount=12599",
        "nuvemshop.billing.prices.MX.currency=MXN", "nuvemshop.billing.prices.MX.premium-amount=99",
        "nuvemshop.billing.prices.MX.premium-plus-amount=149", "nuvemshop.billing.prices.MX.premium-ultra-amount=299",
        "nuvemshop.billing.prices.CO.currency=COP", "nuvemshop.billing.prices.CO.premium-amount=13146",
        "nuvemshop.billing.prices.CO.premium-plus-amount=19723", "nuvemshop.billing.prices.CO.premium-ultra-amount=39446",
        "nuvemshop.billing.prices.US.currency=USD", "nuvemshop.billing.prices.US.premium-amount=4.99",
        "nuvemshop.billing.prices.US.premium-plus-amount=7.49", "nuvemshop.billing.prices.US.premium-ultra-amount=14.99"
})
@AutoConfigureMockMvc
class PublicMarketPagesTest {
    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "BR|R$ 19,99|R$ 29,99|R$ 59,90|Efí",
            "AR|USD 4,99|USD 7,49|USD 14,99|Creem",
            "CL|USD 4,99|USD 7,49|USD 14,99|Creem",
            "MX|USD 4.99|USD 7.49|USD 14.99|Creem",
            "CO|USD 4,99|USD 7,49|USD 14,99|Creem",
            "FR|USD 4.99|USD 7.49|USD 14.99|Creem",
            "US|USD 4.99|USD 7.49|USD 14.99|Creem"
    })
    void showsConfiguredPricesAndProviderForVisitorCountry(String country, String essential, String pro,
                                                            String ultra, String provider) throws Exception {
        mvc.perform(get("/precos/").header("CF-IPCountry", country))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(content().string(containsString(essential))).andExpect(content().string(containsString(pro)))
                .andExpect(content().string(containsString(ultra))).andExpect(content().string(containsString(provider)));
        mvc.perform(get("/").header("CF-IPCountry", country)).andExpect(status().isOk())
                .andExpect(content().string(containsString(essential)));
        mvc.perform(get("/termos/").header("CF-IPCountry", country)).andExpect(status().isOk())
                .andExpect(content().string(containsString(provider)))
                .andExpect(content().string(not(containsString(provider.equals("Efí") ? "Creem" : "Efí"))));
    }

    @Test void manualSelectionPersistsAcrossPagesAndDoesNotChangeStoreCountry() throws Exception {
        var store = new Store(); store.setStoreId(996655999L); store.setAccessToken("test"); store.setStoreCountryCode("BR");
        stores.saveAndFlush(store);
        var session = new MockHttpSession(); session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, store.getStoreId());
        mvc.perform(get("/precos/").session(session).header("CF-IPCountry", "AR"))
                .andExpect(content().string(containsString("R$ 19,99")));
        mvc.perform(get("/precos/").session(session).param("country", "mx").header("CF-IPCountry", "BR"))
                .andExpect(content().string(containsString("USD 4.99")));
        mvc.perform(get("/termos/").session(session).header("CF-IPCountry", "BR"))
                .andExpect(content().string(containsString("Creem")));
        mvc.perform(get("/").session(session).param("country", "invalid").header("CF-IPCountry", "BR"))
                .andExpect(content().string(containsString("USD 4.99")));
        assertThat(stores.findByStoreId(store.getStoreId()).orElseThrow().getStoreCountryCode()).isEqualTo("BR");
    }

    @Test void fallsBackWithoutGeolocationAndShowsSupportAndProBadge() throws Exception {
        mvc.perform(get("/precos/")).andExpect(content().string(containsString("R$ 19,99")))
                .andExpect(content().string(containsString("class=\"pricing-card featured\" aria-labelledby=\"pro-plan-title\"")))
                .andExpect(content().string(not(containsString("class=\"pricing-card featured\" aria-labelledby=\"essential-plan-title\""))));
        mvc.perform(get("/precos/").header("CF-IPCountry", "XX").header("Accept-Language", "es-CL"))
                .andExpect(content().string(containsString("USD 4,99")));
        for (var path : new String[]{"/contato/", "/termos/", "/support/"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("mailto:contato@wzhub.pro")));
        }
    }
}
