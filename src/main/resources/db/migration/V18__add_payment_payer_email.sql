alter table payment_subscriptions
    add column payer_email varchar(254) null after provider_subscription_id;
