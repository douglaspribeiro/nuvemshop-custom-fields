import { readFileSync } from 'node:fs';
import { JSDOM } from 'jsdom';
import { afterEach, expect, it } from 'vitest';
const source = readFileSync(new URL('../../../resources/static/assets/product-fields-editor.js', import.meta.url), 'utf8');
let dom: JSDOM;
afterEach(() => dom?.window.close());
function boot(pattern = '', prepare?: (document: Document) => void) {
    dom = new JSDOM(`<form class="field-editor-form">
        <input name="label" value="Nome para gravar"><input type="checkbox" name="required" checked>
        <select name="fieldType"><option>TEXT</option><option>NUMBER</option><option>SELECT</option><option>IMAGE_SELECT</option><option>COLOR_SELECT</option><option>TEXTAREA</option><option>CHECKBOX</option><option>QUANTITY_SELECT</option><option>FILE_UPLOAD</option></select>
        <label class="field-quantity-layout" hidden><select name="quantityColumns"><option value="1">1</option><option value="2">2</option><option value="3">3</option></select></label><input name="maxFiles" type="number" value="3"><label><input name="maxLength" value="20"></label><label><input name="placeholder" value="Ana"></label>
        <label><textarea name="optionsText">Azul\nVerde</textarea></label><input name="imageOptionsJson" value="[]">
        <label class="field-checkbox-limits" hidden><input name="minSelections" type="number" min="0" data-invalid="Limites inválidos" data-quantity-invalid="Limites de unidades inválidos" data-unit-counter="{0} de {1} unidades" data-unit-count="{0} unidades" data-counter="{0} de {1} selecionadas" data-count="{0} selecionadas"></label>
        <label class="field-checkbox-limits" hidden><input name="maxSelections" type="number" min="1"></label>
        <input type="checkbox" name="showImageZoom" checked>
        <select name="mobileImageColumns"><option value="">Automático</option><option value="2">2</option><option value="3">3</option></select>
        <input type="hidden" name="validationPattern">
        <section class="field-validation"><select class="field-format"><option value="">Livre</option><option value="^[0-9]+$">Números</option><option value="custom">Personalizada</option></select>
        <details class="field-custom-format"><input class="field-pattern" data-invalid="Regra inválida"></details></section>
        <section class="field-live-preview" data-fallback-label="Campo" data-select-prompt="Escolha" data-image-zoom="Ampliar imagem"><div class="field-preview-control"></div></section>
        <input type="hidden" name="sortOrder" value="0"><input class="field-position" type="number" min="1" step="1" required value="1">
        <button type="submit">Salvar</button></form>`, { runScripts: 'outside-only' });
    input('validationPattern').value = pattern;
    prepare?.(dom.window.document);
    dom.window.eval(source);
}
function input(name: string) { return dom.window.document.querySelector<HTMLInputElement>(`[name="${name}"]`)!; }
function select(selector: string, value: string) {
    const control = dom.window.document.querySelector<HTMLSelectElement>(selector)!;
    control.value = value; control.dispatchEvent(new dom.window.Event('change', { bubbles: true }));
}
it('opens the selected field or new field form and focuses its label', () => {
    dom = new JSDOM(`<a data-field-jump href="#configured-field-1">Nome</a><a data-field-jump href="#new-field">Adicionar</a>
        <details id="configured-field-1"><summary>Nome</summary><input name="label"></details>
        <details id="new-field"><summary>Novo campo</summary><input name="label"></details>`, {runScripts:'outside-only'});
    dom.window.eval(source);
    for (const id of ['configured-field-1', 'new-field']) {
        dom.window.document.querySelector<HTMLAnchorElement>(`a[href="#${id}"]`)!.click();
        const target = dom.window.document.getElementById(id) as HTMLDetailsElement;
        expect(target.open).toBe(true);
        expect(dom.window.document.activeElement).toBe(target.querySelector('[name="label"]'));
    }
});
it('previews enlargement links, toggles them live and keeps image selection available', () => {
    boot();
    input('imageOptionsJson').value = JSON.stringify([{id:'image-1',label:'Azul marinho'},{id:'',label:'Rascunho'}]);
    select('[name=fieldType]', 'IMAGE_SELECT');
    const links = () => dom.window.document.querySelectorAll<HTMLAnchorElement>('.field-preview-image-zoom');
    expect(links()).toHaveLength(1);
    expect(links()[0].textContent).toBe('Ampliar imagem');
    expect(links()[0].getAttribute('href')).toBe('/admin/images/image-1');
    expect(links()[0].getAttribute('aria-label')).toBe('Ampliar imagem: Azul marinho');
    expect(links()[0].target).toBe('_blank');
    input('showImageZoom').checked = false;
    input('showImageZoom').dispatchEvent(new dom.window.Event('change',{bubbles:true}));
    expect(links()).toHaveLength(0);
    const choice=dom.window.document.querySelector<HTMLButtonElement>('.field-preview-choice')!;
    expect(choice.querySelector('img')).not.toBeNull();
    expect(choice.textContent).toBe('Azul marinho');choice.click();
    expect(choice.getAttribute('aria-pressed')).toBe('true');
    input('showImageZoom').checked = true;
    input('showImageZoom').dispatchEvent(new dom.window.Event('change',{bubbles:true}));
    expect(links()).toHaveLength(1);
    dom.window.document.querySelector<HTMLElement>('.field-live-preview')!.dataset.imageZoom='Ampliar imagen';
    select('[name=mobileImageColumns]','3');
    expect(links()[0].textContent).toBe('Ampliar imagen');
});
it('reflects two and three mobile columns and returns to automatic in the preview', () => {
    boot();
    input('imageOptionsJson').value = JSON.stringify([{id:'image-1',label:'Verde oliva'}]);
    select('[name=fieldType]', 'IMAGE_SELECT');
    for (const columns of ['2','3','']) {
        select('[name=mobileImageColumns]', columns);
        expect(dom.window.document.querySelector<HTMLElement>('.field-preview-images')!.dataset.mobileColumns).toBe(columns || undefined);
        expect(dom.window.document.querySelector('.field-preview-choice span')!.textContent).toBe('Verde oliva');
    }
});
it('uses ready formats without requiring regex and updates the posted value', () => {
    boot();
    expect(dom.window.document.querySelector<HTMLElement>('.field-custom-format')!.hidden).toBe(true);
    select('.field-format', '^[0-9]+$');
    expect(input('validationPattern').value).toBe('^[0-9]+$');
    select('.field-format', '');
    expect(input('validationPattern').value).toBe('');
});
it('preserves existing custom rules and blocks invalid edits', () => {
    boot('^ABC$');
    expect(dom.window.document.querySelector<HTMLDetailsElement>('.field-custom-format')!.open).toBe(true);
    expect(input('validationPattern').value).toBe('^ABC$');
    const custom = dom.window.document.querySelector<HTMLInputElement>('.field-pattern')!;
    custom.value = '['; custom.dispatchEvent(new dom.window.Event('input', { bubbles: true }));
    expect(custom.validationMessage).toBe('Regra inválida');
    const submit = new dom.window.Event('submit', { cancelable: true });
    dom.window.document.querySelector('form')!.dispatchEvent(submit);
    expect(submit.defaultPrevented).toBe(true);
});
it('shows relevant controls by type while retaining the original values', () => {
    boot('^ABC$');
    select('[name=fieldType]', 'SELECT');
    expect(input('placeholder').closest('label')!.hidden).toBe(false);
    expect(dom.window.document.querySelector('.field-preview-control option')!.textContent).toBe('Ana');
    expect(input('optionsText').closest('label')!.hidden).toBe(false);
    expect(dom.window.document.querySelectorAll('.field-preview-control option')).toHaveLength(3);
    select('[name=fieldType]', 'TEXT');
    expect(input('placeholder').value).toBe('Ana');
    expect(input('validationPattern').value).toBe('^ABC$');
    expect(input('optionsText').closest('label')!.hidden).toBe(true);
});
it('keeps preview responses out of the save request and preserves them during typing', () => {
    boot();
    const preview = dom.window.document.querySelector<HTMLInputElement>('.field-preview-control input')!;
    preview.value = 'Ana'; preview.dispatchEvent(new dom.window.Event('input', { bubbles: true }));
    expect(dom.window.document.querySelector<HTMLInputElement>('.field-preview-control input')!.value).toBe('Ana');
    expect(preview.name).toBe(''); expect(preview.required).toBe(false);
});

