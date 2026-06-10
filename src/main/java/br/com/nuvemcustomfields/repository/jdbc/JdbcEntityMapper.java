package br.com.nuvemcustomfields.repository.jdbc;

import br.com.nuvemcustomfields.entity.CommercePlatform;
import br.com.nuvemcustomfields.entity.FeatureFlag;
import br.com.nuvemcustomfields.entity.FieldType;
import br.com.nuvemcustomfields.entity.IntegrationLog;
import br.com.nuvemcustomfields.entity.PersonalizationField;
import br.com.nuvemcustomfields.entity.PersonalizationRule;
import br.com.nuvemcustomfields.entity.PlanAsset;
import br.com.nuvemcustomfields.entity.PlanEvent;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.ShopifyShop;
import br.com.nuvemcustomfields.entity.Store;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

final class JdbcEntityMapper {

    static final RowMapper<Store> STORE = (rs, rowNum) -> {
        Store store = new Store();
        store.setId(rs.getLong("id"));
        store.setStoreId(rs.getLong("store_id"));
        store.setStoreName(rs.getString("store_name"));
        store.setAccessToken(rs.getString("access_token"));
        store.setScope(rs.getString("scope"));
        store.setPlan(PlanType.valueOf(rs.getString("plan")));
        store.setSubscriptionId(rs.getString("subscription_id"));
        store.setCourtesyPremium(rs.getBoolean("courtesy_premium"));
        store.setCourtesyPremiumReason(rs.getString("courtesy_premium_reason"));
        store.setBillingPlanExternalId(rs.getString("billing_plan_external_id"));
        store.setBillingAmountCurrency(rs.getString("billing_amount_currency"));
        store.setBillingAmountValue(rs.getBigDecimal("billing_amount_value"));
        store.setBillingNextExecution(localDate(rs, "billing_next_execution"));
        store.setBillingLastExecution(localDate(rs, "billing_last_execution"));
        store.setBillingSuspended(rs.getBoolean("billing_suspended"));
        store.setBillingLastSyncedAt(instant(rs, "billing_last_synced_at"));
        store.setBillingLastError(rs.getString("billing_last_error"));
        store.setProductTextColor(rs.getString("product_text_color"));
        store.setCheckoutTextColor(rs.getString("checkout_text_color"));
        store.setCartTextColor(rs.getString("cart_text_color"));
        store.setInstalledAt(instant(rs, "installed_at"));
        store.setUninstalledAt(instant(rs, "uninstalled_at"));
        return store;
    };

    static final RowMapper<ShopifyShop> SHOPIFY_SHOP = (rs, rowNum) -> {
        ShopifyShop shop = new ShopifyShop();
        shop.setId(rs.getLong("id"));
        shop.setShopDomain(rs.getString("shop_domain"));
        shop.setShopifyShopId(nullableLong(rs, "shopify_shop_id"));
        shop.setShopName(rs.getString("shop_name"));
        shop.setAccessToken(rs.getString("access_token"));
        shop.setScope(rs.getString("scope"));
        shop.setPlan(PlanType.valueOf(rs.getString("plan")));
        shop.setInstalledAt(instant(rs, "installed_at"));
        shop.setUninstalledAt(instant(rs, "uninstalled_at"));
        return shop;
    };

    static final RowMapper<PersonalizationRule> RULE = (rs, rowNum) -> {
        PersonalizationRule rule = new PersonalizationRule();
        rule.setId(rs.getLong("id"));
        rule.setPlatform(CommercePlatform.valueOf(rs.getString("platform")));
        rule.setStoreId(rs.getLong("store_id"));
        rule.setProductId(rs.getLong("product_id"));
        rule.setProductName(rs.getString("product_name"));
        rule.setEnabled(rs.getBoolean("enabled"));
        rule.setCreatedAt(instant(rs, "created_at"));
        return rule;
    };

    static final RowMapper<PersonalizationField> FIELD = (rs, rowNum) -> {
        PersonalizationField field = new PersonalizationField();
        field.setId(rs.getLong("id"));
        field.setLabel(rs.getString("label"));
        field.setFieldType(FieldType.valueOf(rs.getString("field_type")));
        field.setRequired(rs.getBoolean("required"));
        field.setMaxLength(nullableInteger(rs, "max_length"));
        field.setPlaceholder(rs.getString("placeholder"));
        field.setValidationPattern(rs.getString("validation_pattern"));
        field.setOptionsText(rs.getString("options_text"));
        field.setSortOrder(nullableInteger(rs, "sort_order"));
        return field;
    };

    static final RowMapper<PlanAsset> PLAN_ASSET = (rs, rowNum) -> {
        PlanAsset asset = new PlanAsset();
        asset.setId(rs.getLong("id"));
        asset.setPlanType(PlanType.valueOf(rs.getString("plan_type")));
        asset.setDisplayName(rs.getString("display_name"));
        asset.setDescription(rs.getString("description"));
        asset.setBillingExternalId(rs.getString("billing_external_id"));
        asset.setCurrency(rs.getString("currency"));
        asset.setAmount(rs.getBigDecimal("amount"));
        asset.setProductLimit(rs.getLong("product_limit"));
        asset.setFieldLimit(rs.getLong("field_limit"));
        asset.setEffectiveFrom(localDate(rs, "effective_from"));
        asset.setEffectiveUntil(localDate(rs, "effective_until"));
        asset.setActive(rs.getBoolean("active"));
        asset.setCreatedAt(instant(rs, "created_at"));
        return asset;
    };

    static final RowMapper<PlanEvent> PLAN_EVENT = (rs, rowNum) -> {
        PlanEvent event = new PlanEvent();
        event.setId(rs.getLong("id"));
        event.setStoreId(rs.getLong("store_id"));
        event.setFromPlan(nullableEnum(rs.getString("from_plan"), PlanType.class));
        event.setToPlan(PlanType.valueOf(rs.getString("to_plan")));
        event.setSource(rs.getString("source"));
        event.setCreatedAt(instant(rs, "created_at"));
        return event;
    };

    static final RowMapper<IntegrationLog> INTEGRATION_LOG = (rs, rowNum) -> {
        IntegrationLog log = new IntegrationLog();
        log.setId(rs.getLong("id"));
        log.setStoreId(nullableLong(rs, "store_id"));
        log.setLevel(rs.getString("level"));
        log.setEventType(rs.getString("event_type"));
        log.setMessage(rs.getString("message"));
        log.setCreatedAt(instant(rs, "created_at"));
        return log;
    };

    static final RowMapper<FeatureFlag> FEATURE_FLAG = (rs, rowNum) -> {
        FeatureFlag flag = new FeatureFlag();
        flag.setKey(rs.getString("flag_key"));
        flag.setEnabled(rs.getBoolean("enabled"));
        flag.setDescription(rs.getString("description"));
        return flag;
    };

    private JdbcEntityMapper() {
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static <E extends Enum<E>> E nullableEnum(String value, Class<E> type) {
        return value == null ? null : Enum.valueOf(type, value);
    }
}
