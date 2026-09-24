alter table payment_subscriptions
    add column provider_checkout_id varchar(120) null after provider_subscription_id,
    add unique key uk_payment_subscriptions_provider_checkout_id (provider_checkout_id);
