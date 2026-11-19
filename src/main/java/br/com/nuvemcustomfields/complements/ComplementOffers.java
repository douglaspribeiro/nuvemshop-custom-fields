package br.com.nuvemcustomfields.complements;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.service.NuvemshopApiClient;
import br.com.nuvemcustomfields.service.StorefrontCache;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
public class ComplementOffers {
    public record Offer(String id, long parentId, String parentName, Long productId, Long variantId,
                        String productName, String label, boolean enabled, Appearance appearance, String state) {
        public boolean ready() { return "READY".equals(state); }
        public boolean uncertain() { return !ready(); }
    }

    public record Appearance(boolean showImage, String backgroundColor, String textColor, boolean showBorder, boolean roundedCorners) {}

    private static Appearance appearance(java.sql.ResultSet row) throws java.sql.SQLException {
        return new Appearance(row.getBoolean("show_image"), row.getString("background_color"),
                row.getString("text_color"), row.getBoolean("show_border"), row.getBoolean("rounded_corners"));
    }

    public record Variant(long id, String name, String price) {}

    public record NewProduct(String name, BigDecimal price, Integer stock, String imageUrl, boolean unlisted,
                             BigDecimal weight, BigDecimal width, BigDecimal height, BigDecimal depth) {}

    public static final class Invalid extends RuntimeException {
        Invalid(String key) { super("complements.error." + key); }
    }

    private final JdbcTemplate jdbc;
    private final NuvemshopApiClient catalog;
    private final StorefrontCache cache;

