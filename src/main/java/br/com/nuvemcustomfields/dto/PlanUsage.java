package br.com.nuvemcustomfields.dto;

import br.com.nuvemcustomfields.entity.PlanType;

public record PlanUsage(
        PlanType plan,
        long productsUsed,
        long productLimit,
        long fieldsUsed,
        long fieldLimit
) {

    public boolean unlimitedProducts() {
        return productLimit < 0;
    }

    public boolean unlimitedFields() {
        return fieldLimit < 0;
    }
}
