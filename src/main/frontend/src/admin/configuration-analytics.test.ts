import { readFileSync } from 'node:fs';
import { JSDOM } from 'jsdom';
import { it, expect, vi } from 'vitest';
const source=readFileSync(new URL('../../../resources/static/assets/configuration-analytics.js',import.meta.url),'utf8');
it('tracks configuration types without transmitting labels, filenames or entered content',()=>{
    const dom=new JSDOM('<form class="field-editor-form"><select name="fieldType"><option>TEXT</option><option>FILE_UPLOAD</option></select><input name="label" value="Private customer name"><input name="placeholder" value="secret.pdf"></form>',{runScripts:'outside-only'});
    const track=vi.fn();Object.assign(dom.window,{gtag:track});dom.window.eval(source);
    const form=dom.window.document.querySelector('form')!;
    const input=form.querySelector('input')!;
    input.dispatchEvent(new dom.window.Event('change',{bubbles:true}));input.dispatchEvent(new dom.window.Event('change',{bubbles:true}));
    const select=form.querySelector('select')!;select.value='FILE_UPLOAD';select.dispatchEvent(new dom.window.Event('change',{bubbles:true}));
    form.dispatchEvent(new dom.window.Event('submit',{bubbles:true,cancelable:true}));
    expect(track.mock.calls).toEqual([
        ['event','configuration_started',{configuration_type:'TEXT',app_area:'merchant_admin'}],
        ['event','configuration_started',{configuration_type:'FILE_UPLOAD',app_area:'merchant_admin'}],
        ['event','configuration_submitted',{configuration_type:'FILE_UPLOAD',app_area:'merchant_admin'}]
    ]);
    expect(JSON.stringify(track.mock.calls)).not.toContain('Private');expect(JSON.stringify(track.mock.calls)).not.toContain('secret.pdf');
    dom.window.close();
});
