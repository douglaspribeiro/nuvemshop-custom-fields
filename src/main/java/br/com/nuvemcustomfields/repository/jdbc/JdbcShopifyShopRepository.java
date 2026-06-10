package br.com.nuvemcustomfields.repository.jdbc;

import br.com.nuvemcustomfields.entity.ShopifyShop;
import br.com.nuvemcustomfields.repository.ShopifyShopRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcShopifyShopRepository implements ShopifyShopRepository {

    private static final String SELECT = """
            SELECT id, shop_domain, shopify_shop_id, shop_name, access_token, scope, plan,
                   installed_at, uninstalled_at
              FROM shopify_shops
            """;

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert insert;

    public JdbcShopifyShopRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.insert = new SimpleJdbcInsert(dataSource)
                .withTableName("shopify_shops")
                .usingGeneratedKeyColumns("id");
    }

    @Override
    public Optional<ShopifyShop> findByShopDomain(String shopDomain) {
        return jdbc.query(SELECT + " WHERE shop_domain = ?", JdbcEntityMapper.SHOPIFY_SHOP, shopDomain)
                .stream().findFirst();
    }

    @Override
    public Optional<ShopifyShop> findActiveById(Long id) {
        return jdbc.query(SELECT + " WHERE id = ? AND uninstalled_at IS NULL", JdbcEntityMapper.SHOPIFY_SHOP, id)
                .stream().findFirst();
    }

    @Override
    public Optional<ShopifyShop> findActiveByShopDomain(String shopDomain) {
        return jdbc.query(SELECT + " WHERE shop_domain = ? AND uninstalled_at IS NULL",
                JdbcEntityMapper.SHOPIFY_SHOP, shopDomain).stream().findFirst();
    }

    @Override
    public ShopifyShop save(ShopifyShop shop) {
        Map<String, Object> values = values(shop);
        if (shop.getId() == null) {
            shop.setId(insert.executeAndReturnKey(values).longValue());
            return shop;
        }
        jdbc.update("""
                UPDATE shopify_shops
                   SET shop_domain = ?, shopify_shop_id = ?, shop_name = ?, access_token = ?,
                       scope = ?, plan = ?, installed_at = ?, uninstalled_at = ?
                 WHERE id = ?
                """,
                values.get("shop_domain"), values.get("shopify_shop_id"), values.get("shop_name"),
                values.get("access_token"), values.get("scope"), values.get("plan"),
                values.get("installed_at"), values.get("uninstalled_at"), shop.getId());
        return shop;
    }

    private Map<String, Object> values(ShopifyShop shop) {
        Map<String, Object> values = new HashMap<>();
        values.put("shop_domain", shop.getShopDomain());
        values.put("shopify_shop_id", shop.getShopifyShopId());
        values.put("shop_name", shop.getShopName());
        values.put("access_token", shop.getAccessToken());
        values.put("scope", shop.getScope());
        values.put("plan", shop.getPlan().name());
        values.put("installed_at", Timestamp.from(shop.getInstalledAt()));
        values.put("uninstalled_at", shop.getUninstalledAt() == null ? null : Timestamp.from(shop.getUninstalledAt()));
        return values;
    }
}
