import { readFileSync } from 'node:fs';
import { JSDOM } from 'jsdom';
import { expect, it } from 'vitest';

const script = readFileSync(new URL('../../../resources/static/assets/merchant-coming-soon.js', import.meta.url), 'utf8');
const markup = (store: string, version = 'personalization-v1') => `<section data-coming-soon="${version}" data-store-id="${store}"><button data-coming-soon-dismiss hidden>Dispensar</button></section>`;

it('keeps dismissal scoped to the store and announcement version', () => {
    const dom = new JSDOM(markup('123'), { url: 'https://app.test/admin', runScripts: 'outside-only' });
    const { window } = dom;
    window.eval(script);
    window.document.querySelector('button')!.click();
    expect(window.document.querySelector('section')!.hidden).toBe(true);
    window.document.body.innerHTML = markup('123');
    window.eval(script);
    expect(window.document.querySelector('section')!.hidden).toBe(true);
    for (const [store, version] of [['456', 'personalization-v1'], ['123', 'personalization-v2']]) {
        window.document.body.innerHTML = markup(store, version);
        window.eval(script);
        expect(window.document.querySelector('section')!.hidden).toBe(false);
        expect(window.document.querySelector('button')!.hidden).toBe(false);
    }
    window.close();
});

it('allows dismissing when browser storage is blocked', () => {
    const dom = new JSDOM(markup('123'), { runScripts: 'outside-only' });
    dom.window.eval(script);
    dom.window.document.querySelector('button')!.click();
    expect(dom.window.document.querySelector('section')!.hidden).toBe(true);
    dom.window.close();
});
