(() => {
    document.querySelectorAll('.field-editor-form').forEach(form => {
        const get = name => form.querySelector(`[name="${name}"]`);
        const type = get('fieldType');
        const position = form.querySelector('.field-position');
        if (position) {
            position.addEventListener('input', () => {
                if (position.validity.valid && position.value !== '') {
                    get('sortOrder').value = String(Number(position.value) - 1);
                }
            });
        }
        const pattern = get('validationPattern');
        const format = form.querySelector('.field-format');
        const custom = form.querySelector('.field-pattern');
        const advanced = form.querySelector('.field-custom-format');
        const preview = form.querySelector('.field-live-preview');
        const target = preview.querySelector('.field-preview-control');
        const initial = pattern.value || '';
        format.value = [...format.options].some(option => option.value === initial) ? initial : 'custom';
        custom.value = initial;
        advanced.open = format.value === 'custom';

        function validatePattern() {
            let message = '';
            if (format.value === 'custom' && custom.value) {
                try { new RegExp(custom.value); } catch (_) { message = custom.dataset.invalid; }
            }
            custom.setCustomValidity(message);
        }
        function syncFormat() {
            pattern.value = format.value === 'custom' ? custom.value : format.value;
            advanced.hidden = format.value !== 'custom';
            if (!advanced.hidden) advanced.open = true;
            validatePattern();
        }
        format.addEventListener('change', () => {
            syncFormat();
            render();
        });
        custom.addEventListener('input', () => { syncFormat(); render(); });
        function visibility() {
            const text = ['TEXT', 'TEXTAREA', 'NUMBER'].includes(type.value);
            ['maxLength', 'placeholder'].forEach(name => get(name).closest('label').hidden = !text);
            get('optionsText').closest('label').hidden = type.value !== 'SELECT';
            form.querySelector('.field-validation').hidden = !text;
            // Hidden controls retain their saved values when changing field types.
            custom.disabled = !text;
            syncFormat();
        }
        function render() {
            target.replaceChildren();
            const wrapper = document.createElement('label');
            const title = document.createElement('span');
            title.textContent = get('label').value || preview.dataset.fallbackLabel;
            if (get('required').checked) title.textContent += ' *';
            wrapper.append(title);
            let input;
            if (type.value === 'IMAGE_SELECT') {
                const gallery = document.createElement('div');
                gallery.className = 'field-preview-images';
                let options = [];
                try { options = JSON.parse(get('imageOptionsJson').value || '[]'); } catch (_) { /* An incomplete draft has no options yet. */ }
                if (!Array.isArray(options)) options = [];
                options.forEach(option => {
                    const choice = document.createElement('button');
                    choice.type = 'button'; choice.className = 'field-preview-choice';
                    choice.setAttribute('aria-pressed', 'false');
                    if (option.id) {
                        const img = document.createElement('img');
                        img.src = '/admin/images/' + encodeURIComponent(option.id) + '?size=thumbnail';
                        img.alt = ''; choice.append(img);
                    }
                    const text = document.createElement('span'); text.textContent = option.label || ''; choice.append(text);
                    choice.addEventListener('click', () => {
                        gallery.querySelectorAll('button').forEach(button => button.setAttribute('aria-pressed', String(button === choice)));
                    });
                    gallery.append(choice);
                });
                target.append(title, gallery); return;
            }
            if (type.value === 'SELECT') {
                input = document.createElement('select');
                const prompt = document.createElement('option');
                prompt.value = ''; prompt.textContent = preview.dataset.selectPrompt; input.append(prompt);
                get('optionsText').value.split(/\r?\n/).map(value => value.trim()).filter(Boolean).forEach(value => {
                    const option = document.createElement('option'); option.textContent = value; input.append(option);
                });
            } else {
                input = document.createElement(type.value === 'TEXTAREA' ? 'textarea' : 'input');
                if (type.value !== 'TEXTAREA') input.type = 'text';
                if (type.value === 'NUMBER') input.inputMode = 'decimal';
                input.placeholder = get('placeholder').value;
                input.maxLength = Number(get('maxLength').value) || 100;
                if (type.value === 'TEXTAREA') input.rows = 3;
            }
            // The preview lives inside the editor form, so it must not block saving.
            input.addEventListener('input', () => {
                let valid = true;
                try { if (pattern.value) valid = new RegExp(pattern.value).test(input.value); } catch (_) { /* Show errors in the rule control. */ }
                input.setAttribute('aria-invalid', String(Boolean(input.value) && !valid));
            });
            wrapper.append(input); target.append(wrapper);
        }
        form.addEventListener('input', event => { if (!preview.contains(event.target)) render(); });
        form.addEventListener('change', event => { if (!preview.contains(event.target)) { visibility(); render(); } });
        form.addEventListener('submit', event => {
            validatePattern();
            if (!custom.disabled && !custom.checkValidity()) { event.preventDefault(); advanced.open = true; custom.reportValidity(); }
        });
        visibility(); render();
    });
})();
