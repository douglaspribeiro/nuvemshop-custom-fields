import { readFileSync } from "node:fs";
import { JSDOM } from "jsdom";
import { afterEach, expect, it, vi } from "vitest";
import { MOBILE_IMAGE_OPTIONS_CSS } from "./image-options-layout";

const source = readFileSync(new URL("../../../resources/static/assets/nuvemshop-personalizer.js", import.meta.url), "utf8");
let dom: JSDOM;
afterEach(() => dom?.window.close());

async function boot(theme?: string, lightspeed = true, lsTheme?: string) {
	dom = new JSDOM(`<html><head>
		<script src="https://campos-personalizados.wzhub.pro/assets/nuvemshop-personalizer.js?store=123"></script>
		${lightspeed ? '<script src="https://lightspeed-cdn.mitiendanube.com/v1/theme.js"></script>' : ""}
		</head><body><form id="product_form" data-product-id="456"><button type="submit">Comprar</button></form></body></html>`,
		{ url: "https://store.test/productos/example", runScripts: "outside-only" });
	const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ enabled: false }) });
	Object.assign(dom.window, { fetch,
		nubeSDK: theme === undefined ? undefined : { getState: () => ({ store: { theme } }) },
		LS: lsTheme ? { theme: { code: lsTheme } } : undefined,
	});
	Object.defineProperty(dom.window.document, "readyState", { value: "complete", configurable: true });
	dom.window.eval(source);
	await Promise.resolve(); await Promise.resolve();
	return fetch;
}

it("routes only an explicitly identified Patagonia to the new adapter", async () => {
	const fetch = await boot("patagonia");
	expect(dom.window.document.getElementById("ncf-patagonia-script")?.getAttribute("src"))
		.toBe("https://campos-personalizados.wzhub.pro/assets/nuvemshop-patagonia.js");
	expect(fetch.mock.calls.some(([url]) => String(url).includes("/personalization?"))).toBe(false);
});

it("preserves the legacy product flow for another theme even on Lightspeed", async () => {
	const fetch = await boot("nuvem");
	expect(dom.window.document.getElementById("ncf-patagonia-script")).toBeNull();
	expect(fetch.mock.calls.some(([url]) => String(url).includes("/personalization?productId=456"))).toBe(true);
});

it("preserves stores without the SDK or Lightspeed", async () => {
	const fetch = await boot(undefined, false);
	expect(dom.window.document.getElementById("ncf-patagonia-script")).toBeNull();
	expect(fetch.mock.calls.some(([url]) => String(url).includes("/personalization?productId=456"))).toBe(true);
});

it("does not assume Patagonia from the CDN while the theme is still unknown", async () => {
	const fetch = await boot();
	expect(dom.window.document.getElementById("ncf-patagonia-script")).toBeNull();
	expect(fetch.mock.calls.some(([url]) => String(url).includes("/personalization?"))).toBe(false);
});

it("recognizes an explicit LS theme before the SDK is ready", async () => {
	await boot(undefined, true, "patagonia");
	expect(dom.window.document.getElementById("ncf-patagonia-script")).not.toBeNull();
});

it.each([
    {showImageZoom:undefined,mobileImageColumns:undefined},
    {showImageZoom:true,mobileImageColumns:2},
    {showImageZoom:false,mobileImageColumns:3},
])("renders traditional image selection with %o", async ({showImageZoom,mobileImageColumns}) => {
    dom=new JSDOM(`<html lang="pt-BR"><head><script src="https://campos-personalizados.wzhub.pro/assets/nuvemshop-personalizer.js?store=123"></script></head>
        <body><form id="product_form" data-product-id="456"><button type="submit">Comprar</button></form></body></html>`,
        {url:"https://store.test/productos/example",runScripts:"outside-only"});
    Object.assign(dom.window,{fetch:vi.fn().mockResolvedValue({ok:true,json:async()=>({enabled:true,fields:[
        {label:"Capa",propertyName:"Capa",fieldType:"IMAGE_SELECT",required:true,options:["Floral"],showImageZoom,mobileImageColumns,
            imageOptions:[{label:"Floral",thumbnailUrl:"/public/thumb",imageUrl:"/public/large"}]}],style:{}})})});
    Object.defineProperty(dom.window.document,"readyState",{value:"complete",configurable:true});dom.window.eval(source);
    await vi.waitFor(()=>expect(dom.window.document.querySelector("button[data-value='Floral']")).not.toBeNull());
    const option=dom.window.document.querySelector<HTMLButtonElement>("button[data-value='Floral']")!;option.click();
    const select=dom.window.document.querySelector<HTMLSelectElement>("select[name='properties[Capa]']")!;
    expect(select.value).toBe("Floral");expect(option.getAttribute("aria-pressed")).toBe("true");
    expect(dom.window.document.querySelector<HTMLElement>(".ncf-image-options")?.dataset.mobileColumns)
        .toBe(mobileImageColumns ? String(mobileImageColumns) : undefined);
    if (mobileImageColumns) expect(dom.window.document.getElementById("ncf-mobile-image-styles")?.textContent).toBe(MOBILE_IMAGE_OPTIONS_CSS);
    expect(dom.window.document.querySelector(".ncf-field a")?.getAttribute("href"))
        .toBe(showImageZoom === false ? undefined : "https://campos-personalizados.wzhub.pro/public/large");
});

