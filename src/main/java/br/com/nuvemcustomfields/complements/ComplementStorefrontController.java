package br.com.nuvemcustomfields.complements;

import br.com.nuvemcustomfields.entity.PersonalizationRule;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.i18n.StoreLocale;
import br.com.nuvemcustomfields.properties.FeatureAccessProperties;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.StorefrontCache;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class ComplementStorefrontController {
    private final FeatureAccessProperties features;
    private final StoreRepository stores;
    private final PersonalizationRuleRepository rules;
    private final ComplementOffers offers;
    private final StorefrontCache cache;

    public ComplementStorefrontController(FeatureAccessProperties features, StoreRepository stores,
            PersonalizationRuleRepository rules, ComplementOffers offers, StorefrontCache cache) {
        this.features = features;
        this.stores = stores;
        this.rules = rules;
        this.offers = offers;
        this.cache = cache;
    }

    public record Catalog(String locale, String currency, List<Item> items) {}
    public record Item(long productId, long variantId, String label, String price, String imageUrl, Integer stock, String productUrl, boolean singleVariant, ComplementOffers.Appearance appearance) {}

    @GetMapping("/public/stores/{storeId}/products/{productId}/complements")
    public Catalog get(@PathVariable long storeId, @PathVariable long productId) {
        if (productId <= 0 || !features.allows(storeId, FeatureAccessProperties.Feature.PRODUCT_COMPLEMENTS)) {
            return empty();
        }
        return cache.get("complements", storeId, productId, () -> load(storeId, productId));
    }

    private Catalog load(long storeId, long productId) {
        Store store = stores.findActiveByStoreId(storeId).orElse(null);
        if (store == null) return empty();
        return new Catalog(StoreLocale.tagFor(store.getStoreCountryCode()), store.getStoreCurrency(),
                offers.published(storeId, productId).stream()
                        .map(offer -> loadItem(store, offer)).filter(Objects::nonNull).toList());
    }

    private Item loadItem(Store store, ComplementOffers.PublishedOffer offer) {
        if (rules.findWithFieldsByStoreIdAndProductId(store.getStoreId(), offer.productId())
                .filter(PersonalizationRule::isEnabled).filter(rule -> !rule.getFields().isEmpty()).isPresent()) {
            return null;
        }
        JsonNode product;
        try {
            product = offers.product(store, offer.productId());
        } catch (ComplementOffers.Invalid ex) {
            return null;
        }
        String visibility = product.path("visibility").asText();
        if ("hidden".equals(visibility) || (visibility.isEmpty() && !product.path("published").asBoolean(true))) {
            return null;
        }
        return StreamSupport.stream(product.path("variants").spliterator(), false)
                .filter(variant -> variant.path("id").asLong() == offer.variantId())
                .map(variant -> toItem(offer, product, variant)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private Item toItem(ComplementOffers.PublishedOffer offer, JsonNode product, JsonNode variant) {
        Integer stock = variant.path("stock_management").asBoolean() && variant.path("stock").isIntegralNumber()
                ? variant.path("stock").intValue() : null;
        if (stock != null && stock <= 0) return null;
        String price = variant.path("promotional_price").asText("");
        if (price.isBlank()) price = variant.path("price").asText("");
        if (price.isBlank()) return null;
        String image = product.path("images").path(0).path("src").asText("");
        return new Item(offer.productId(), offer.variantId(), offer.label(), price,
                offer.appearance().showImage() && image.startsWith("https://") ? image : null, stock,
                product.path("canonical_url").asText(null), product.path("variants").size() == 1, offer.appearance());
    }

    private static Catalog empty() {
        return new Catalog("pt-BR", null, List.of());
    }
}
