const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const script = fs.readFileSync('src/main/resources/static/assets/billing-analytics.js', 'utf8');
function run(overrides = {}) {
    const calls = [], listeners = {}, fields = {};
    const form = { action: 'https://app.test/admin/billing/pay',
        querySelector: selector => fields[selector],
        appendChild: field => { fields[`input[name="${field.name}"]`] = field; } };
    const document = { querySelectorAll: () => [form], createElement: () => ({}),
        addEventListener: (name, fn) => { listeners[name] = fn; } };
    const window = { billingAnalytics: { measurementId: 'G-TEST', step: 'payment', flow: 'upgrade',
        plan: 'PREMIUM_ULTRA', sourcePlan: 'PREMIUM_PLUS', currency: 'BRL', provider: 'EFI',
        amount: 8.97, recurringAmount: 59.90, coupon: 'UPGRADE', ...overrides },
        gtag: (...args) => {
            calls.push(args);
            if (args[0] === 'get') args[3](args[2] === 'client_id' ? '123.456' : '789');
        } };
    vm.runInNewContext(script, { window, document, location: { href: 'https://app.test/admin/billing/pay' }, URL });
    return { window, calls, listeners, fields };
}
test('Efí native submission keeps anonymous IDs and reports the actual adjustment', () => {
    const { window, calls, fields } = run();
    assert.equal(fields['input[name="gaClientId"]'].value, '123.456');
    assert.equal(fields['input[name="gaSessionId"]'].value, '789');
    window.billingTrackPayment();
    const event = calls.find(args => args[1] === 'add_payment_info');
    assert.equal(event[2].value, 8.97);
    assert.equal(event[2].recurring_amount, 59.90);
    assert.equal(event[2].coupon, 'UPGRADE');
    assert.ok(!calls.some(args => args[1] === 'purchase'));
});
test('sandbox and absent GA configuration leave payments functional without analytics', () => {
    for (const overrides of [{ sandbox: true }, { measurementId: '' }]) {
        const { calls, fields, window } = run(overrides);
        assert.equal(calls.length, 0);
        assert.equal(Object.keys(fields).length, 0);
        assert.equal(window.billingTrackPayment, undefined);
    }
});
test('only the selected plan enters an external checkout funnel', () => {
    const { listeners, calls } = run({ step: 'plans', plan: null, amount: null, provider: 'MERCADO_PAGO' });
    listeners.submit({ target: { action: 'https://app.test/admin/billing/checkout', querySelector: () => ({ value: 'PREMIUM_PLUS' }) } });
    const event = calls.find(args => args[1] === 'begin_checkout');
    assert.equal(event[2].target_plan, 'PREMIUM_PLUS');
    assert.equal(event[2].flow_type, 'subscription');
});
test('failure events include only a fixed stage, without gateway messages or form data', () => {
    const { window, calls } = run();
    window.billingTrackFailure('tokenization');
    const event = calls.find(args => args[1] === 'payment_failed');
    assert.equal(event[2].failure_stage, 'tokenization');
    assert.ok(!Object.keys(event[2]).some(key => /email|cpf|card|token|payer/i.test(key)));
});
