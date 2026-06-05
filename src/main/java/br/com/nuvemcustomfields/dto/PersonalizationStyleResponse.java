package br.com.nuvemcustomfields.dto;

import br.com.nuvemcustomfields.entity.Store;

public record PersonalizationStyleResponse(String productTextColor, String checkoutTextColor) {

    public static PersonalizationStyleResponse from(Store store) {
        if (store == null) {
            return empty();
        }
        return new PersonalizationStyleResponse(
                store.getProductTextColor(),
                store.getCheckoutTextColor()
        );
    }

    public static PersonalizationStyleResponse empty() {
        return new PersonalizationStyleResponse(null, null);
    }
}
