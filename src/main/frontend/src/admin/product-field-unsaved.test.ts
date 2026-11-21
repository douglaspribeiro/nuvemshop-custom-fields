import { readFileSync } from 'node:fs';
import { JSDOM, VirtualConsole } from 'jsdom';
import { afterEach, expect, it, vi } from 'vitest';

const source = readFileSync(new URL('../../../resources/static/assets/product-field-unsaved.js', import.meta.url), 'utf8');
let dom: JSDOM;
afterEach(() => dom?.window.close());

function boot(initialDraft = false) {
    const field = (id: string) => `<form id="${id}" class="field-editor-form" ${initialDraft && id === 'draft' ? 'data-initial-dirty="true"' : ''}>
        <input name="label" value="Nome" required>
        <select name="fieldType"><option>TEXT</option><option>CHECKBOX</option><option>IMAGE_SELECT</option></select>
        <input name="placeholder"><input name="required" type="checkbox">
        <textarea name="optionsText"></textarea><input name="imageOptionsJson" type="hidden">
        <div class="field-live-preview"><input></div>
    </form>`;
    dom = new JSDOM(`<main data-field-unsaved-message="¿Descartar cambios?">
        ${field('saved')}${field('draft')}<form id="delete"></form>
        <a id="products" href="/admin/products">Productos</a>
        <a id="jump" href="#draft">Añadir</a>
        <a id="new-tab" href="/admin/products" target="_blank">Nueva pestaña</a>
        <dialog data-field-unsaved-dialog>
            <button id="stay" data-field-unsaved-stay>Seguir editando</button>
            <button id="leave" data-field-unsaved-leave>Descartar y salir</button>
        </dialog>
    </main>`, { url: 'https://example.com/admin/products/1/fields', runScripts: 'outside-only', virtualConsole: new VirtualConsole() });
    const confirmation = vi.fn(() => false);
    dom.window.confirm = confirmation;
    dom.window.eval(source);
    return confirmation;
}

function edit(selector: string, value: string, kind = 'input') {
    const control = dom.window.document.querySelector<HTMLInputElement>(selector)!;
    control.value = value;
    control.dispatchEvent(new dom.window.Event(kind, { bubbles: true }));
}

function unload() {
    return !dom.window.dispatchEvent(new dom.window.Event('beforeunload', { cancelable: true }));
}

function click(id: string, options: MouseEventInit = {}) {
    return dom.window.document.getElementById(id)!.dispatchEvent(new dom.window.MouseEvent('click', {
        bubbles: true, cancelable: true, ...options,
    }));
}

function submit(id: string) {
    return dom.window.document.getElementById(id)!.dispatchEvent(new dom.window.Event('submit', {
        bubbles: true, cancelable: true,
    }));
}

it('ignores preview typing and clears the browser guard when values are restored or reset', async () => {
    const confirmation = boot();
    edit('#saved .field-live-preview input', 'Exemplo');
    expect(unload()).toBe(false);
    edit('#saved [name=label]', 'Personalização');
    expect(unload()).toBe(true);
    edit('#saved [name=label]', 'Nome');
    expect(unload()).toBe(false);
    edit('#saved [name=label]', 'Personalização');
    dom.window.document.querySelector<HTMLFormElement>('#saved')!.reset();
    await new Promise(resolve => dom.window.setTimeout(resolve, 0));
    expect(unload()).toBe(false);
    expect(confirmation).not.toHaveBeenCalled();
});

it('warns on same-tab internal navigation but allows jumps and new tabs', () => {
    const confirmation = boot();
    edit('#draft [name=label]', 'Rascunho');
    expect(click('jump')).toBe(true);
    expect(click('new-tab')).toBe(true);
    expect(confirmation).not.toHaveBeenCalled();
    expect(click('products')).toBe(false);
    expect(confirmation).toHaveBeenCalledWith('¿Descartar cambios?');
    expect(unload()).toBe(true);
    confirmation.mockReturnValue(true);
    expect(click('products')).toBe(true);
    expect(unload()).toBe(false);
    expect(unload()).toBe(true);
});

it('lets the user keep editing or discard from the internal navigation dialog', () => {
    const confirmation = boot();
    const dialog = dom.window.document.querySelector<HTMLDialogElement>('dialog')!;
    dialog.showModal = () => { dialog.open = true; };
    dialog.close = () => { dialog.open = false; };
    edit('#draft [name=label]', 'Rascunho');
    expect(click('products')).toBe(false);
    expect(dialog.open).toBe(true);
    click('stay');
    expect(dialog.open).toBe(false);
    expect(unload()).toBe(true);
    expect(click('products')).toBe(false);
    click('leave');
    expect(dialog.open).toBe(false);
    expect(unload()).toBe(false);
    expect(confirmation).not.toHaveBeenCalled();
});

it('allows own valid submissions and guards other dirty forms', () => {
    const confirmation = boot();
    edit('#saved [name=label]', 'Nome editado');
    expect(submit('saved')).toBe(true);
    expect(confirmation).not.toHaveBeenCalled();
    expect(unload()).toBe(false);
    edit('#draft [name=label]', 'Rascunho');
    expect(submit('saved')).toBe(false);
    expect(submit('delete')).toBe(false);
    expect(unload()).toBe(true);
    confirmation.mockReturnValue(true);
    expect(submit('draft')).toBe(true);
    expect(unload()).toBe(false);
});

it('keeps the guard when form validation cancels submission', () => {
    const confirmation = boot();
    edit('#saved [name=label]', '');
    dom.window.document.getElementById('saved')!.addEventListener('submit', event => event.preventDefault());
    expect(submit('saved')).toBe(false);
    expect(confirmation).not.toHaveBeenCalled();
    expect(unload()).toBe(true);
});

it('ignores inactive widget initialization and compares active image options', () => {
    boot();
    edit('#draft [name=imageOptionsJson]', '[]', 'change');
    edit('#draft [name=optionsText]', 'JPEG\nPNG\nPDF');
    expect(unload()).toBe(false);
    edit('#draft [name=fieldType]', 'IMAGE_SELECT', 'change');
    edit('#draft [name=imageOptionsJson]', '[{"id":"a","label":"Foto"}]', 'change');
    expect(unload()).toBe(true);
    edit('#draft [name=fieldType]', 'TEXT', 'change');
    expect(unload()).toBe(false);
});

it('guards a server-prefilled unsaved draft immediately', () => {
    const confirmation = boot(true);
    expect(unload()).toBe(true);
    expect(submit('saved')).toBe(false);
    expect(confirmation).toHaveBeenCalledOnce();
    expect(submit('draft')).toBe(true);
    expect(unload()).toBe(false);
});
