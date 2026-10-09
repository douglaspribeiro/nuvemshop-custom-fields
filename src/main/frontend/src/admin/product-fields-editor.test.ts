import { readFileSync } from 'node:fs';
import { JSDOM } from 'jsdom';
import { afterEach, expect, it } from 'vitest';
const source = readFileSync(new URL('../../../resources/static/assets/product-fields-editor.js', import.meta.url), 'utf8');
let dom: JSDOM;
afterEach(() => dom?.window.close());
function boot(pattern = '') {
    dom = new JSDOM(`<form class="field-editor-form">
        <input name="label" value="Nome para gravar"><input type="checkbox" name="required" checked>
        <select name="fieldType"><option>TEXT</option><option>SELECT</option><option>IMAGE_SELECT</option><option>TEXTAREA</option></select>
        <label><input name="maxLength" value="20"></label><label><input name="placeholder" value="Ana"></label>
        <label><textarea name="optionsText">Azul\nVerde</textarea></label><input name="imageOptionsJson" value="[]">
        <input type="hidden" name="validationPattern">
        <section class="field-validation"><select class="field-format"><option value="">Livre</option><option value="^[0-9]+$">Números</option><option value="custom">Personalizada</option></select>
        <details class="field-custom-format"><input class="field-pattern" data-invalid="Regra inválida"></details></section>
        <section class="field-live-preview" data-fallback-label="Campo" data-select-prompt="Escolha"><div class="field-preview-control"></div></section>
        <input type="hidden" name="sortOrder" value="0"><input class="field-position" type="number" min="1" step="1" required value="1">
        <button type="submit">Salvar</button></form>`, { runScripts: 'outside-only' });
    input('validationPattern').value = pattern;
    dom.window.eval(source);
}
function input(name: string) { return dom.window.document.querySelector<HTMLInputElement>(`[name="${name}"]`)!; }
function select(selector: string, value: string) {
    const control = dom.window.document.querySelector<HTMLSelectElement>(selector)!;
    control.value = value; control.dispatchEvent(new dom.window.Event('change', { bubbles: true }));
}
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
