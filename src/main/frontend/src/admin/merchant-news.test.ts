import { readFileSync } from 'node:fs';
import { JSDOM } from 'jsdom';
import { expect, it } from 'vitest';
const script=readFileSync(new URL('../../../resources/static/assets/merchant-news.js',import.meta.url),'utf8');
function boot(store='42', ids=['templates-v1','buyer-uploads-v1']) {
    return new JSDOM(`<div data-merchant-news data-store-id="${store}"><button data-dismiss-news hidden>Dispensar</button>${ids.map(id=>`<article data-news-id="${id}"></article>`).join('')}</div>`, {url:'https://app.test',runScripts:'outside-only'});
}
it('remembers dismissal per store and announcement, showing future announcements',()=>{
    const dom=boot();const doc=dom.window.document;dom.window.eval(script);
    (doc.querySelector('[data-dismiss-news]') as HTMLButtonElement).click();
    expect((doc.querySelector('[data-merchant-news]') as HTMLElement).hidden).toBe(true);
    expect(dom.window.localStorage.getItem('ncf-news:42:templates-v1')).toBe('dismissed');
    dom.window.eval(script);
    expect((doc.querySelector('[data-merchant-news]') as HTMLElement).hidden).toBe(true);
    const panel=doc.querySelector('[data-merchant-news]') as HTMLElement;
    panel.dataset.storeId='99';dom.window.eval(script);expect(panel.hidden).toBe(false);
    panel.dataset.storeId='42';doc.querySelector('[data-news-id]')!.setAttribute('data-news-id','templates-v2');
    dom.window.eval(script);expect(panel.hidden).toBe(false);
    expect((doc.querySelector('[data-news-id="buyer-uploads-v1"]') as HTMLElement).hidden).toBe(true);
    dom.window.close();
});
it('allows dismissal when browser storage is blocked',()=>{
    const dom=boot();Object.defineProperty(dom.window,'localStorage',{get(){throw new Error('blocked');}});
    dom.window.eval(script);const doc=dom.window.document;
    (doc.querySelector('[data-dismiss-news]') as HTMLButtonElement).click();
    expect((doc.querySelector('[data-merchant-news]') as HTMLElement).hidden).toBe(true);dom.window.close();
});
it('does nothing on pages without news',()=>{
    const dom=new JSDOM('<body></body>',{runScripts:'outside-only'});
    expect(()=>dom.window.eval(script)).not.toThrow();dom.window.close();
});