it('shows positions from one and submits the original zero-based order', () => {
    boot();
    const position = dom.window.document.querySelector<HTMLInputElement>('.field-position')!;
    expect(position.value).toBe('1');
    expect(input('sortOrder').value).toBe('0');
    position.value = '4'; position.dispatchEvent(new dom.window.Event('input', { bubbles: true }));
    expect(input('sortOrder').value).toBe('3');
    position.value = '0'; position.dispatchEvent(new dom.window.Event('input', { bubbles: true }));
    expect(position.checkValidity()).toBe(false);
    expect(input('sortOrder').value).toBe('3');
});

it('uses the configured select prompt and falls back when blank', () => {
    boot();
    input('placeholder').value = 'Escolha a moagem';
    select('[name=fieldType]', 'SELECT');
    const option = () => dom.window.document.querySelector<HTMLOptionElement>('.field-preview-control option')!;
    expect(option().textContent).toBe('Escolha a moagem');
    expect(option().value).toBe('');
    input('placeholder').value = '   ';
    input('placeholder').dispatchEvent(new dom.window.Event('input', { bubbles: true }));
    expect(option().textContent).toBe('Escolha');
});

it('previews an interactive checkbox and hides irrelevant text settings', () => {
    boot('^ABC$');
    select('[name=fieldType]', 'CHECKBOX');
    const checkbox = dom.window.document.querySelector<HTMLInputElement>('.field-preview-control input')!;
    expect(checkbox.type).toBe('checkbox');
    expect(checkbox.required).toBe(false);
    expect(checkbox.name).toBe('');
    expect(checkbox.checked).toBe(false);
    checkbox.click();
    expect(checkbox.checked).toBe(true);
    for (const name of ['maxLength', 'placeholder']) expect(input(name).closest('label')!.hidden).toBe(true);
    expect(dom.window.document.querySelector<HTMLElement>('.field-validation')!.hidden).toBe(true);
    expect(input('required').disabled).toBe(false);
});

