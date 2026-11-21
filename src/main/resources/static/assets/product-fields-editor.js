(() => {
    const productPreview = document.querySelector('[data-product-preview]');
    const productControls = productPreview?.querySelector('[data-product-preview-fields]');
    const productEmpty = productPreview?.querySelector('[data-product-preview-empty]');
    const productFields = [];

    let activePreviewField;
    function highlightField(field) {
        activePreviewField?.removeAttribute('aria-current');
        activePreviewField = field.control;
        activePreviewField.setAttribute('aria-current', 'true');
    }

    function positionProductField(field) {
        productFields.sort((left, right) => left.order - right.order || left.id - right.id);
        const following = productFields.slice(productFields.indexOf(field) + 1)
            .find(candidate => candidate.control.parentElement === productControls);
        if (field.control.parentElement !== productControls || field.control.nextElementSibling !== (following?.control ?? null)) {
            productControls.insertBefore(field.control, following?.control ?? null);
        }
    }

    function initialize(form) {
        if(form.dataset.fieldEditorInitialized)return;
        form.dataset.fieldEditorInitialized="true";
        const get = name => form.querySelector(`[name="${name}"]`);
        const type = get('fieldType');
        let previousRequired = get('required').checked;
        let wasSelect = false;
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
        const fieldItem = form.closest('.field-list > .field-item');
        const isNew = form.closest('#new-field') !== null;
        const productField = productControls && (fieldItem || isNew) ? {
            control: document.createElement('div'),
            id: isNew ? Infinity : Number(fieldItem.id.slice('configured-field-'.length)),
            order: Number(get('sortOrder').value)
        } : null;
        let hasDraftSettings = false;
        let locate;
        if (productField) {
            const control = productField.control;
            control.className = 'field-preview-control';
            control.tabIndex = -1;
            control.setAttribute('role', 'group');
            locate = document.createElement('button');
            locate.type = 'button';
            locate.className = 'button small preview-locate';
            locate.textContent = productPreview.dataset.fieldLink;
            preview.append(locate);
            form.addEventListener('focusin', () => highlightField(productField));
            locate.addEventListener('click', () => {
                productPreview.open = true;
                highlightField(productField);
                control.focus();
                control.scrollIntoView?.({block: 'center'});
            });
        }
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
            if(type.value === 'FILE_UPLOAD' && !get('optionsText').value.trim()) get('optionsText').value='JPEG\nPNG\nPDF';
            const text = ['TEXT', 'TEXTAREA', 'NUMBER'].includes(type.value);
            const required = get('required');
            if (type.value === 'SELECT') {
                if (!wasSelect) previousRequired = required.checked;
                required.checked = true;
            } else if (wasSelect) {
                required.checked = previousRequired;
            }
            required.disabled = type.value === 'SELECT';
            wasSelect = type.value === 'SELECT';
            const requiredHelp = form.querySelector('.field-select-required');
            if (requiredHelp) requiredHelp.hidden = !wasSelect;
            get('maxLength').closest('label').hidden = !text;
            const placeholderLabel = get('placeholder').closest('label');
            placeholderLabel.hidden = !text && type.value !== 'SELECT';
            placeholderLabel.querySelectorAll('[data-select-text]').forEach(element => {
                element.textContent = type.value === 'SELECT' ? element.dataset.selectText : element.dataset.text;
            });
            get('optionsText').closest('label').hidden = !['SELECT', 'CHECKBOX', 'QUANTITY_SELECT', 'FILE_UPLOAD'].includes(type.value);
            const uploadCount=form.querySelector('.field-upload-count');
            if(uploadCount) uploadCount.hidden=type.value !== 'FILE_UPLOAD';
            const uploadHelp=form.querySelector('.field-upload-help');
            if(uploadHelp) uploadHelp.hidden=type.value !== 'FILE_UPLOAD';
            form.querySelectorAll('.field-checkbox-limits').forEach(element => {
                element.hidden = !['CHECKBOX','QUANTITY_SELECT'].includes(type.value);
                element.querySelectorAll('[data-quantity-text]').forEach(text => {text.textContent=type.value === 'QUANTITY_SELECT' ? text.dataset.quantityText : text.dataset.checkboxText;});
                element.querySelectorAll('input').forEach(input => { input.disabled = !['CHECKBOX','QUANTITY_SELECT'].includes(type.value); });
            });
            form.querySelectorAll('.field-quantity-layout').forEach(element=>{element.hidden=type.value!=='QUANTITY_SELECT';element.querySelectorAll('select').forEach(input=>{input.disabled=type.value!=='QUANTITY_SELECT';});});
            form.querySelectorAll('.field-quantity-help').forEach(help => {help.hidden=type.value !== 'QUANTITY_SELECT';});
            const checkboxHelp = form.querySelector('.field-checkbox-help');
            if (checkboxHelp) checkboxHelp.hidden = type.value !== 'CHECKBOX';
            form.querySelector('.field-validation').hidden = !text;
            // Hidden controls retain their saved values when changing field types.
            custom.disabled = !text;
            syncFormat();
        }
        function validateSelections() {
            const min = get('minSelections'), max = get('maxSelections');
            if (!min || !max) return;
            const options = new Set(get('optionsText').value.split(/\r?\n/).map(v => v.trim()).filter(Boolean)).size;
            const minimum = min.value === '' ? null : Number(min.value);
            const maximum = max.value === '' ? null : Number(max.value);
            const quantities = type.value === 'QUANTITY_SELECT';
            const invalid = ['CHECKBOX','QUANTITY_SELECT'].includes(type.value) && (quantities || minimum != null || maximum != null) &&
                (!options || minimum != null && (!Number.isInteger(minimum) || minimum < 0 || !quantities && minimum > options)
                    || maximum != null && (!Number.isInteger(maximum) || maximum < 1 || !quantities && maximum > options)
                    || minimum != null && maximum != null && minimum > maximum);
            min.setCustomValidity(invalid ? (quantities ? min.dataset.quantityInvalid : min.dataset.invalid) : '');
        }
        function renderControl(target) {
            target.replaceChildren();
            const wrapper = document.createElement('label');
            const title = document.createElement('span');
            title.textContent = get('label').value || preview.dataset.fallbackLabel;
            if (get('required').checked) title.textContent += ' *';
            wrapper.append(title);
            let input;
            if(type.value === 'FILE_UPLOAD') {
                const extensions = { JPG: '.jpg,.jpeg', PNG: '.png', PDF: '.pdf' };
                const formats = new Set(get('optionsText').value.trim().toUpperCase().split(/[,\s]+/).map(value => value === 'JPEG' ? 'JPG' : value));
                const allowed = Object.keys(extensions).filter(format => formats.has(format));
                input=document.createElement('input');input.type='file';input.multiple=Number(get('maxFiles')?.value || 1)>1;
                input.accept=allowed.map(format => extensions[format]).join(',');
                wrapper.className='field-preview-upload';
                const help=document.createElement('small');help.className='field-preview-upload-help';
                const maxFiles=Number(get('maxFiles')?.value || 1);
                help.textContent=(preview.dataset.uploadHelp || '{1} · até {0} MB por arquivo')
                    .replace('{0}',preview.dataset.uploadMaxMb || '10').replace('{1}',allowed.join(', '));
                if(maxFiles>1) help.textContent+=' · '+(preview.dataset.uploadCount || 'até {0} arquivos').replace('{0}',String(maxFiles));
                const selected=document.createElement('div');selected.className='field-preview-upload-files';
                input.addEventListener('change',()=>{selected.textContent=Array.from(input.files || []).map(file=>file.name).join(', ');});
                wrapper.append(input,help,selected);target.append(wrapper);return;
            }
            if (type.value === 'COLOR_SELECT') {
                const group=document.createElement('div'); group.className='field-preview-colors';
                let options=[];try{options=JSON.parse(get('optionsText').value || '[]');}catch{}
                if(Array.isArray(options)) options.forEach(option=>{
                    const button=document.createElement('button');button.type='button';button.className='field-preview-choice';button.setAttribute('aria-pressed','false');
                    const chip=document.createElement('span');chip.className='color-swatch';
                    if(/^#[0-9a-f]{6}$/i.test(option.color))chip.style.backgroundColor=option.color;
                    const name=document.createElement('span');name.textContent=option.label;button.setAttribute('aria-label',option.label);button.title=option.label;button.append(chip);if(get('showColorNames')?.checked ?? true)button.append(name);
                    button.addEventListener('click',()=>group.querySelectorAll('button').forEach(b=>b.setAttribute('aria-pressed',String(b===button))));group.append(button);
                });target.append(title,group);return;
            }
            if (type.value === 'IMAGE_SELECT') {
                const gallery = document.createElement('div');
                gallery.className = 'field-preview-images';
                const columns = get('mobileImageColumns')?.value;
                if (columns === '2' || columns === '3') gallery.dataset.mobileColumns = columns;
                let options = [];
                try { options = JSON.parse(get('imageOptionsJson').value || '[]'); } catch (_) { /* An incomplete draft has no options yet. */ }
                if (!Array.isArray(options)) options = [];
                options.forEach(option => {
                    const card = document.createElement('div');
                    card.className = 'field-preview-image-option';
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
                    card.append(choice);
                    if (option.id && (get('showImageZoom')?.checked ?? true)) {
                        const link = document.createElement('a');
                        link.className = 'field-preview-image-zoom';
                        link.href = '/admin/images/' + encodeURIComponent(option.id);
                        link.target = '_blank'; link.rel = 'noopener';
                        link.textContent = preview.dataset.imageZoom;
                        link.setAttribute('aria-label', `${link.textContent}: ${option.label || ''}`);
                        card.append(link);
                    }
                    gallery.append(card);
                });
                target.append(title, gallery); return;
            }
            if (type.value === 'QUANTITY_SELECT') {
                const group=document.createElement('fieldset');group.className='field-preview-quantities';
                const legend=document.createElement('legend');legend.textContent=title.textContent;group.append(legend);
                const options=[...new Set(get('optionsText').value.split(/\r?\n/).map(v=>v.trim()).filter(Boolean))];
                const minimum=get('minSelections'),maximum=get('maxSelections');
                const list=document.createElement('div');list.className='field-preview-quantity-list';list.style.setProperty('--quantity-columns',get('quantityColumns')?.value || '1');group.append(list);
                const inputs=options.map(option=>{
                    const row=document.createElement('label'),text=document.createElement('span'),quantity=document.createElement('input');
                    row.className='field-preview-quantity-row';
                    text.textContent=option;quantity.type='number';quantity.step='any';quantity.value='0';
                    quantity.setAttribute('aria-label',option);
                    const controls=document.createElement('span');controls.className='field-preview-quantity-controls';
                    const minus=document.createElement('button'),plus=document.createElement('button');minus.type=plus.type='button';minus.textContent='−';plus.textContent='+';
                    minus.setAttribute('aria-label','− '+option);plus.setAttribute('aria-label','+ '+option);
                    minus.addEventListener('click',()=>{quantity.value=String(Math.max(0,(Number(quantity.value)||0)-1));sync();});
                    plus.addEventListener('click',()=>{quantity.value=String((Number(quantity.value)||0)+1);sync();});
                    controls.append(minus,quantity,plus);
                    // Preview inputs have no native constraints: examples must not block saving the definition.
                    row.append(text,controls);list.append(row);return quantity;
                });
                const counter=document.createElement('small');counter.setAttribute('aria-live','polite');group.append(counter);
                const sync=()=>{const total=inputs.reduce((sum,input)=>sum+(Number.isSafeInteger(input.valueAsNumber)&&input.valueAsNumber>=0?input.valueAsNumber:0),0);
                    counter.textContent=(maximum?.value ? minimum.dataset.unitCounter : minimum?.dataset.unitCount || '{0}')
                        .replace('{0}',String(total)).replace('{1}',maximum?.value || '');};
                group.addEventListener('input',sync);sync();target.append(group);return;
            }
            if (type.value === 'CHECKBOX') {
                const options = [...new Set(get('optionsText').value.split(/\r?\n/).map(value => value.trim()).filter(Boolean))];
                if (options.length) {
                    const group = document.createElement('fieldset');
                    group.className = 'field-preview-checkbox-group';
                    const legend = document.createElement('legend'); legend.textContent = title.textContent; group.append(legend);
                    options.forEach(option => {
                        const choice = document.createElement('label'); choice.className = 'field-preview-checkbox';
                        const box = document.createElement('input'); box.type = 'checkbox';
                        const caption = document.createElement('span'); caption.textContent = option;
                        choice.append(box, caption); group.append(choice);
                    });
                    const counter = document.createElement('small'); counter.setAttribute('aria-live', 'polite');
                    const minimum = get('minSelections'), maximum = get('maxSelections');
                    if (minimum?.value || maximum?.value) group.append(counter);
                    const sync = () => {
                        const boxes = [...group.querySelectorAll('input[type=checkbox]')];
                        const count = boxes.filter(box => box.checked).length;
                        const max = maximum?.value ? Number(maximum.value) : null;
                        counter.textContent = (max != null ? minimum.dataset.counter : minimum?.dataset.count || '{0}')
                            .replace('{0}', String(count)).replace('{1}', String(max));
                        boxes.forEach(box => { box.disabled = !box.checked && max != null && count >= max; });
                    };
                    group.addEventListener('change', sync); sync();
                    target.append(group); return;
                }
                input = document.createElement('input'); input.type = 'checkbox';
                wrapper.className = 'field-preview-checkbox';
                wrapper.prepend(input); target.append(wrapper); return;
            }
            if (type.value === 'SELECT') {
                input = document.createElement('select');
                const prompt = document.createElement('option');
                prompt.value = ''; prompt.textContent = get('placeholder').value.trim() || preview.dataset.selectPrompt; input.append(prompt);
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
                let valid = type.value !== 'NUMBER' || /^-?\d+([.,]\d+)?$/.test(input.value.trim());
                try { if (valid && pattern.value) valid = new RegExp(pattern.value).test(input.value); } catch (_) { /* Show errors in the rule control. */ }
                input.setAttribute('aria-invalid', String(Boolean(input.value) && !valid));
            });
            wrapper.append(input); target.append(wrapper);
        }
        function render() {
            validateSelections();
            renderControl(target);
            if (!productField) return;
            if (isNew && !get('label').value.trim() && !hasDraftSettings) {
                productField.control.remove();
            } else {
                renderControl(productField.control);
                const oldOrder = productField.order;
                productField.order = Number(get('sortOrder').value);
                if (oldOrder !== productField.order || !productField.control.parentElement) {
                    positionProductField(productField);
                }
            }
            locate.hidden = !productField.control.parentElement;
            productField.control.setAttribute('aria-label', get('label').value.trim() || preview.dataset.fallbackLabel);
            productEmpty.hidden = productControls.childElementCount > 0;
        }
        function update(event) {
            if (preview.contains(event.target)) return;
            if (isNew && event.target !== get('label')) hasDraftSettings = true;
            if (event.type === 'change') visibility();
            render();
        }
        form.addEventListener('input', update);
        form.addEventListener('change', update);
        form.addEventListener('submit', event => {
            validatePattern(); validateSelections();
            if (!custom.disabled && !custom.checkValidity()) { event.preventDefault(); advanced.open = true; custom.reportValidity(); }
        });
        if (productField) productFields.push(productField);
        visibility(); render();
    }
    window.ncfInitFieldEditor=initialize;
    document.querySelectorAll('.field-editor-form').forEach(initialize);
    document.querySelectorAll('[data-field-jump]').forEach(link => link.addEventListener('click', () => {
        const target = document.getElementById(link.hash.slice(1));
        if (!target) return;
        target.open = true;
        target.querySelector('input[name="label"]')?.focus({preventScroll:true});
    }));
})();
