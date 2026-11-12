(() => {
    'use strict';
    const types = new Set(['TEXT', 'NUMBER', 'SELECT', 'TEXTAREA', 'IMAGE_SELECT', 'COLOR_SELECT', 'CHECKBOX', 'FILE_UPLOAD']);
    const started = new WeakMap();
    const row = target => target.closest('.field-editor-form');
    function track(name, element) {
        const fieldType = element.querySelector('[name="fieldType"]')?.value;
        if (!types.has(fieldType) || typeof window.gtag !== 'function') return;
        window.gtag('event', name, { configuration_type: fieldType, app_area: 'merchant_admin' });
    }
    document.addEventListener('change', event => {
        const element = row(event.target);
        if (!element) return;
        const type = element.querySelector('[name="fieldType"]')?.value;
        if (started.get(element) !== type) { track('configuration_started', element); started.set(element, type); }
    });
    document.addEventListener('submit', event => {
        if (event.defaultPrevented || !event.target.matches('form')) return;
        const elements = event.target.matches('.field-editor-form') ? [event.target] :
            event.target.id === 'template-editor' ? [...event.target.querySelectorAll('.field-editor-form')] : [];
        for (const element of elements) track('configuration_submitted', element);
    }, true);
})();
