package br.com.nuvemcustomfields.dto;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.i18n.StoreLocale;

/**
 * `locale` acompanha o estilo porque o script de checkout ja consome este endpoint e
 * precisa do idioma da loja para o titulo do bloco: um segundo request so para isso
 * dobraria as chamadas do comprador.
 */
public record PersonalizationStyleResponse(
        String productTextColor,
        String checkoutTextColor,
        String cartTextColor,
        String locale
) {

    public static PersonalizationStyleResponse from(Store store) {
        if (store == null) {
            return empty();
        }
        return new PersonalizationStyleResponse(
                store.getProductTextColor(),
                store.getCheckoutTextColor(),
                store.getCartTextColor(),
                StoreLocale.tagFor(store.getStoreCountryCode())
        );
    }

    public static PersonalizationStyleResponse empty() {
        return new PersonalizationStyleResponse(null, null, null, StoreLocale.PT_BR.toLanguageTag());
    }
}