async function bootSelect(placeholder: string | null, lang = "es-AR") {
    dom = new JSDOM(`<html lang="${lang}"><head><script src="https://campos-personalizados.wzhub.pro/assets/nuvemshop-personalizer.js?store=123"></script></head>
        <body><form id="product_form" data-product-id="456"><button class="js-addtocart" type="submit"><span>Comprar</span></button></form></body></html>`,
        { url: "https://store.test/productos/example", runScripts: "outside-only" });
    Object.assign(dom.window, { fetch: vi.fn().mockResolvedValue({ ok: true, json: async () => ({ enabled: true,
        fields: [{ label: "Moagem", fieldType: "SELECT", required: false, placeholder, options: ["Grano", "Molido"] }], style: {} }) }) });
    Object.defineProperty(dom.window.document, "readyState", { value: "complete", configurable: true });
    dom.window.eval(source);
    await vi.waitFor(() => expect(dom.window.document.querySelector(".ncf-field select")).not.toBeNull());
    return dom.window.document.querySelector<HTMLSelectElement>(".ncf-field select")!;
}

it("renders the configured prompt as an empty value and uses translated defaults", async () => {
    const select = await bootSelect("Elegí la molienda");
    expect(select.options[0].textContent).toBe("Elegí la molienda");
    expect(select.value).toBe("");
    dom.window.close();
    expect((await bootSelect("   ")).options[0].textContent).toBe("Seleccioná una opción");
    dom.window.close();
    expect((await bootSelect(null, "pt-BR")).options[0].textContent).toBe("Selecione uma opção");
});

it("blocks AJAX purchase clicks and form submissions until a list item is selected", async () => {
    const select = await bootSelect("Elegí la molienda");
    const button = dom.window.document.querySelector<HTMLButtonElement>(".js-addtocart")!;
    const nativeAdd = vi.fn();
    button.addEventListener("click", event => { event.preventDefault(); nativeAdd(); });
    const click = () => button.querySelector("span")!.dispatchEvent(new dom.window.MouseEvent("click", { bubbles: true, cancelable: true }));
    expect(select.required).toBe(true);
    expect(click()).toBe(false);
    expect(nativeAdd).not.toHaveBeenCalled();
    const submit = new dom.window.Event("submit", { bubbles: true, cancelable: true });
    button.form!.dispatchEvent(submit);
    expect(submit.defaultPrevented).toBe(true);
    select.value = "Molido";
    expect(select.checkValidity()).toBe(true);
    click();
    expect(nativeAdd).toHaveBeenCalledTimes(1);
    expect(select.value).toBe("Molido");
});

it.each([true, false])('sends only checked checkbox properties and enforces required=%s', async (required) => {
    dom = new JSDOM(`<html lang="pt-BR"><head><script src="https://campos-personalizados.wzhub.pro/assets/nuvemshop-personalizer.js?store=123"></script></head>
        <body><form id="product_form" data-product-id="456"><button type="submit">Comprar</button></form></body></html>`,
        {url:'https://store.test/productos/example',runScripts:'outside-only'});
    Object.assign(dom.window,{fetch:vi.fn().mockResolvedValue({ok:true,json:async()=>({enabled:true,
        fields:[{label:'Embrulhar',fieldType:'CHECKBOX',required}],style:{}})})});
    Object.defineProperty(dom.window.document,'readyState',{value:'complete',configurable:true});dom.window.eval(source);
    await vi.waitFor(()=>expect(dom.window.document.querySelector('.ncf-field input')).not.toBeNull());
    const form = dom.window.document.querySelector('form')!;
    const checkbox = form.querySelector<HTMLInputElement>('.ncf-field input')!;
    const submit = () => { const event = new dom.window.Event('submit',{cancelable:true});form.dispatchEvent(event);return event; };
    const properties = () => new dom.window.FormData(form).get('properties[Embrulhar]');
    expect(checkbox.type).toBe('checkbox');expect(checkbox.checked).toBe(false);
    expect(properties()).toBeNull();expect(submit().defaultPrevented).toBe(required);
    checkbox.click();expect(properties()).toBe('Sim');expect(submit().defaultPrevented).toBe(false);
    checkbox.click();expect(properties()).toBeNull();expect(submit().defaultPrevented).toBe(required);
});

