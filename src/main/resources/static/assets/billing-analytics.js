(() => {
    'use strict';
    const config = window.billingAnalytics;
    if (!config?.measurementId || config.sandbox || typeof window.gtag !== 'function') return;
    const params = (plan = config.plan, flow = config.flow) => {
        const result = { flow_type: flow };
        if (plan) {
            result.target_plan = plan;
            result.items = [{ item_id: plan, item_name: plan, quantity: 1 }];
        }
        if (config.sourcePlan) result.source_plan = config.sourcePlan;
        if (config.provider) result.payment_provider = config.provider;
        if (config.currency) result.currency = config.currency;
        if (config.amount != null && config.currency) {
            result.value = Number(config.amount);
            if (result.items) result.items[0].price = Number(config.amount);
        }
        if (config.recurringAmount != null) result.recurring_amount = Number(config.recurringAmount);
        if (config.coupon) result.coupon = config.coupon;
        return result;
    };
    const track = (name, data = params()) => window.gtag('event', name, data);
    // Prepare hidden fields before either regular submission or the Efí native form.submit().
    const setField = (name, value) => {
        if (!value || !/^[0-9.]{1,64}$/.test(String(value))) return;
        document.querySelectorAll('form[method="post"]').forEach(form => {
            const path = new URL(form.action, location.href).pathname;
            if (!path.startsWith('/admin/billing/')) return;
            let field = form.querySelector(`input[name="${name}"]`);
            if (!field) { field = document.createElement('input'); field.type = 'hidden'; field.name = name; form.appendChild(field); }
            field.value = value;
        });
    };
    window.gtag('get', config.measurementId, 'client_id', value => setField('gaClientId', value));
    window.gtag('get', config.measurementId, 'session_id', value => setField('gaSessionId', value));
    window.billingTrackPayment = (paymentType = 'credit_card') => track('add_payment_info', { ...params(), payment_type: paymentType });
    window.billingTrackFailure = stage => track('payment_failed', { ...params(), failure_stage: stage });
    if (config.step === 'plans') {
        track('view_item_list', { ...params(), item_list_id: 'subscription_plans',
            items: ['PREMIUM', 'PREMIUM_PLUS', 'PREMIUM_ULTRA'].map(item_id => ({ item_id, item_name: item_id })) });
    } else if (config.step === 'upgrade_review') {
        track('upgrade_view');
        if (config.coupon) track('coupon_applied');
    } else if (config.step === 'payment') {
        track(config.flow === 'upgrade' ? 'payment_view' : 'begin_checkout');
    }
    if (config.hasError && config.step === 'payment') track('checkout_error', { ...params(), failure_stage: 'server' });
    document.addEventListener('submit', event => {
        const form = event.target;
        const path = new URL(form.action, location.href).pathname;
        if (path === '/admin/billing/checkout' || path === '/admin/billing/subscribe') {
            const plan = form.querySelector('[name="plan"]')?.value;
            track('select_item', params(plan, 'subscription'));
            if (config.provider !== 'EFI') track('begin_checkout', params(plan, 'subscription'));
        } else if (config.step === 'upgrade_review' && path !== '/admin/billing/upgrade/efi') {
            track('begin_checkout');
        }
    });
    document.addEventListener('click', event => {
        const link = event.target.closest('a[href]');
        if (!link) return;
        const url = new URL(link.href, location.href);
        if (config.step === 'plans' && url.pathname === '/admin/billing/upgrade') {
            track('select_item', params(url.searchParams.get('plan'), 'upgrade'));
        } else if (config.step === 'upgrade_review' && url.pathname === '/admin/billing/upgrade/efi/pay') {
            track('begin_checkout');
        }
    });
})();
