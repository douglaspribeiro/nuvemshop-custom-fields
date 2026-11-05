import { readFileSync } from "node:fs";
import { JSDOM } from "jsdom";
import { afterEach, expect, it, vi } from "vitest";

// JSDOM has no page navigation; replace only that browser boundary with a spy.
const source = readFileSync(new URL("../../../resources/static/assets/nuvemshop-personalizer.js", import.meta.url), "utf8")
    .replace("window.location.assign(url);", "window.__navigate(url);");
let dom: JSDOM;
afterEach(() => dom?.window.close());

// Idea markup observed in LAB Cafe on 2026-11-03.
function card(id: number) {
    return `<div class="js-item-product" data-product-type="list" data-product-id="${id}">
        <div class="js-quickshop-container">
            <a class="item-link" href="/productos/cafe-${id}/">Café</a>
            <a href="#" class="js-item-buy-open"><svg><use></use></svg></a>
            <form class="js-product-form" action="/comprar/" method="post">
                <input type="hidden" name="add_to_cart" value="${id}">
                <input type="submit" class="js-addtocart" value="Agregar al carrito">
            </form>
        </div></div>`;
}

async function boot(options: { store?: number; policy?: (id: number) => Promise<unknown>; pdp?: boolean; cart?: boolean } = {}) {
    dom = new JSDOM(`<html lang="es"><head><script src="https://campos-personalizados.wzhub.pro/assets/nuvemshop-personalizer.js?store=${options.store ?? 959276}"></script></head>
        <body>${options.pdp ? '<form id="product_form" data-product-id="333"><button type="submit">Comprar</button></form>' : ""}
        ${options.cart ? '<form action="/comprar/" class="js-ajax-cart-panel"><input type="submit" name="go_to_checkout" value="Iniciar compra"></form><form action="/search/"><input name="q"></form>' : ""}
        ${card(44960054)}${card(128433426)}</body></html>`,
        { url: "https://shop.labcafe.com.ar/" + (options.pdp ? "productos/actual/" : ""), runScripts: "outside-only" });
    const navigate = vi.fn();
    const fetch = vi.fn(async (url: string) => {
        const id = Number(new URL(url).searchParams.get("productId"));
        return { ok: true, json: async () => id
            ? options.policy ? options.policy(id) : { enabled: id === 44960054, fields: [{label: "Molienda", fieldType: "TEXT", required: true}] }
            : {} };
    });
    Object.assign(dom.window, { fetch, __navigate: navigate, LS: {
        theme: { code: "idea" }, ...(options.pdp ? {product: {id: 333}} : {})
    } });
    Object.defineProperty(dom.window.document, "readyState", { value: "complete", configurable: true });
    dom.window.eval(source);
    await new Promise(resolve => setTimeout(resolve, 0));
    return { navigate, fetch, document: dom.window.document };
}

