import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {afterEach,expect,it,vi} from 'vitest';
const asset=(name:string)=>readFileSync(new URL(`../../../resources/static/assets/${name}.js`,import.meta.url),'utf8');
let dom:JSDOM;
afterEach(()=>dom?.window.close());
function boot(saved?:string){
 dom=new JSDOM(`<form class="field-editor-form"><select name="fieldType"><option>COLOR_SELECT</option><option>SELECT</option></select><label><textarea name="optionsText"></textarea></label>
 <section class="color-options-editor" data-name="Nome" data-color="Cor" data-up="Subir" data-down="Descer" data-remove="Remover" data-drag="Arrastar" data-invalid="Inválido"><div class="color-options-rows"></div><button type="button" class="color-option-add">Adicionar</button><p class="color-options-status"></p></section></form>`,{runScripts:'outside-only'});
 dom.window.document.querySelector('textarea')!.value=saved || JSON.stringify([{label:'Azul',color:'#0000FF'},{label:'Branco',color:'#FFFFFF'},{label:'Preto',color:'#000000'}]);
 dom.window.eval(asset('field-ordering'));dom.window.eval(asset('color-options-admin'));
}
const options=()=>JSON.parse(dom.window.document.querySelector('textarea')!.value);
it('edits colors and persists keyboard ordering/removal into reopened definitions',()=>{
 boot();let rows=dom.window.document.querySelectorAll('.color-option-row');
 const name=rows[0].querySelector('input')!;name.value='Azul marinho';name.dispatchEvent(new dom.window.Event('input',{bubbles:true}));
 (rows[0].querySelectorAll('button')[2] as HTMLButtonElement).click();expect(options().map((o:{label:string})=>o.label)).toEqual(['Branco','Azul marinho','Preto']);
 rows=dom.window.document.querySelectorAll('.color-option-row');(rows[0].querySelectorAll('button')[3] as HTMLButtonElement).click();expect(options()).toHaveLength(2);
 const picker=dom.window.document.querySelector<HTMLInputElement>('input[type=color]')!;picker.value='#123abc';picker.dispatchEvent(new dom.window.Event('input',{bubbles:true}));expect(options()[0].color).toBe('#123abc');
 const saved=dom.window.document.querySelector('textarea')!.value;dom.window.close();boot(saved);
 expect([...dom.window.document.querySelectorAll<HTMLInputElement>('input:not([type=color])')].map(i=>i.value)).toEqual(['Azul marinho','Preto']);
});
it('supports drag and touch handles and blocks duplicate names before save',async()=>{
 boot();await Promise.resolve();
 let rows=dom.window.document.querySelectorAll('.color-option-row');
 rows[2].querySelector('button')!.dispatchEvent(new dom.window.Event('dragstart',{bubbles:true}));rows[0].dispatchEvent(new dom.window.Event('drop',{bubbles:true,cancelable:true}));
 expect(options().map((o:{label:string})=>o.label)).toEqual(['Preto','Azul','Branco']);
 rows=dom.window.document.querySelectorAll('.color-option-row');
 Object.defineProperty(dom.window.document,'elementFromPoint',{value:()=>rows[0]});
 rows[2].querySelector('button')!.dispatchEvent(new dom.window.Event('pointerdown',{bubbles:true}));rows[2].dispatchEvent(new dom.window.Event('pointerup',{bubbles:true}));
 expect(options()[0].label).toBe('Branco');
 const names=dom.window.document.querySelectorAll<HTMLInputElement>('.color-option-row input:not([type=color])');names[1].value=' branco ';names[1].dispatchEvent(new dom.window.Event('input',{bubbles:true}));
 const submit=new dom.window.Event('submit',{bubbles:true,cancelable:true});dom.window.document.querySelector('form')!.dispatchEvent(submit);expect(submit.defaultPrevented).toBe(true);
});
it('keeps ordinary select options independent when changing field type',()=>{
 boot();const type=dom.window.document.querySelector('select')!;type.value='SELECT';type.dispatchEvent(new dom.window.Event('change'));dom.window.document.querySelector('textarea')!.value='P\nM\nG';
 type.value='COLOR_SELECT';type.dispatchEvent(new dom.window.Event('change'));expect(options()[0].label).toBe('Azul');type.value='SELECT';type.dispatchEvent(new dom.window.Event('change'));expect(dom.window.document.querySelector('textarea')!.value).toBe('P\nM\nG');
});
it('saves all field positions after ordering without submitting unsaved field changes',async()=>{
 dom=new JSDOM(`<button id="save-field-order">Salvar</button><span id="field-order-status"></span><div class="field-list" data-order-url="/admin/products/123/fields/order" data-saved="Salvo" data-failed="Falhou">${[1,2,3].map(id=>`<details id="configured-field-${id}"><summary><button type="button" data-order-handle>Arrastar</button><button type="button" data-order-up>Subir</button></summary><input name="label" value="Não salvo"><input name="sortOrder" value="${id-1}"><input class="field-position" value="${id}"></details>`).join('')}</div>`,{runScripts:'outside-only'});
 const fetch=vi.fn().mockResolvedValue({ok:true});Object.assign(dom.window,{fetch});dom.window.eval(asset('field-ordering'));
 dom.window.document.querySelector<HTMLButtonElement>('#configured-field-3 [data-order-up]')!.click();dom.window.document.getElementById('save-field-order')!.click();
 await vi.waitFor(()=>expect(dom.window.document.getElementById('field-order-status')!.textContent).toBe('Salvo'));
 expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual([1,3,2]);expect(fetch.mock.calls[0][1].headers['X-NCF-Action']).toBe('field-order');
 expect(dom.window.document.querySelector<HTMLInputElement>('#configured-field-3 [name=sortOrder]')!.value).toBe('1');expect(fetch.mock.calls[0][1].body).not.toContain('Não salvo');
});
