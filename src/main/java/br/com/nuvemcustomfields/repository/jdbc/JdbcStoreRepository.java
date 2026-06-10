package br.com.nuvemcustomfields.repository.jdbc;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcStoreRepository implements StoreRepository {

    private static final String SELECT = """
            SELECT id, store_id, store_name, access_token, scope, plan, subscription_id,
                   courtesy_premium, courtesy_premium_reason, billing_plan_external_id,
                   billing_amount_currency, billing_amount_value, billing_next_execution,
                   billing_last_execution, billing_suspended, billing_last_synced_at,
                   billing_last_error, product_text_color, checkout_text_color, cart_text_color,
                   installed_at, uninstalled_at
              FROM stores
            """;

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert insert;

    public JdbcStoreRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.insert = new SimpleJdbcInsert(dataSource)
                .withTableName("stores")
                .usingGeneratedKeyColumns("id");
    }

    @Override
    public Optional<Store> findByStoreId(Long storeId) {
        return jdbc.query(SELECT + " WHERE store_id = ?", JdbcEntityMapper.STORE, storeId).stream().findFirst();
    }

    @Override
    public Optional<Store> findActiveByStoreId(Long storeId) {
        return jdbc.query(SELECT + " WHERE store_id = ? AND uninstalled_at IS NULL",
                JdbcEntityMapper.STORE, storeId).stream().findFirst();
    }

    @Override
    public List<Store> findAll() {
        return jdbc.query(SELECT + " ORDER BY id", JdbcEntityMapper.STORE);
    }

    @Override
    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM stores", Long.class);
    }

    @Override
    public Store save(Store store) {
        if (store.getId() == null) {
            Number id = insert.executeAndReturnKey(values(store));
            store.setId(id.longValue());
            return store;
        }
        Map<String, Object> values = values(store);
        jdbc.update("""
                UPDATE stores
                   SET store_id = ?, store_name = ?, access_token = ?, scope = ?, plan = ?,
                       subscription_id = ?, courtesy_premium = ?, courtesy_premium_reason = ?,
                       billing_plan_external_id = ?, billing_amount_currency = ?, billing_amount_value = ?,
                       billing_next_execution = ?, billing_last_execution = ?, billing_suspended = ?,
                       billing_last_synced_at = ?, billing_last_error = ?, product_text_color = ?,
                       checkout_text_color = ?, cart_text_color = ?, installed_at = ?, uninstalled_at = ?
                 WHERE id = ?
                """,
                values.get("store_id"), values.get("store_name"), values.get("access_token"), values.get("scope"),
                values.get("plan"), values.get("subscription_id"), values.get("courtesy_premium"),
                values.get("courtesy_premium_reason"), values.get("billing_plan_external_id"),
                values.get("billing_amount_currency"), values.get("billing_amount_value"),
                values.get("billing_next_execution"), values.get("billing_last_execution"),
                values.get("billing_suspended"), values.get("billing_last_synced_at"),
                values.get("billing_last_error"), values.get("product_text_color"),
                values.get("checkout_text_color"), values.get("cart_text_color"),
                values.get("installed_at"), values.get("uninstalled_at"), store.getId());
        return store;
    }

    private Map<String, Object> values(Store store) {
        Map<String, Object> values = new HashMap<>();
        values.put("store_id", store.getStoreId());
        values.put("store_name", store.getStoreName());
        values.put("access_token", store.getAccessToken());
        values.put("scope", store.getScope());
        values.put("plan", store.getPlan().name());
        values.put("subscription_id", store.getSubscriptionId());
        values.put("courtesy_premium", store.isCourtesyPremium());
        values.put("courtesy_premium_reason", store.getCourtesyPremiumReason());
        values.put("billing_plan_external_id", store.getBillingPlanExternalId());
        values.put("billing_amount_currency", store.getBillingAmountCurrency());
        values.put("billing_amount_value", store.getBillingAmountValue());
        values.put("billing_next_execution", store.getBillingNextExecution() == null
                ? null : Date.valueOf(store.getBillingNextExecution()));
        values.put("billing_last_execution", store.getBillingLastExecution() == null
                ? null : Date.valueOf(store.getBillingLastExecution()));
        values.put("billing_suspended", store.isBillingSuspended());
        values.put("billing_last_synced_at", store.getBillingLastSyncedAt() == null
                ? null : Timestamp.from(store.getBillingLastSyncedAt()));
        values.put("billing_last_error", store.getBillingLastError());
        values.put("product_text_color", store.getProductTextColor());
        values.put("checkout_text_color", store.getCheckoutTextColor());
        values.put("cart_text_color", store.getCartTextColor());
        values.put("installed_at", Timestamp.from(store.getInstalledAt()));
        values.put("uninstalled_at", store.getUninstalledAt() == null ? null : Timestamp.from(store.getUninstalledAt()));
        return values;
    }
}