    public ComplementOffers(JdbcTemplate jdbc, NuvemshopApiClient catalog, StorefrontCache cache) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.cache = cache;
    }

    public List<Offer> list(long storeId) {
        return jdbc.query("SELECT * FROM product_complement_offers WHERE store_id=? ORDER BY created_at DESC,id",
                (rs, n) -> new Offer(rs.getString("id"), rs.getLong("parent_product_id"), rs.getString("parent_name"),
                        rs.getObject("complement_product_id", Long.class), rs.getObject("complement_variant_id", Long.class),
                        rs.getString("complement_name"), rs.getString("label"), rs.getBoolean("enabled"), appearance(rs),
                        rs.getString("creation_state")), storeId);
    }

    record PublishedOffer(long productId, long variantId, String label, Appearance appearance) {}

    List<PublishedOffer> published(long storeId, long parentId) {
        return jdbc.query("""
                SELECT complement_product_id, complement_variant_id, label, show_image, background_color, text_color, show_border, rounded_corners
                FROM product_complement_offers
                WHERE store_id=? AND parent_product_id=? AND enabled AND creation_state='READY'
                ORDER BY created_at,id
                """, (rs, n) -> new PublishedOffer(rs.getLong(1), rs.getLong(2), rs.getString(3), appearance(rs)), storeId, parentId);
    }

    public static boolean hasScope(Store store, String scope) {
        return hasScope(store.getScope(), scope);
    }

    public static boolean hasScope(String scopes, String scope) {
        return scopes != null && Arrays.stream(scopes.split("[,\\s]+")).anyMatch(scope::equals);
    }

    public JsonNode product(Store store, long id) {
        if (id <= 0) throw new Invalid("product");
        try {
            return catalog.getCatalogProduct(store, id);
        } catch (RestClientResponseException ex) {
            throw new Invalid(ex.getStatusCode().value() == 404 ? "product" : "catalog");
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new Invalid("catalog");
        } catch (IllegalArgumentException ex) {
            throw new Invalid("product");
        }
    }

    public List<Variant> variants(JsonNode product, String language) {
        return StreamSupport.stream(product.path("variants").spliterator(), false)
                .filter(variant -> variant.path("id").asLong() > 0)
                .map(variant -> {
                    String name = StreamSupport.stream(variant.path("values").spliterator(), false)
                            .map(value -> localized(value, language))
                            .filter(value -> !value.isBlank()).collect(Collectors.joining(" / "));
                    if (name.isBlank()) name = "#" + variant.path("id").asLong();
                    return new Variant(variant.path("id").asLong(), name, variant.path("price").asText(""));
                }).toList();
    }

    public void link(Store store, long parentId, long productId, long variantId, String label, String language) {
        if (parentId == productId) throw new Invalid("same");
        label = text(label, 120);
        JsonNode parent = product(store, parentId);
        JsonNode complement = product(store, productId);
        if ("hidden".equals(complement.path("visibility").asText())
                || (!complement.has("visibility") && complement.has("published") && !complement.path("published").asBoolean())
                || variants(complement, language).stream().noneMatch(variant -> variant.id() == variantId)) {
            throw new Invalid("variant");
        }
        try {
            jdbc.update("""
                    INSERT INTO product_complement_offers
                    (id,store_id,parent_product_id,parent_name,complement_product_id,complement_variant_id,complement_name,label,creation_state)
                    VALUES (?,?,?,?,?,?,?,?,'READY')
                    """, UUID.randomUUID().toString(), store.getStoreId(), parentId, name(parent, language),
                    productId, variantId, name(complement, language), label);
        } catch (DuplicateKeyException ex) {
            throw new Invalid("duplicate");
        }
    }

    public void create(Store store, UUID requestId, long parentId, String label, NewProduct input, String language) {
        if (!hasScope(store, "write_products")) throw new Invalid("scope");
        String productName = text(input.name(), 120);
        label = text(label, 120);
        var payload = productPayload(input, productName, requestId);
        JsonNode parent = product(store, parentId);
        String id = requestId.toString();
        try {
            jdbc.update("""
                    INSERT INTO product_complement_offers
                    (id,store_id,parent_product_id,parent_name,complement_name,label,creation_state)
                    VALUES (?,?,?,?,?,?,'PENDING')
                    """, id, store.getStoreId(), parentId, name(parent, language), productName, label);
        } catch (DuplicateKeyException ex) {
            throw new Invalid("submitted");
        }
        JsonNode created;
        try {
            created = catalog.createCatalogProduct(store, payload);
        } catch (RestClientResponseException ex) {
            if (Set.of(400, 401, 402, 403, 422, 429).contains(ex.getStatusCode().value())) {
                jdbc.update("DELETE FROM product_complement_offers WHERE store_id=? AND id=? AND creation_state='PENDING'",
                        store.getStoreId(), id);
                throw new Invalid(ex.getStatusCode().value() == 403 ? "scope" : "rejected");
            }
            throw new Invalid("uncertain");
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new Invalid("uncertain");
        }
        long productId = created == null ? 0 : created.path("id").asLong();
        long variantId = created == null ? 0 : created.path("variants").path(0).path("id").asLong();
        if (productId <= 0 || variantId <= 0) throw new Invalid("uncertain");
        complete(store, id, productId, variantId);
    }

    public void recover(Store store, UUID requestId) {
        String id = requestId.toString();
        if (jdbc.queryForObject("""
                SELECT COUNT(*) FROM product_complement_offers
                WHERE store_id=? AND id=? AND creation_state='PENDING'
                """, Integer.class, store.getStoreId(), id) == 0) throw new Invalid("missing");
        String sku = "NC-" + id;
        JsonNode product;
        try {
            product = catalog.getCatalogProductBySku(store, sku);
        } catch (RestClientResponseException ex) {
            throw new Invalid(ex.getStatusCode().value() == 404 ? "notFoundYet" : "catalog");
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new Invalid("catalog");
        }
        if (product == null || product.path("id").asLong() <= 0) throw new Invalid("notFoundYet");
        for (JsonNode variant : product.path("variants")) {
            if (sku.equals(variant.path("sku").asText()) && variant.path("id").asLong() > 0) {
                complete(store, id, product.path("id").asLong(), variant.path("id").asLong());
                return;
            }
        }
        throw new Invalid("notFoundYet");
    }

    private void complete(Store store, String id, long productId, long variantId) {
        try {
            jdbc.update("""
                    UPDATE product_complement_offers
                    SET complement_product_id=?,complement_variant_id=?,creation_state='READY'
                    WHERE store_id=? AND id=? AND creation_state='PENDING'
                    """, productId, variantId, store.getStoreId(), id);
        } catch (DuplicateKeyException ex) {
            throw new Invalid("duplicate");
        }
    }

    private static Map<String, Object> productPayload(NewProduct input, String name, UUID requestId) {
        if (input.price() == null || input.price().signum() <= 0 || input.price().scale() > 2
                || input.price().compareTo(new BigDecimal("999999999")) > 0
                || input.stock() == null || input.stock() < 0 || input.stock() > 1_000_000) throw new Invalid("fields");
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("price", input.price().toPlainString());
        variant.put("sku", "NC-" + requestId);
        variant.put("stock_management", true);
        variant.put("stock", input.stock());
        dimension(variant, "weight", input.weight());
        dimension(variant, "width", input.width());
        dimension(variant, "height", input.height());
        dimension(variant, "depth", input.depth());
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("name", Map.of("pt", name, "es", name, "en", name));
        product.put("visibility", input.unlisted() ? "unlisted" : "visible");
        product.put("requires_shipping", true);
        product.put("variants", List.of(variant));
        if (input.imageUrl() != null && !input.imageUrl().isBlank()) {
            String url = input.imageUrl().strip();
            try {
                URI uri = URI.create(url);
                if (url.length() > 1000 || !"https".equalsIgnoreCase(uri.getScheme())
                        || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
            } catch (IllegalArgumentException ex) {
                throw new Invalid("image");
            }
            product.put("images", List.of(Map.of("src", url)));
        }
        return product;
    }

    private static void dimension(Map<String, Object> variant, String field, BigDecimal value) {
        if (value == null) return;
        if (value.signum() <= 0 || value.scale() > 3 || value.compareTo(BigDecimal.valueOf(10000)) > 0) {
            throw new Invalid("fields");
        }
        variant.put(field, value.toPlainString());
    }

    public void update(long storeId, UUID id, String label, boolean enabled, Appearance appearance) {
        int count = jdbc.update("""
                UPDATE product_complement_offers SET label=?,enabled=?,show_image=?,background_color=?,text_color=?,show_border=?,rounded_corners=?
                WHERE store_id=? AND id=? AND creation_state='READY'
                """, text(label, 120), enabled, appearance.showImage(), color(appearance.backgroundColor()), color(appearance.textColor()),
                appearance.showBorder(), appearance.roundedCorners(), storeId, id.toString());
        if (count == 0) throw new Invalid("missing");
        cache.invalidateStoreAfterCommit(storeId);
    }

    private static String color(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.matches("#[0-9a-fA-F]{6}")) throw new Invalid("color");
        return value;
    }

    public void remove(long storeId, UUID id) {
        int count = jdbc.update("DELETE FROM product_complement_offers WHERE store_id=? AND id=? AND creation_state='READY'",
                storeId, id.toString());
        if (count == 0) throw new Invalid("missing");
        cache.invalidateStoreAfterCommit(storeId);
    }

    public static String name(JsonNode product, String language) {
        String name = localized(product.path("name"), language);
        if (name.isBlank()) return "#" + product.path("id").asLong();
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private static String localized(JsonNode value, String language) {
        if (value.isTextual()) return value.asText();
        for (String key : List.of(language, "pt", "es", "en")) {
            String text = value.path(key).asText("");
            if (!text.isBlank()) return text;
        }
        return "";
    }

    private static String text(String value, int max) {
        if (value == null || value.isBlank() || value.strip().length() > max) throw new Invalid("fields");
        return value.strip();
    }
}
