package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.dto.ProductPage;
import br.com.nuvemcustomfields.dto.ProductSummary;
import br.com.nuvemcustomfields.entity.PersonalizationRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminProductOrderingTest {

    @Test
    void configuredProductsAppearBeforeUnconfiguredEvenWhenAbsentFromApiFirstPage() {
        ProductPage page = new ProductPage(List.of(
                new ProductSummary(1L, "Sem campos"),
                new ProductSummary(2L, "Com campos")
        ), 1, 50, 100, true, null);
        PersonalizationRule configured = rule(2L, "Com campos");
        PersonalizationRule fromLaterPage = rule(90L, "Outro configurado");

        assertThat(AdminController.prioritizedProducts(page, List.of(configured, fromLaterPage), List.of(2L, 90L)))
                .extracting(ProductSummary::id)
                .containsExactly(2L, 90L, 1L);
    }

    @Test
    void searchKeepsApiResultsAndMovesConfiguredMatchesFirst() {
        ProductPage page = new ProductPage(List.of(
                new ProductSummary(1L, "Primeiro"),
                new ProductSummary(2L, "Segundo")
        ), 1, 50, 2, false, "teste");

        assertThat(AdminController.prioritizedProducts(page, List.of(rule(90L, "Fora da busca")), List.of(2L, 90L)))
                .extracting(ProductSummary::id)
                .containsExactly(2L, 1L);
    }

    @Test
    void sortsConfiguredAndOtherProductsAlphabetically() {
        ProductPage page = new ProductPage(List.of(
                new ProductSummary(1L, "Zebra"),
                new ProductSummary(2L, "Álbum"),
                new ProductSummary(3L, "Caneca"),
                new ProductSummary(4L, "Abajur")
        ), 1, 50, 4, false, null);

        assertThat(AdminController.prioritizedProducts(page,
                List.of(rule(3L, "Caneca"), rule(2L, "Álbum")), List.of(2L, 3L)))
                .extracting(ProductSummary::id)
                .containsExactly(2L, 3L, 4L, 1L);
    }

    private PersonalizationRule rule(Long id, String name) {
        PersonalizationRule rule = new PersonalizationRule();
        rule.setProductId(id);
        rule.setProductName(name);
        return rule;
    }
}