it('previews several independent checkbox options without submitting preview answers', () => {
    boot();
    input('optionsText').value = 'Laço\nCartão\nLaço\nEmbalagem';
    select('[name=fieldType]', 'CHECKBOX');
    expect(input('optionsText').closest('label')!.hidden).toBe(false);
    const boxes = [...dom.window.document.querySelectorAll<HTMLInputElement>('.field-preview-checkbox-group input')];
    expect(boxes).toHaveLength(3);
    boxes[0].click(); boxes[2].click();
    expect(boxes.map(box => box.checked)).toEqual([true, false, true]);
    boxes[0].click(); expect(boxes[2].checked).toBe(true);
    expect(boxes.every(box => box.name === '' && !box.required)).toBe(true);
    input('optionsText').value = ''; select('[name=fieldType]', 'CHECKBOX');
    expect(dom.window.document.querySelectorAll('.field-preview-control input')).toHaveLength(1);
});

it('shows the compact upload preview with the configured count and plan byte limit',()=>{
    boot();
    const preview=dom.window.document.querySelector<HTMLElement>('.field-live-preview')!;
    preview.dataset.uploadMaxMb='20';
    select('[name="fieldType"]','FILE_UPLOAD');
    const picker=preview.querySelector<HTMLInputElement>('input[type=file]')!;
    expect(picker.multiple).toBe(true);
    expect(picker.closest('.field-preview-upload')).not.toBeNull();
    expect(preview.querySelector('.field-preview-upload-help')?.textContent).toContain('20 MB');
    expect(preview.querySelector('.field-preview-upload-help')?.textContent).toContain('3 arquivos');
});
it('prefills upload formats without overwriting a merchant restriction',()=>{
    boot();
    input('optionsText').value='';
    select('[name="fieldType"]','FILE_UPLOAD');
    expect(input('optionsText').value).toBe('JPEG\nPNG\nPDF');
    input('optionsText').value='PNG';
    select('[name="fieldType"]','TEXT');
    select('[name="fieldType"]','FILE_UPLOAD');
    expect(input('optionsText').value).toBe('PNG');
    expect(dom.window.document.querySelector<HTMLInputElement>('.field-preview-upload input')!.accept).toBe('.png');
});

