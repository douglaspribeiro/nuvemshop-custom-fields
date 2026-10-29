import { readFileSync } from "node:fs";
import { JSDOM } from "jsdom";
import { afterEach, it, expect, vi } from "vitest";

const source=readFileSync(new URL("../../../resources/static/assets/store-templates.js",import.meta.url),"utf8");
let dom:JSDOM;
afterEach(()=>dom?.window.close());
function boot(markup:string,model:unknown=null) {
    dom=new JSDOM(`<html><body>${markup}<p id="template-status"></p><div id="template-ui" data-failed="Falhou" data-field="Campo" data-image-wait="Aguarde as imagens" data-delete-confirm="Confirma exclusão?" data-copy-prompt="Nome"></div></body></html>`,{url:"https://app.test/admin/templates",runScripts:"outside-only"});
    const fetch=vi.fn().mockRejectedValue(new Error("Falha de rede"));
    Object.assign(dom.window,{fetch,ncfTemplateEditor:{template:model},ncfInitFieldEditor:vi.fn(),ncfInitOptionImages:vi.fn()});
    dom.window.eval(source);return fetch;
}
const row=`<fieldset><legend data-template-heading></legend><input name="label" required><select name="fieldType"><option>TEXT</option><option>CHECKBOX</option></select><input name="required" type="checkbox"><input name="maxLength" value="100"><input name="placeholder"><input name="validationPattern"><textarea name="optionsText"></textarea><input name="imageOptionsJson"><input name="showImageZoom" type="checkbox"><select name="mobileImageColumns"><option value=""></option></select><section class="image-options-editor" data-uploading-count="0"></section><button type="button" data-field-remove>Remover</button><button type="button" data-field-up>Subir</button><button type="button" data-field-down>Descer</button></fieldset>`;
const editor=`<form id="template-editor"><input name="templateName" required><textarea name="templateDescription"></textarea><div id="template-fields"></div><button type="button" id="template-add-field">Adicionar</button><button type="submit">Salvar</button></form><template id="template-field-row">${row}</template>`;
it("reuses field/image editors, preserves order and sends definitions without buyer values",async()=>{
    const fetch=boot(editor,{id:3,name:"Modelo",revision:2,fields:[{label:"Primeiro",fieldType:"TEXT",maxLength:40,required:true},{label:"Segundo",fieldType:"CHECKBOX",maxLength:100,optionsText:"A\nB",buyerValue:"nunca copiar"}]});
    const doc=dom.window.document;
    expect(dom.window.ncfInitFieldEditor).toHaveBeenCalledTimes(2);
    doc.querySelectorAll<HTMLButtonElement>("[data-field-up]")[1].click();
    doc.getElementById("template-editor")!.dispatchEvent(new dom.window.Event("submit",{bubbles:true,cancelable:true}));
    await vi.waitFor(()=>expect(fetch).toHaveBeenCalledOnce());
    const payload=JSON.parse(fetch.mock.calls[0][1].body);
    expect(payload.revision).toBe(2);expect(payload.fields.map((f:{label:string})=>f.label)).toEqual(["Segundo","Primeiro"]);
    expect(JSON.stringify(payload)).not.toContain("buyerValue");
    expect(fetch.mock.calls[0][1].headers["X-NCF-Action"]).toBe("template-write");
    await vi.waitFor(()=>expect(doc.getElementById("template-status")!.textContent).toBe("Falha de rede"));
    expect(doc.querySelectorAll("#template-fields > fieldset")).toHaveLength(2);
});
it("blocks save while an image upload is incomplete",()=>{
    const fetch=boot(editor,{id:3,name:"Modelo",revision:2,fields:[{label:"Nome",fieldType:"TEXT",maxLength:40}]});
    dom.window.document.querySelector<HTMLElement>(".image-options-editor")!.dataset.uploadingCount="1";
    dom.window.document.getElementById("template-editor")!.dispatchEvent(new dom.window.Event("submit",{cancelable:true}));
    expect(fetch).not.toHaveBeenCalled();expect(dom.window.document.getElementById("template-status")!.textContent).toBe("Aguarde as imagens");
});
it("requires explicit replace confirmation and keeps the same request id on retry",async()=>{
    const fetch=boot(`<form id="template-apply" data-key="own:3" data-product-id="123" data-review-token="review" data-request-id="request"><input type="radio" name="mode" value="APPEND" required><input type="radio" name="mode" value="REPLACE" required><label id="template-replace-confirm" hidden><input type="checkbox" name="confirmed"></label><button type="submit">Aplicar</button></form>`);
    const doc=dom.window.document,form=doc.getElementById("template-apply")!;
    const replace=doc.querySelector<HTMLInputElement>("[value=REPLACE]")!;replace.click();
    expect(doc.querySelector<HTMLInputElement>("[name=confirmed]")!.required).toBe(true);
    form.dispatchEvent(new dom.window.Event("submit",{cancelable:true}));expect(fetch).not.toHaveBeenCalled();
    doc.querySelector<HTMLInputElement>("[name=confirmed]")!.checked=true;
    form.dispatchEvent(new dom.window.Event("submit",{cancelable:true}));
    await vi.waitFor(()=>expect(doc.querySelector<HTMLButtonElement>("button[type=submit]")!.disabled).toBe(false));
    form.dispatchEvent(new dom.window.Event("submit",{cancelable:true}));await vi.waitFor(()=>expect(fetch).toHaveBeenCalledTimes(2));
    expect(fetch.mock.calls.map(call=>JSON.parse(call[1].body).requestId)).toEqual(["request","request"]);
});
it("never deletes a template when confirmation is declined",()=>{
    const fetch=boot(`<button data-template-delete data-id="3" data-revision="2">Excluir</button>`);
    dom.window.confirm=vi.fn(()=>false);dom.window.document.querySelector<HTMLButtonElement>("button")!.click();
    expect(fetch).not.toHaveBeenCalled();
});
