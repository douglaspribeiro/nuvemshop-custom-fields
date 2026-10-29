import { readFileSync } from "node:fs";
import { JSDOM } from "jsdom";
import { afterEach, expect, it, vi } from "vitest";

const asset = (name: string) => readFileSync(new URL(`../../../resources/static/assets/${name}`, import.meta.url), "utf8");
let dom: JSDOM;
afterEach(() => dom?.window.close());
async function boot() {
    dom = new JSDOM(`<head><script src="http://localhost:8080/assets/nuvemshop-personalizer.js?store=990000000001"></script></head>
        <body><form id="product_form" data-product-id="900001">
        <select name="variant_id" id="demo-variant"><option value="9000011" data-price="50.00">300 ml / Sem personalização</option>
        <option value="9000012" data-price="60.00">300 ml / Com personalização</option></select>
        <span id="demo-price"></span><input name="quantity" type="number" min="1" step="1" value="2" required>
        <div><button type="submit">Adicionar</button></div></form>
        <p id="demo-config-status"></p><div id="demo-cart"></div><span id="demo-total"></span><pre id="demo-properties"></pre><button id="demo-clear">Limpar</button></body>`,
        {url:"http://localhost:8080/local/vitrine",runScripts:"outside-only"});
    const fetch = vi.fn().mockResolvedValue({ok:true,json:async()=>({enabled:true,variantRestricted:true,variantIds:[9000012],
        fields:[{label:"Nome",propertyName:"Nome",fieldType:"TEXT",required:true,options:[]}],style:{}})});
    Object.assign(dom.window,{fetch});
    Object.defineProperty(dom.window.document,"readyState",{value:"complete",configurable:true});
    dom.window.eval(asset("local-storefront-demo.js"));
    dom.window.eval(asset("nuvemshop-personalizer.js"));
    const form = dom.window.document.querySelector<HTMLFormElement>("#product_form")!;
    await vi.waitFor(()=>expect(form.dataset.ncfInjected).toBe("true"));
    return {form, fetch};
}
it("uses the real personalization script, validates required fields and adds two personalized units for 120",async()=>{
    const {form,fetch}=await boot();const document=dom.window.document;
    expect(fetch.mock.calls.some(([url])=>String(url).startsWith("http://localhost:8080/public/stores/990000000001/personalization"))).toBe(true);
    const variant=document.querySelector<HTMLSelectElement>("#demo-variant")!;
    const input=form.querySelector<HTMLInputElement>('[name="properties[Nome]"]')!;
    expect(input.disabled).toBe(true);expect(document.querySelector("#demo-price")!.textContent).toContain("50,00");
    variant.value="9000012";variant.dispatchEvent(new dom.window.Event("change",{bubbles:true}));
    expect(input.disabled).toBe(false);expect(document.querySelector("#demo-price")!.textContent).toContain("60,00");
    const submit=()=>form.dispatchEvent(new dom.window.Event("submit",{bubbles:true,cancelable:true}));
    submit();expect(document.querySelectorAll(".demo-cart-line")).toHaveLength(0);
    input.value="Ana <script>";submit();
    expect(document.querySelectorAll(".demo-cart-line")).toHaveLength(1);
    const row=JSON.parse(document.querySelector("#demo-properties")!.textContent!);
    expect(row).toMatchObject({variantId:9000012,price:6000,quantity:2,properties:{"properties[Nome]":"Ana <script>"}});
    expect(document.querySelector("#demo-total")!.textContent).toContain("120,00");
    expect(document.querySelector("#demo-cart script")).toBeNull();
    variant.value="9000011";variant.dispatchEvent(new dom.window.Event("change",{bubbles:true}));submit();
    expect(JSON.parse(document.querySelector("#demo-properties")!.textContent!).properties).toEqual({});
    expect(document.querySelector("#demo-total")!.textContent).toContain("220,00");
    variant.value="9000012";variant.dispatchEvent(new dom.window.Event("change",{bubbles:true}));
    expect(input.value).toBe("");submit();expect(document.querySelectorAll(".demo-cart-line")).toHaveLength(2);
    document.querySelector<HTMLButtonElement>("#demo-clear")!.click();expect(document.querySelectorAll(".demo-cart-line")).toHaveLength(0);
    expect(document.querySelector("#demo-total")!.textContent).toContain("0,00");
});