it('previews named colors in configured order without submitting preview choices',()=>{
    boot();input('optionsText').value=JSON.stringify([{label:'Branco',color:'#FFFFFF'},{label:'Azul marinho',color:'#123ABC'}]);
    select('[name=fieldType]','COLOR_SELECT');
    const buttons=dom.window.document.querySelectorAll<HTMLButtonElement>('.field-preview-colors button');
    expect([...buttons].map(b=>b.textContent)).toEqual(['Branco','Azul marinho']);buttons[1].click();expect(buttons[1].getAttribute('aria-pressed')).toBe('true');
    expect(buttons[1].name).toBe('');expect(input('optionsText').closest('label')!.hidden).toBe(true);
});
it('can hide color names in the preview while keeping an accessible choice',()=>{
    boot();const checkbox=dom.window.document.createElement('input');checkbox.type='checkbox';checkbox.name='showColorNames';checkbox.checked=false;
    input('optionsText').closest('form')!.append(checkbox);
    input('optionsText').value=JSON.stringify([{label:'Azul',color:'#123ABC'}]);select('[name=fieldType]','COLOR_SELECT');
    const choice=dom.window.document.querySelector<HTMLButtonElement>('.field-preview-colors button')!;
    expect(choice.textContent).toBe('');expect(choice.getAttribute('aria-label')).toBe('Azul');
});

it('shows optional checkbox limits, rejects invalid ranges and previews a bounded selection',()=>{
    boot();expect(input('minSelections').disabled).toBe(true);
    input('optionsText').value='1\n2\n3';input('minSelections').value='2';input('maxSelections').value='2';
    select('[name=fieldType]','CHECKBOX');
    expect(input('minSelections').disabled).toBe(false);expect(input('minSelections').checkValidity()).toBe(true);
    const boxes=[...dom.window.document.querySelectorAll<HTMLInputElement>('.field-preview-checkbox-group input')];
    boxes[0].click();boxes[1].click();expect(boxes[2].disabled).toBe(true);
    expect(dom.window.document.querySelector('.field-preview-checkbox-group small')?.textContent).toBe('2 de 2 selecionadas');
    input('minSelections').value='3';input('minSelections').dispatchEvent(new dom.window.Event('input',{bubbles:true}));
    expect(input('minSelections').validationMessage).toBe('Limites inválidos');
    select('[name=fieldType]','TEXT');expect(input('minSelections').disabled).toBe(true);
    expect(input('minSelections').validationMessage).toBe('');
});

it('previews option quantities and accepts a total larger than the count of distinct options',()=>{
    boot();input('optionsText').value='Rosa\nMargarida';input('minSelections').value='7';input('maxSelections').value='7';
    select('[name=fieldType]','QUANTITY_SELECT');expect(input('minSelections').checkValidity()).toBe(true);
    const inputs=[...dom.window.document.querySelectorAll<HTMLInputElement>('.field-preview-quantities input')];
    inputs[0].value='3';inputs[1].value='4';inputs[1].dispatchEvent(new dom.window.Event('input',{bubbles:true}));
    expect(dom.window.document.querySelector('.field-preview-quantities small')?.textContent).toBe('7 de 7 unidades');
    input('minSelections').value='8';input('minSelections').dispatchEvent(new dom.window.Event('input',{bubbles:true}));
    expect(input('minSelections').validationMessage).toBe('Limites de unidades inválidos');
    select('[name=fieldType]','CHECKBOX');expect(input('minSelections').validationMessage).toBe('Limites inválidos');
});

it('previews the saved quantity layout and hides the column control for other types',()=>{
    boot();input('optionsText').value='Rosa\nMargarida';
    select('[name=fieldType]','QUANTITY_SELECT');select('[name=quantityColumns]','3');
    expect(dom.window.document.querySelector<HTMLElement>('.field-preview-quantity-list')?.style.getPropertyValue('--quantity-columns')).toBe('3');
    expect(input('quantityColumns').disabled).toBe(false);
    select('[name=fieldType]','TEXT');expect(input('quantityColumns').disabled).toBe(true);
});

it('validates numeric preview answers with either decimal separator', () => {
    boot();
    select('[name="fieldType"]', 'NUMBER');
    const answer = dom.window.document.querySelector<HTMLInputElement>('.field-preview-control input')!;
    expect(answer.inputMode).toBe('decimal');
    for (const [value, invalid] of [['abc', 'true'], ['-1,25', 'false'], ['8.5', 'false'], ['', 'false']]) {
        answer.value = value;
        answer.dispatchEvent(new dom.window.Event('input', { bubbles: true }));
        expect(answer.getAttribute('aria-invalid')).toBe(invalid);
    }
});