it("shows fields before opening the native quick-buy panel without navigation", async () => {
 const {document,navigate}=await boot(); const theme=vi.fn(e=>e.preventDefault());
 const opener=document.querySelector<HTMLElement>(".js-item-buy-open")!;opener.addEventListener("click",theme);opener.click();opener.click();
 await vi.waitFor(()=>expect(theme).toHaveBeenCalledTimes(1));
 expect(document.querySelectorAll(".ncf-field")).toHaveLength(1);expect(navigate).not.toHaveBeenCalled();
});
it("blocks empty required input and submits completed properties through the native form", async () => {
 const {document}=await boot();document.querySelector<HTMLElement>(".js-item-buy-open")!.click();
 await vi.waitFor(()=>expect(document.querySelector(".ncf-field input")).not.toBeNull());
 const form=document.querySelector("form")!;const submit=form.querySelector<HTMLInputElement>("input[type=submit]")!;
 const theme=vi.fn(e=>e.preventDefault());form.addEventListener("click",theme);
 submit.click();expect(theme).not.toHaveBeenCalled();
 const input=form.querySelector<HTMLInputElement>(".ncf-field input")!;input.value="En grano";
 submit.click();expect(theme).toHaveBeenCalledTimes(1);
 expect(new dom.window.FormData(form).get("properties[Molienda]")).toBe("En grano");
 document.querySelector<HTMLElement>(".js-item-buy-open")!.click();
 expect(form.querySelectorAll(".ncf-field")).toHaveLength(1);expect(input.value).toBe("En grano");
});
it("does not add an item on a first direct submit before fields load", async () => {
 const {document,navigate}=await boot();const form=document.querySelector("form")!;const theme=vi.fn(e=>e.preventDefault());form.addEventListener("submit",theme);
 form.requestSubmit(form.querySelector<HTMLInputElement>("input[type=submit]")!);
 await vi.waitFor(()=>expect(form.querySelector(".ncf-field")).not.toBeNull());expect(theme).not.toHaveBeenCalled();expect(navigate).not.toHaveBeenCalled();
});
it("retains native quick buy and original submitter for a product without fields", async () => {
 const {document,navigate}=await boot();const form=document.querySelectorAll("form")[1];const button=form.querySelector<HTMLInputElement>("input[type=submit]")!;
 const theme=vi.fn(e=>e.preventDefault());form.addEventListener("submit",theme);form.requestSubmit(button);
 await vi.waitFor(()=>expect(theme).toHaveBeenCalledTimes(1));expect(theme.mock.calls[0][0].submitter).toBe(button);expect(navigate).not.toHaveBeenCalled();
});
it("uses the recommendation card ID instead of the current product and preserves separate forms", async () => {
 const {document,navigate}=await boot({pdp:true,policy:async id=>({enabled:id===555,fields:[{label:"Molienda",fieldType:"TEXT",required:true}]})});
 document.body.insertAdjacentHTML("beforeend",card(555));document.querySelector<HTMLElement>('[data-product-id="555"] .js-item-buy-open')!.click();
 await vi.waitFor(()=>expect(document.querySelector('[data-product-id="555"] .ncf-field')).not.toBeNull());
 expect(document.querySelector("#product_form .ncf-field")).toBeNull();expect(navigate).not.toHaveBeenCalled();
 const form=document.querySelector<HTMLFormElement>('[data-product-id="555"] form')!;
 form.querySelector<HTMLInputElement>(".ncf-field input")!.value="Molido";
 form.addEventListener("submit",e=>e.preventDefault());form.requestSubmit();
 expect(new dom.window.FormData(form).get("properties[Molienda]")).toBe("Molido");
});
it.each([123,959276,5656805])("shows quick-buy fields for store %s",async store=>{
 const {document,navigate}=await boot({store});document.querySelector<HTMLElement>(".js-item-buy-open")!.click();
 await vi.waitFor(()=>expect(document.querySelector(".ncf-field")).not.toBeNull());expect(navigate).not.toHaveBeenCalled();
});
it("falls back to the product page on lookup failure",async()=>{
 const {document,navigate}=await boot({policy:async()=>{throw Error("offline");}});document.querySelector<HTMLElement>(".js-item-buy-open")!.click();
 await vi.waitFor(()=>expect(navigate).toHaveBeenCalledTimes(1));expect(document.querySelector(".ncf-field")).toBeNull();
});
it("falls back safely when a theme has no local quick-buy form",async()=>{
 const {document,navigate}=await boot();document.querySelector("form")!.remove();document.querySelector<HTMLElement>(".js-item-buy-open")!.click();
 await vi.waitFor(()=>expect(navigate).toHaveBeenCalledTimes(1));
});
it("does not fetch the entire catalog on startup",async()=>{
 const {fetch}=await boot();expect(fetch.mock.calls.filter(([url])=>url.includes("/personalization?"))).toHaveLength(0);
});

it("warms only interested cards and keeps the PDP purchase untouched",async()=>{
 const {document,fetch}=await boot({pdp:true});const form=document.querySelector<HTMLFormElement>("#product_form")!;
 const theme=vi.fn(e=>e.preventDefault());form.addEventListener("submit",theme);form.requestSubmit();expect(theme).toHaveBeenCalledTimes(1);
 const opener=document.querySelector<HTMLElement>(".js-item-buy-open")!;
 opener.dispatchEvent(new dom.window.Event("pointerover",{bubbles:true}));opener.dispatchEvent(new dom.window.Event("focusin",{bubbles:true}));
 await vi.waitFor(()=>expect(fetch.mock.calls.filter(([url])=>url.includes("productId=44960054"))).toHaveLength(1));
 expect(fetch.mock.calls.some(([url])=>url.includes("productId=128433426"))).toBe(false);
});
it("supports component-marked cards and modal opening buttons",async()=>{
 const {document,navigate}=await boot();const product=document.querySelector<HTMLElement>(".js-item-product")!;
 product.className="other-theme";product.dataset.component="product-list-item";
 const opener=product.querySelector<HTMLElement>(".js-item-buy-open")!;opener.className="js-quickshop-modal-open";opener.click();
 await vi.waitFor(()=>expect(product.querySelector(".ncf-field")).not.toBeNull());expect(navigate).not.toHaveBeenCalled();
});
it("validates select choices and keeps answers isolated between products",async()=>{
 const {document}=await boot({policy:async()=>({enabled:true,fields:[{label:"Molienda",fieldType:"SELECT",required:true,options:["Grano","Molido"]}]})});
 document.querySelectorAll<HTMLElement>(".js-item-buy-open").forEach(b=>b.click());
 await vi.waitFor(()=>expect(document.querySelectorAll(".ncf-field select")).toHaveLength(2));
 const forms=document.querySelectorAll("form");const selected=forms[0].querySelector("select")!;selected.value="Grano";
 const theme=vi.fn(e=>e.preventDefault());forms[0].addEventListener("click",theme);forms[0].querySelector<HTMLElement>("input[type=submit]")!.click();
 expect(theme).toHaveBeenCalledTimes(1);expect(forms[1].querySelector("select")!.value).toBe("");
 const forged=document.createElement("option");forged.value="Invalid";selected.append(forged);selected.value="Invalid";
 forms[0].querySelector<HTMLElement>("input[type=submit]")!.click();expect(theme).toHaveBeenCalledTimes(1);
});


