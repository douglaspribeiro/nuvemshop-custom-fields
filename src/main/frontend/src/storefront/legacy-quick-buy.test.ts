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

async function boot(options: { store?: number; policy?: (id: number) => Promise<unknown>; pdp?: boolean } = {}) {
    dom = new JSDOM(`<html lang="es"><head><script src="https://campos-personalizados.wzhub.pro/assets/nuvemshop-personalizer.js?store=${options.store ?? 959276}"></script></head>
        <body>${options.pdp ? '<form id="product_form" data-product-id="333"><button type="submit">Comprar</button></form>' : ""}
        ${card(44960054)}${card(128433426)}</body></html>`,
        { url: "https://shop.labcafe.com.ar/" + (options.pdp ? "productos/actual/" : ""), runScripts: "outside-only" });
    const navigate = vi.fn();
    const fetch = vi.fn(async (url: string) => {
        const id = Number(new URL(url).searchParams.get("productId"));
        return { ok: true, json: async () => id
            ? options.policy ? options.policy(id) : { enabled: id === 44960054, fields: [{label: "Molienda", fieldType: "TEXT"}] }
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

it("redirects a personalized card before the theme can open quick buy or add an item", async () => {
    const {navigate, document} = await boot();
    const theme = vi.fn();
    document.addEventListener("click", theme);
    document.querySelector("use")!.dispatchEvent(new dom.window.MouseEvent("click", { bubbles: true, cancelable: true }));
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith("https://shop.labcafe.com.ar/productos/cafe-44960054/"));
    expect(theme).not.toHaveBeenCalled();
    expect(document.querySelector(".ncf-field")).toBeNull();
});

it("keeps normal quick buy for a product without fields and reuses its policy", async () => {
    const {navigate, document, fetch} = await boot();
    const control = document.querySelectorAll(".js-item-buy-open")[1] as HTMLAnchorElement;
    const theme = vi.fn(event => event.preventDefault());
    control.addEventListener("click", theme);
    control.click();
    await vi.waitFor(() => expect(theme).toHaveBeenCalledTimes(1));
    expect(navigate).not.toHaveBeenCalled();
    expect(fetch.mock.calls.filter(([url]) => url.includes("productId=128433426"))).toHaveLength(1);
});

it("intercepts a direct submit before any native cart mutation", async () => {
    const {navigate, document} = await boot();
    const form = document.querySelector("form")!;
    const theme = vi.fn();
    form.addEventListener("submit", theme);
    form.requestSubmit(form.querySelector<HTMLInputElement>("input[type=submit]")!);
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledTimes(1));
    expect(theme).not.toHaveBeenCalled();
});

it("replays a non-personalized submit exactly once with the original submitter", async () => {
    const {navigate, document} = await boot();
    const form = document.querySelectorAll("form")[1];
    const submitter = form.querySelector<HTMLInputElement>("input[type=submit]")!;
    const theme = vi.fn(event => event.preventDefault());
    form.addEventListener("submit", theme);
    form.requestSubmit(submitter);
    await vi.waitFor(() => expect(theme).toHaveBeenCalledTimes(1));
    expect(theme.mock.calls[0][0].submitter).toBe(submitter);
    expect(navigate).not.toHaveBeenCalled();
});

it("covers dynamically inserted cards and ignores the current PDP product ID", async () => {
    const {navigate, document} = await boot({pdp: true, policy: async id => ({enabled: id === 555, fields: [{label: "Molienda"}]})});
    document.body.insertAdjacentHTML("beforeend", card(555));
    (document.querySelectorAll(".js-item-buy-open")[2] as HTMLElement).click();
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith("https://shop.labcafe.com.ar/productos/cafe-555/"));
});

it("keeps the PDP purchase action available", async () => {
    const {navigate, document} = await boot({pdp: true});
    const theme = vi.fn(event => event.preventDefault());
    const form = document.querySelector<HTMLFormElement>("#product_form")!;
    form.addEventListener("submit", theme); form.requestSubmit();
    expect(theme).toHaveBeenCalledTimes(1); expect(navigate).not.toHaveBeenCalled();
});

it("does not redirect to an external card URL", async () => {
    const {navigate, document} = await boot();
    document.querySelector(".item-link")!.setAttribute("href", "https://external.test/productos/cafe/");
    const theme = vi.fn(event => event.preventDefault());
    const control = document.querySelector<HTMLElement>(".js-item-buy-open")!;
    control.addEventListener("click", theme); control.click();
    expect(theme).toHaveBeenCalledTimes(1); expect(navigate).not.toHaveBeenCalled();
});

it("waits for an unknown policy and coalesces repeated clicks", async () => {
    let resolvePolicy!: (value: unknown) => void;
    const pending = new Promise(resolve => { resolvePolicy = resolve; });
    const {navigate, document} = await boot({policy: () => pending});
    const control = document.querySelector<HTMLElement>(".js-item-buy-open")!;
    const theme = vi.fn(); control.addEventListener("click", theme);
    control.click(); control.click();
    expect(navigate).not.toHaveBeenCalled(); expect(theme).not.toHaveBeenCalled();
    resolvePolicy({enabled: true, fields: [{label: "Molienda"}]});
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledTimes(1));
});

it("routes to the PDP when the personalization policy cannot be loaded", async () => {
    const {navigate, document} = await boot({policy: async () => { throw new Error("offline"); }});
    const control = document.querySelector<HTMLElement>(".js-addtocart")!;
    const theme = vi.fn(); control.addEventListener("click", theme); control.click();
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledTimes(1));
    expect(theme).not.toHaveBeenCalled();
});

it.each([123, 959276, 5656805])("redirects configured products for store %s without an allowlist", async store => {
    const {navigate, document} = await boot({store});
    const theme = vi.fn();
    document.querySelector(".js-item-buy-open")!.addEventListener("click", theme);
    (document.querySelector(".js-item-buy-open") as HTMLElement).click();
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledTimes(1));
    expect(theme).not.toHaveBeenCalled();
});

it("warms only a card of interest instead of fetching every product on page load", async () => {
    const {fetch, document} = await boot();
    expect(fetch.mock.calls.filter(([url]) => url.includes("/personalization?"))).toHaveLength(0);
    const control = document.querySelector<HTMLElement>(".js-item-buy-open")!;
    control.dispatchEvent(new dom.window.Event("pointerover", {bubbles: true}));
    control.dispatchEvent(new dom.window.Event("focusin", {bubbles: true}));
    await vi.waitFor(() => expect(fetch.mock.calls.filter(([url]) => url.includes("/personalization?"))).toHaveLength(1));
    expect(fetch.mock.calls.some(([url]) => url.includes("productId=128433426"))).toBe(false);
});

it("supports component-marked cards and modal quick buy buttons in other themes", async () => {
    const {navigate, document} = await boot({store: 456});
    const product = document.querySelector<HTMLElement>(".js-item-product")!;
    product.className = "custom-theme-card";
    product.dataset.component = "product-list-item";
    const control = product.querySelector<HTMLElement>(".js-item-buy-open")!;
    control.className = "js-quickshop-modal-open";
    product.querySelector(".item-link")!.className = "js-item-image-link";
    control.click();
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith("https://shop.labcafe.com.ar/productos/cafe-44960054/"));
});
