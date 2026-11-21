(() => {
    const editor = document.querySelector('[data-field-unsaved-message]');
    if (!editor) return;
    const dialog = editor.querySelector('[data-field-unsaved-dialog]');
    let destination;

    const textTypes = ['TEXT', 'TEXTAREA', 'NUMBER'];
    const multiTypes = ['CHECKBOX', 'QUANTITY_SELECT'];
    const optionTypes = [...multiTypes, 'SELECT', 'COLOR_SELECT', 'FILE_UPLOAD'];

    function snapshot(form) {
        const data = new FormData(form);
        const type = data.get('fieldType');
        const position = form.querySelector('.field-position');
        const values = [type, data.get('label'), data.has('required'), position ? position.value : data.get('sortOrder')];
        if (textTypes.includes(type)) values.push(data.get('maxLength'), data.get('validationPattern'));
        if (textTypes.includes(type) || type === 'SELECT') values.push(data.get('placeholder'));
        if (multiTypes.includes(type)) values.push(data.get('minSelections'), data.get('maxSelections'));
        if (type === 'QUANTITY_SELECT') values.push(data.get('quantityColumns'));
        if (type === 'FILE_UPLOAD') values.push(data.get('maxFiles'));
        if (optionTypes.includes(type)) values.push(data.get('optionsText'));
        if (type === 'COLOR_SELECT') values.push(data.has('showColorNames'));
        if (type === 'IMAGE_SELECT') {
            const raw = data.get('imageOptionsJson');
            let images = raw;
            try {
                const options = JSON.parse(raw || '[]');
                if (Array.isArray(options)) images = options.map(option => [option?.id, option?.label]);
            } catch (_) { /* Compare invalid drafts as entered. */ }
            values.push(images, data.has('showImageZoom'), data.get('mobileImageColumns'));
        }
        return JSON.stringify(values);
    }

    const originals = new Map([...editor.querySelectorAll('.field-editor-form')]
        .map(form => [form, form.dataset.initialDirty === 'true' ? null : snapshot(form)]));
    const dirty = new Set();
    let navigation;
    let expiry;

    function clearNavigation() {
        window.clearTimeout(expiry);
        navigation = null;
    }
    function permitNavigation(event) {
        clearNavigation();
        navigation = { event };
        expiry = window.setTimeout(clearNavigation, 2000);
    }
    function beforeUnload(event) {
        const allowed = navigation && !navigation.event?.defaultPrevented;
        clearNavigation();
        if (allowed || !dirty.size) return;
        event.preventDefault();
        event.returnValue = true;
    }
    function refresh(form) {
        if (snapshot(form) === originals.get(form)) dirty.delete(form);
        else dirty.add(form);
        if (dirty.size) window.addEventListener('beforeunload', beforeUnload);
        else window.removeEventListener('beforeunload', beforeUnload);
    }
    for (const form of originals.keys()) {
        if (originals.get(form) === null) refresh(form);
        const edited = event => {
            if (event.target.closest('.field-live-preview')) return;
            clearNavigation();
            refresh(form);
        };
        form.addEventListener('input', edited);
        form.addEventListener('change', edited);
        form.addEventListener('reset', () => window.setTimeout(() => refresh(form), 0));
    }
    window.addEventListener('pageshow', clearNavigation);
    dialog?.querySelector('[data-field-unsaved-stay]').addEventListener('click', () => dialog.close());
    dialog?.querySelector('[data-field-unsaved-leave]').addEventListener('click', () => {
        dialog.close();
        permitNavigation();
        location.assign(destination);
    });

    document.addEventListener('click', event => {
        if (!dirty.size || event.defaultPrevented || event.button !== 0 || event.ctrlKey ||
                event.metaKey || event.shiftKey || event.altKey) return;
        const link = event.target.closest('a[href]');
        if (!link || link.hasAttribute('download') || link.target && link.target.toLowerCase() !== '_self') return;
        const url = new URL(link.href, location.href);
        const current = new URL(location.href);
        if (url.origin !== current.origin) return;
        if (url.pathname === current.pathname && url.search === current.search && link.getAttribute('href').includes('#')) return;
        clearNavigation();
        if (typeof dialog?.showModal === 'function') {
            event.preventDefault();
            destination = url.href;
            dialog.showModal();
        } else if (window.confirm(editor.dataset.fieldUnsavedMessage)) permitNavigation(event);
        else event.preventDefault();
    });
    document.addEventListener('submit', event => {
        clearNavigation();
        if (event.defaultPrevented || !dirty.size) return;
        if ([...dirty].some(form => form !== event.target) && !window.confirm(editor.dataset.fieldUnsavedMessage)) {
            event.preventDefault();
            return;
        }
        permitNavigation(event);
    });
})();
