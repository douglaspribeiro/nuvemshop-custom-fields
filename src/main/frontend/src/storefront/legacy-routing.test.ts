import { readFileSync } from "node:fs";
import { JSDOM } from "jsdom";
import { afterEach, expect, it, vi } from "vitest";

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

it("renders image selection in traditional themes with native form properties", async () => {
    dom=new JSDOM(`<html lang="pt-BR"><head><script src="https://campos-personalizados.wzhub.pro/assets/nuvemshop-personalizer.js?store=123"></script></head>
        <body><form id="product_form" data-product-id="456"><button type="submit">Comprar</button></form></body></html>`,
        {url:"https://store.test/productos/example",runScripts:"outside-only"});
    Object.assign(dom.window,{fetch:vi.fn().mockResolvedValue({ok:true,json:async()=>({enabled:true,fields:[
        {label:"Capa",propertyName:"Capa",fieldType:"IMAGE_SELECT",required:true,options:["Floral"],
            imageOptions:[{label:"Floral",thumbnailUrl:"/public/thumb",imageUrl:"/public/large"}]}],style:{}})})});
    Object.defineProperty(dom.window.document,"readyState",{value:"complete",configurable:true});dom.window.eval(source);
    await vi.waitFor(()=>expect(dom.window.document.querySelector("button[data-value='Floral']")).not.toBeNull());
    const option=dom.window.document.querySelector<HTMLButtonElement>("button[data-value='Floral']")!;option.click();
    const select=dom.window.document.querySelector<HTMLSelectElement>("select[name='properties[Capa]']")!;
    expect(select.value).toBe("Floral");expect(option.getAttribute("aria-pressed")).toBe("true");
    expect(dom.window.document.querySelector(".ncf-field a")?.getAttribute("href"))
        .toBe("https://campos-personalizados.wzhub.pro/public/large");
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