it.each([true,false])('serializes all selected legacy options and requires at least one when required=%s',async(required)=>{
    dom=new JSDOM(`<html lang="pt-BR"><head><script src="https://campos-personalizados.wzhub.pro/assets/nuvemshop-personalizer.js?store=123"></script></head>
        <body><form id="product_form" data-product-id="456"><button type="submit">Comprar</button></form></body></html>`,
        {url:'https://store.test/productos/example',runScripts:'outside-only'});
    Object.assign(dom.window,{fetch:vi.fn().mockResolvedValue({ok:true,json:async()=>({enabled:true,
        fields:[{label:'Adicionais',fieldType:'CHECKBOX',required,options:['Laço','Cartão, impresso','Embalagem']}],style:{}})})});
    Object.defineProperty(dom.window.document,'readyState',{value:'complete',configurable:true});dom.window.eval(source);
    await vi.waitFor(()=>expect(dom.window.document.querySelectorAll('.ncf-field input[type=checkbox]')).toHaveLength(3));
    const form=dom.window.document.querySelector('form')!;
    const boxes=[...form.querySelectorAll<HTMLInputElement>('input[type=checkbox]')];
    const submit=()=>{const e=new dom.window.Event('submit',{cancelable:true});form.dispatchEvent(e);return e;};
    const properties=()=>new dom.window.FormData(form).getAll('properties[Adicionais]');
    expect(properties()).toEqual([]);expect(submit().defaultPrevented).toBe(required);
    boxes[0].click();boxes[1].click();
    expect(boxes.map(box=>box.checked)).toEqual([true,true,false]);
    expect(properties()).toEqual(['Laço, Cartão, impresso']);expect(submit().defaultPrevented).toBe(false);
    boxes[0].click();expect(properties()).toEqual(['Cartão, impresso']);expect(submit().defaultPrevented).toBe(false);
    boxes[1].click();expect(properties()).toEqual([]);expect(submit().defaultPrevented).toBe(required);
});


it("disables native properties on plain variants and returns to empty required fields",async()=>{
    dom=new JSDOM(`<head><script src="https://campos-personalizados.wzhub.pro/assets/nuvemshop-personalizer.js?store=123"></script></head>
        <body><form id="product_form" data-product-id="456"><input name="variant_id" value="101"><button type="submit">Comprar</button></form></body>`,
        {url:"https://store.test/products/test",runScripts:"outside-only"});
    Object.assign(dom.window,{fetch:vi.fn().mockResolvedValue({ok:true,json:async()=>({enabled:true,variantRestricted:true,variantIds:[102],
        fields:[{label:"Nome",propertyName:"Nome",fieldType:"TEXT",required:true}],style:{}})})});
    Object.defineProperty(dom.window.document,"readyState",{value:"complete",configurable:true});dom.window.eval(source);
    const form=dom.window.document.querySelector("form")!;
    await vi.waitFor(()=>expect(form.querySelector(".ncf-personalization")).not.toBeNull());
    const variant=form.querySelector<HTMLInputElement>('[name="variant_id"]')!;
    const input=form.querySelector<HTMLInputElement>('[name="properties[Nome]"]')!;
    const change=(id:string)=>{variant.value=id;variant.dispatchEvent(new dom.window.Event("change",{bubbles:true}));};
    expect(input.disabled).toBe(true);expect(new dom.window.FormData(form).has("properties[Nome]")).toBe(false);
    change("102");expect(input.disabled).toBe(false);input.value="Ana";
    change("101");expect(input.value).toBe("");expect(new dom.window.FormData(form).has("properties[Nome]")).toBe(false);
    change("102");expect(input.value).toBe("");
    const event=new dom.window.Event("submit",{cancelable:true});form.dispatchEvent(event);expect(event.defaultPrevented).toBe(true);
});
