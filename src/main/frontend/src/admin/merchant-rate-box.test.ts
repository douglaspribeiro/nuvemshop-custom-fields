import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import {afterEach,expect,it,vi} from 'vitest';
const source=readFileSync(new URL('../../../resources/static/assets/merchant-rate-box.js',import.meta.url),'utf8');
let dom:JSDOM;
afterEach(()=>dom?.window.close());
async function boot(eligible=true){
 dom=new JSDOM(`<section id="merchant-rate-box" hidden data-error="Falha" data-selected="Sua nota: {0} de 5"><button type="button" data-rate-dismiss>Fechar</button>
 <form id="rate-box-form"><fieldset class="rate-box-stars">${[1,2,3,4,5].map(score=>`<label><input type="radio" required name="rating" value="${score}"><span>★</span></label>`).join('')}</fieldset>
 <p id="rate-box-selection"></p><div id="rate-box-feedback" hidden><textarea id="rate-box-comment" maxlength="2000"></textarea></div><button type="submit" id="rate-box-submit" hidden>Enviar</button><button type="button" data-rate-dismiss>Agora não</button></form>
 <div id="rate-box-thanks" hidden tabindex="-1"><div id="rate-box-public" hidden><a id="rate-box-public-link" target="_blank" rel="noopener noreferrer">Avaliar na Nuvemshop</a></div></div><p id="rate-box-error"></p></section>`,{runScripts:'outside-only',url:'https://app.test/admin',pretendToBeVisual:true});
 const fetch=vi.fn(async(url:string,options:{body:string})=>{
  if(url.endsWith('/display'))return {ok:true,status:eligible?200:204,json:async()=>({id:'display-1'})};
  if(url.endsWith('/dismiss'))return {ok:true,status:200,json:async()=>({dismissed:true})};
  if(url.endsWith('/public-review'))return {ok:true,status:200,json:async()=>({recorded:true})};
  const {rating}=JSON.parse(options.body);return {ok:true,status:200,json:async()=>({rating,reviewUrl:rating===5?'https://www.nuvemshop.com.br/loja-aplicativos-nuvem/campos-personalizados7#reviews-section':null})};
 });
 Object.assign(dom.window,{fetch});dom.window.eval(source);
 await vi.waitFor(()=>expect(fetch).toHaveBeenCalledOnce());await Promise.resolve();await Promise.resolve();return fetch;
}
const element=(id:string)=>dom.window.document.getElementById(id)!;
const select=(score:number)=>dom.window.document.querySelector<HTMLInputElement>(`input[value="${score}"]`)!.click();
const submit=()=>element('rate-box-form').dispatchEvent(new dom.window.Event('submit',{bubbles:true,cancelable:true}));
it('keeps the box hidden when the server says the store is not eligible',async()=>{
 const fetch=await boot(false);expect(element('merchant-rate-box').hidden).toBe(true);expect(fetch.mock.calls[0][0]).toBe('/admin/rate-box/display');
});
it.each([1,2,3,4])('stores score %s and a private comment without offering a public rating',async(score)=>{
 const fetch=await boot();await vi.waitFor(()=>expect(element('merchant-rate-box').hidden).toBe(false));
 select(score);expect(element('rate-box-feedback').hidden).toBe(false);expect(element('rate-box-selection').textContent).toContain(String(score));
 (element('rate-box-comment') as HTMLTextAreaElement).value='Melhorar a configuração';submit();submit();
 await vi.waitFor(()=>expect(element('rate-box-thanks').hidden).toBe(false));
 expect(element('rate-box-public').hidden).toBe(true);expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({rating:score,comment:'Melhorar a configuração'});expect(fetch).toHaveBeenCalledTimes(2);
});
it('records five stars before showing an optional external link and never publishes automatically',async()=>{
 const fetch=await boot();await vi.waitFor(()=>expect(element('merchant-rate-box').hidden).toBe(false));select(5);
 expect(fetch).toHaveBeenCalledOnce();expect(element('rate-box-public').hidden).toBe(true);submit();
 await vi.waitFor(()=>expect(element('rate-box-public').hidden).toBe(false));
 expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({rating:5,comment:null});
 const link=element('rate-box-public-link') as HTMLAnchorElement;expect(link.href).toContain('campos-personalizados7#reviews-section');expect(link.target).toBe('_blank');expect(link.rel).toContain('noopener');
});
it('closes without requiring a rating and stays closed even when offline',async()=>{
 const fetch=await boot();await vi.waitFor(()=>expect(element('merchant-rate-box').hidden).toBe(false));fetch.mockRejectedValueOnce(new Error('offline'));
 dom.window.document.querySelector<HTMLButtonElement>('[data-rate-dismiss]')!.click();expect(element('merchant-rate-box').hidden).toBe(true);
 await vi.waitFor(()=>expect(fetch).toHaveBeenCalledTimes(2));expect(fetch.mock.calls[1][0]).toBe('/admin/rate-box/display-1/dismiss');
});
it('records the public review click only after five stars and keeps the external link usable',async()=>{
 const fetch=await boot();await vi.waitFor(()=>expect(element('merchant-rate-box').hidden).toBe(false));select(5);submit();
 await vi.waitFor(()=>expect(element('rate-box-public').hidden).toBe(false));
 expect(fetch).toHaveBeenCalledTimes(2);
 const link=element('rate-box-public-link');
 const event=new dom.window.MouseEvent('click',{bubbles:true,cancelable:true});
 link.dispatchEvent(event);
 await vi.waitFor(()=>expect(fetch).toHaveBeenCalledTimes(3));
 expect(fetch.mock.calls[2][0]).toBe('/admin/rate-box/display-1/public-review');
 expect(event.defaultPrevented).toBe(false);
});
it('preserves the typed feedback and allows retry after a failed save',async()=>{
 const fetch=await boot();await vi.waitFor(()=>expect(element('merchant-rate-box').hidden).toBe(false));select(2);
 (element('rate-box-comment') as HTMLTextAreaElement).value='Meu comentário';fetch.mockRejectedValueOnce(new Error('Falha de rede'));submit();
 await vi.waitFor(()=>expect(element('rate-box-error').textContent).toBe('Falha de rede'));
 expect((element('rate-box-comment') as HTMLTextAreaElement).value).toBe('Meu comentário');expect(element('rate-box-thanks').hidden).toBe(true);
 submit();await vi.waitFor(()=>expect(element('rate-box-thanks').hidden).toBe(false));
});
