package br.com.nuvemcustomfields.dto;

import br.com.nuvemcustomfields.entity.Store;

public record PersonalizationStyleResponse(String productTextColor, String checkoutTextColor, String cartTextColor) {

    public static PersonalizationStyleResponse from(Store store) {
        if (store == null) {
            return empty();
        }
        return new PersonalizationStyleResponse(
                store.getProductTextColor(),
                store.getCheckoutTextColor(),
                store.getCartTextColor()
        );
    }

    public static PersonalizationStyleResponse empty() {
        return new PersonalizationStyleResponse(null, null, null);
    }
}