it("never injects required fields into the cart or search form on a listing", async () => {
 const {document}=await boot({cart:true});
 document.querySelector<HTMLElement>(".js-item-buy-open")!.click();
 await vi.waitFor(()=>expect(document.querySelector('.js-item-product .ncf-field')).not.toBeNull());
 expect(document.querySelector('.js-ajax-cart-panel .ncf-personalization')).toBeNull();
 expect(document.querySelector('form[action="/search/"] .ncf-personalization')).toBeNull();
 const checkout=document.querySelector<HTMLFormElement>('.js-ajax-cart-panel')!;
 const submit=vi.fn(e=>e.preventDefault());checkout.addEventListener('submit',submit);checkout.requestSubmit();
 expect(submit).toHaveBeenCalledTimes(1);
});
it("keeps PDP fields inside the product form when a cart form is also present",async()=>{
 const {document}=await boot({pdp:true,cart:true,policy:async()=>({enabled:true,fields:[{label:"Nome",fieldType:"TEXT",required:true}]})});
 await vi.waitFor(()=>expect(document.querySelector('#product_form .ncf-field')).not.toBeNull());
 expect(document.querySelector('.js-ajax-cart-panel .ncf-personalization')).toBeNull();
});

it("uses persisted line identity for different choices of the same product, ignoring local drafts",async()=>{
 const {document}=await boot();
 const ls=(dom.window as unknown as {LS:{cart:unknown}}).LS;
 ls.cart={items:[
   {id:11,product_id:44960054,quantity:1,properties:{Molienda:"En Grano"}},
   {id:22,product_id:44960054,quantity:6,properties:[{name:"Molienda",value:"Molido"}]},
 ]};
 dom.window.localStorage.setItem("ncf:cart-properties:959276",JSON.stringify([{productId:44960054,productName:"Café",fields:[{label:"Molienda",value:"Molido"}],createdAt:Date.now()}]));
 document.body.insertAdjacentHTML("beforeend",'<div class="js-cart-item" data-item-id="11"><a class="js-cart-item-name">Café</a></div><div class="js-cart-item" data-item-id="22"><a class="js-cart-item-name">Café</a></div><div class="js-cart-item"><a class="js-cart-item-name">Café sin identidad</a></div>');
 await vi.waitFor(()=>expect(document.querySelector('[data-item-id="11"] .ncf-cart-property-list')?.textContent).toBe("Molienda: En Grano"));
 expect(document.querySelector('[data-item-id="22"] .ncf-cart-property-list')?.textContent).toBe("Molienda: Molido");
 expect(document.querySelectorAll('.ncf-cart-property-list')).toHaveLength(2);
 // Reordering rows must not swap the answers.
 document.body.append(document.querySelector('[data-item-id="11"]')!);
 await new Promise(resolve=>setTimeout(resolve,150));
 expect(document.querySelector('[data-item-id="11"] .ncf-cart-property-list')?.textContent).toBe("Molienda: En Grano");
 // A native cart refresh must replace an outdated label.
 (ls.cart as {items:unknown[]}).items=[{id:22,properties:{Molienda:"En Grano"}}];
 document.body.append(document.createElement('span'));
 await vi.waitFor(()=>expect(document.querySelector('[data-item-id="22"] .ncf-cart-property-list')?.textContent).toBe("Molienda: En Grano"));
 expect(document.querySelector('[data-item-id="11"] .ncf-cart-property-list')).toBeNull();
});

it("refreshes cart labels from the native productAddedToCart event payload",async()=>{
 const {document}=await boot();
 const ls=(dom.window as unknown as {LS:Record<string,unknown>}).LS;
 ls.events={productAddedToCart:'productAddedToCart'};
 ls.on=(name:string,callback:EventListener)=>document.addEventListener(name,callback);
 ls.cart={items:[{id:22,properties:{Molienda:'En Grano'}}]};
 document.body.insertAdjacentHTML('beforeend','<div class="js-cart-item" data-item-id="22"><a class="js-cart-item-name">Café</a></div>');
 await vi.waitFor(()=>expect(document.querySelector('.ncf-cart-property-list')?.textContent).toBe('Molienda: En Grano'));
 document.dispatchEvent(new dom.window.CustomEvent('productAddedToCart',{detail:{cart:{products:[{id:22,quantity:6,properties:{Molienda:'Molido'}}]}}}));
 expect(document.querySelector('.ncf-cart-property-list')?.textContent).toBe('Molienda: Molido');
 expect(document.querySelectorAll('.ncf-cart-property-list')).toHaveLength(1);
});
