import { readFileSync } from "node:fs";
import { JSDOM } from "jsdom";
import { afterEach, expect, it, vi } from "vitest";
const source=readFileSync(new URL("../../../resources/static/assets/option-images-admin.js",import.meta.url),"utf8");
let dom:JSDOM;
afterEach(()=>dom?.window.close());
function boot(options:unknown[]=[],maxOptions=25,enabled=true){
    dom=new JSDOM(`<form class="image-options-form"><select name="fieldType"><option>TEXT</option><option selected>IMAGE_SELECT</option></select>
        <label><input name="maxLength" value="100"></label><label><input name="placeholder"></label><label><input name="validationPattern"></label><label><textarea name="optionsText"></textarea></label>
        <section class="image-options-editor" data-enabled="${enabled}" data-max-options="${maxOptions}" data-limit="Limite do plano" data-upload-url="/admin/products/1/images" data-label="Nome" data-file="Imagem" data-remove="Remover" data-up="Acima" data-down="Abaixo" data-incomplete="Preencha as opções" data-failed="Falha no envio">
            <input name="imageOptionsJson" type="hidden"><div class="image-options-rows"></div><button class="image-option-add" type="button">Adicionar</button><p class="image-options-status"></p>
        </section><button type="submit">Salvar</button></form>`,{url:"https://app.test/admin/products/1/fields",runScripts:"outside-only"});
    dom.window.document.querySelector<HTMLInputElement>("[name=imageOptionsJson]")!.value=JSON.stringify(options);
    const fetch=vi.fn();Object.assign(dom.window,{fetch});dom.window.eval(source);return fetch;
}
const buttons=()=>[...dom.window.document.querySelectorAll<HTMLButtonElement>(".image-option-row button")];
const options=()=>JSON.parse(dom.window.document.querySelector<HTMLInputElement>("[name=imageOptionsJson]")!.value);
it("removes/reorders options in saved JSON and leaves other options intact",()=>{
    boot([{id:"a",label:"Floral"},{id:"b",label:"Azul"}]);buttons()[1].click();expect(options().map((o:{id:string})=>o.id)).toEqual(["b","a"]);
    buttons()[2].click();expect(options()).toEqual([{id:"a",label:"Floral"}]);
});
it("preserves the current image during upload failure and replaces it only on success",async()=>{
    const fetch=boot([{id:"old",label:"Floral"}]);
    const input=dom.window.document.querySelector<HTMLInputElement>("[type=file]")!;
    Object.defineProperty(input,"files",{value:[new dom.window.File(["image"],"capa.png",{type:"image/png"})],configurable:true});
    fetch.mockRejectedValueOnce(new Error("Network error"));input.dispatchEvent(new dom.window.Event("change"));
    expect(dom.window.document.querySelector<HTMLButtonElement>("button[type=submit]")!.disabled).toBe(true);
    await vi.waitFor(()=>expect(dom.window.document.querySelector(".image-options-status")!.textContent).toBe("Falha no envio"));
    expect(options()[0].id).toBe("old");
    fetch.mockResolvedValueOnce({ok:true,json:async()=>({id:"new"})});input.dispatchEvent(new dom.window.Event("change"));
    await vi.waitFor(()=>expect(options()[0].id).toBe("new"));
});
it("blocks saving incomplete options",()=>{
    boot([{id:"",label:"Floral"}]);const event=new dom.window.Event("submit",{cancelable:true});
    dom.window.document.querySelector("form")!.dispatchEvent(event);expect(event.defaultPrevented).toBe(true);
});

it("uses the plan option ceiling and prevents saving an excess configuration",()=>{
    boot([{id:"a",label:"A"},{id:"b",label:"B"},{id:"c",label:"C"}],3);
    expect(dom.window.document.querySelector<HTMLButtonElement>(".image-option-add")!.disabled).toBe(true);
    boot([{id:"a",label:"A"},{id:"b",label:"B"},{id:"c",label:"C"},{id:"d",label:"D"}],3);
    const event=new dom.window.Event("submit",{cancelable:true});
    dom.window.document.querySelector("form")!.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
    expect(dom.window.document.querySelector(".image-options-status")!.textContent).toBe("Limite do plano");
});
it("disables uploading when images are unavailable for the plan",()=>{
    boot([{id:"a",label:"A"}],0,false);
    expect(dom.window.document.querySelector<HTMLInputElement>("[type=file]")!.disabled).toBe(true);
    expect(dom.window.document.querySelector<HTMLButtonElement>(".image-option-add")!.disabled).toBe(true);
});