it('previews the whole product in storefront order with every field type and live drafts', () => {
    boot('', document => {
        const form = document.querySelector('form')!;
        const configured = document.createElement('div');
        configured.className = 'field-list';
        const types = ['TEXT', 'TEXTAREA', 'NUMBER', 'SELECT', 'CHECKBOX', 'IMAGE_SELECT', 'COLOR_SELECT', 'QUANTITY_SELECT', 'FILE_UPLOAD'];
        for (const [index, type] of types.entries()) {
            const row = document.createElement('details');
            row.className = 'field-item';
            row.id = 'configured-field-' + (index + 1);
            const editor = form.cloneNode(true) as HTMLFormElement;
            editor.querySelector<HTMLInputElement>('[name=label]')!.value = type;
            editor.querySelector<HTMLSelectElement>('[name=fieldType]')!.value = type;
            editor.querySelector<HTMLInputElement>('[name=sortOrder]')!.value = String(index);
            editor.querySelector<HTMLInputElement>('.field-position')!.value = String(index + 1);
            editor.querySelector<HTMLInputElement>('[name=required]')!.checked = index === 0;
            if (type === 'COLOR_SELECT') editor.querySelector<HTMLTextAreaElement>('[name=optionsText]')!.value = '[{"label":"Verde","color":"#008000"}]';
            if (type === 'FILE_UPLOAD') editor.querySelector<HTMLTextAreaElement>('[name=optionsText]')!.value = 'PDF';
            if (type === 'IMAGE_SELECT') editor.querySelector<HTMLInputElement>('[name=imageOptionsJson]')!.value = '[{"id":"img","label":"Foto"}]';
            row.append(editor);
            configured.append(row);
        }
        const draft = document.createElement('details');
        draft.id = 'new-field';
        form.querySelector<HTMLInputElement>('[name=label]')!.value = '';
        form.before(configured, draft);
        draft.append(form);
        document.body.insertAdjacentHTML('beforeend', '<details data-product-preview><p data-product-preview-empty></p><div data-product-preview-fields></div></details>');
    });
    const document = dom.window.document;
    const preview = document.querySelector<HTMLElement>('[data-product-preview-fields]')!;
    const labels = () => [...preview.children].map(field => field.querySelector('legend, span')?.textContent);
    expect(labels()).toEqual(['TEXT *', 'TEXTAREA', 'NUMBER', 'SELECT *', 'CHECKBOX', 'IMAGE_SELECT', 'COLOR_SELECT', 'QUANTITY_SELECT', 'FILE_UPLOAD']);
    expect([...preview.children].map((field, index) => Boolean(field.querySelector([
        'input[type=text]', 'textarea', 'input[inputmode=decimal]', 'select', 'input[type=checkbox]',
        'img[src*="img"]', 'button[aria-label=Verde]', 'input[type=number]', 'input[accept=".pdf"]'
    ][index])))).toEqual(Array(9).fill(true));
    expect(Array.from(preview.querySelectorAll('input, textarea, select, button')).every(control =>
        !control.id && !control.hasAttribute('name') && !control.closest('form'))).toBe(true);
    const editor = document.querySelector<HTMLFormElement>('#configured-field-4 form')!;
    editor.querySelector<HTMLInputElement>('[name=label]')!.focus();
    expect(preview.children[3].getAttribute('aria-current')).toBe('true');
    editor.querySelector<HTMLButtonElement>('.preview-locate')!.click();
    expect(preview.closest('details')!.open).toBe(true);
    expect(document.activeElement).toBe(preview.children[3]);
    const dropdown = preview.querySelector('select')!;
    dropdown.selectedIndex = 1;
    expect(dropdown.value).toBe('Azul');
    const edit = (control: HTMLInputElement, value: string) => {
        control.value = value;
        control.dispatchEvent(new dom.window.Event('input', { bubbles: true }));
    };
    edit(document.querySelector<HTMLInputElement>('#configured-field-1 .field-position')!, '9');
    expect(labels()).toEqual(['TEXTAREA', 'NUMBER', 'SELECT *', 'CHECKBOX', 'IMAGE_SELECT', 'COLOR_SELECT', 'QUANTITY_SELECT', 'TEXT *', 'FILE_UPLOAD']);
    expect(dropdown.value).toBe('Azul');
    const draft = document.querySelector<HTMLInputElement>('#new-field [name=label]')!;
    edit(draft, 'Rascunho');
    expect(labels()[0]).toBe('Rascunho *');
    expect(preview.children).toHaveLength(10);
    edit(draft, '');
    expect(preview.children).toHaveLength(9);
});
