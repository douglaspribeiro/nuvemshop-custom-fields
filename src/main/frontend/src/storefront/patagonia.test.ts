// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { NubeSDKState } from "@tiendanube/nube-sdk-types";
import { DISABLED, type PersonalizationConfig } from "../shared/config";
import { startPatagonia, type StorefrontRuntime } from "./patagonia";

const configuration: PersonalizationConfig = {
	...DISABLED, enabled: true, locale: "es-AR",
	fields: [{ label: "Nombre", propertyName: "Nombre", fieldType: "TEXT", required: true,
		maxLength: 20, placeholder: null, validationPattern: null, options: [] }],
};
const productState = (id = 341531127, theme = "patagonia",
	variants: { id: number; values?: Record<string, string>[] }[] = [{ id: 101 }]) => ({
	store: { id: 7278258, theme }, location: { page: { type: "product", data: { product: { id, variants } } } },
}) as unknown as NubeSDKState;

describe("Patagonia transition", () => {
	let state: NubeSDKState;
	let runtime: StorefrontRuntime;
	let listeners: Map<string, (state: NubeSDKState) => void>;
	let dispose: (() => void) | undefined;
	const markup = () => {
		document.body.innerHTML = `<section>
			<div data-nubesdk-slot="before_product_detail_add_to_cart"></div>
			<input name="quantity" type="number" value="2">
			<button data-component="product.add-to-cart"><span>Agregar al carrito</span></button>
			<button data-component="wallet.buy-now">Comprar ahora</button>
		</section><form><input name="zipcode"></form>`;
	};
	const field = () => document.querySelector<HTMLInputElement>('[name="properties[Nombre]"]')!;
	const button = () => document.querySelector<HTMLButtonElement>('[data-component="product.add-to-cart"]')!;
	const click = () => button().querySelector("span")!.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
	const ready = async () => { await vi.waitFor(() => expect(field()).not.toBeNull()); };
	beforeEach(() => {
		markup();
		state = productState();
		listeners = new Map();
		runtime = { getState: () => state, on: (event, fn) => { listeners.set(event, fn); },
			off: (event) => { listeners.delete(event); }, send: vi.fn() };
	});
	afterEach(() => { dispose?.(); dispose = undefined; vi.useRealTimers(); });

	it("shows a custom select prompt and requires a choice even for previously optional lists", async () => {
        dispose = startPatagonia(runtime, vi.fn().mockResolvedValue({ ...configuration, fields: [{
            ...configuration.fields[0], fieldType: "SELECT", required: false,
            placeholder: "Elegí la molienda", options: ["Grano", "Molido"] }]}));
        await ready();
        const select = field() as unknown as HTMLSelectElement;
        expect(select.options[0].textContent).toBe("Elegí la molienda");
        expect(select.value).toBe("");
        click();
        expect(runtime.send).not.toHaveBeenCalled();
        select.value = "Molido";
        click();
        expect(runtime.send).toHaveBeenCalledTimes(1);
        expect(vi.mocked(runtime.send).mock.calls[0][2]()).toMatchObject({ cart: { items: [{ properties: { Nombre: "Molido" } }] } });
    });

	it("renders outside the SDK slot and blocks native add when required values are absent", async () => {
		const nativeAdd = vi.fn();
		button().addEventListener("click", nativeAdd);
		dispose = startPatagonia(runtime, vi.fn().mockResolvedValue(configuration));
		await ready();
		expect(document.querySelector('[data-nubesdk-slot]')!.children).toHaveLength(0);
		click();
		expect(field().validationMessage).toBe("Campo obligatorio.");
		expect(nativeAdd).not.toHaveBeenCalled();
		expect(runtime.send).not.toHaveBeenCalled();
	});

	it("renders on the actual Patagonia markup even when no SDK slot exists", async () => {
		document.querySelector('[data-nubesdk-slot]')!.remove();
		dispose = startPatagonia(runtime, vi.fn().mockResolvedValue(configuration));
		await ready();
		expect(field().closest("section")).toBe(button().closest("section"));
		expect(document.querySelector('form input[name^="properties"]')).toBeNull();
	});

	it("sends the selected variant, native quantity and properties exactly once, preserving values on failure", async () => {
		state = productState(341531127, "patagonia", [{ id: 101 }, { id: 102 }]);
		dispose = startPatagonia(runtime, vi.fn().mockResolvedValue(configuration));
		await ready();
		listeners.get("product:variant_selected")!({ ...state, eventPayload: { id: 102, product_id: 341531127 } });
		field().value = "  Pauli  ";
		click(); click();
		expect(runtime.send).toHaveBeenCalledTimes(1);
		const update = vi.mocked(runtime.send).mock.calls[0][2];
		expect(update()).toEqual({ cart: { items: [{ product_id: 341531127, variant_id: 102, quantity: 2, properties: { Nombre: "Pauli" } }] } });
		expect(button().disabled).toBe(true);
		listeners.get("cart:add:success")!({ ...state, eventPayload: { product_id: 99, variant_id: 102 } });
		expect(button().disabled).toBe(true);
		listeners.get("cart:add:fail")!({ ...state, eventPayload: { product_id: 341531127, variant_id: 102 } });
		expect(button().disabled).toBe(false);
		expect(field().value).toBe("  Pauli  ");
		expect(document.body.textContent).toContain("No se pudo agregar");
		click();
		listeners.get("cart:add:success")!({ ...state, eventPayload: { product_id: 341531127, variant_id: 102 } });
		expect(document.body.textContent).toContain("Producto agregado al carrito con la personalización");
	});

	it("does not let express purchase bypass personalization", async () => {
		dispose = startPatagonia(runtime, vi.fn().mockResolvedValue(configuration));
		await ready();
		const express = document.querySelector<HTMLButtonElement>('[data-component="wallet.buy-now"]')!;
		const nativeBuyNow = vi.fn();
		express.addEventListener("click", nativeBuyNow);
		express.click();
		expect(nativeBuyNow).not.toHaveBeenCalled();
		expect(runtime.send).not.toHaveBeenCalled();
		field().value = "Pauli";
		express.click();
		expect(runtime.send).toHaveBeenCalledTimes(1);
	});

	it("rejects invalid quantity and never guesses a variant", async () => {
		state = productState(341531127, "patagonia", [{ id: 101 }, { id: 102 }]);
		dispose = startPatagonia(runtime, vi.fn().mockResolvedValue(configuration));
		await ready(); field().value = "Pauli";
		click();
		expect(document.body.textContent).toContain("Seleccioná la variante");
		listeners.get("product:variant_selected")!({ ...state, eventPayload: { id: 101, product_id: 341531127 } });
		document.querySelector<HTMLInputElement>('[name="quantity"]')!.value = "0";
		click();
		expect(document.body.textContent).toContain("cantidad entera");
		expect(runtime.send).not.toHaveBeenCalled();
	});

	it("recognizes the explicitly selected default variant when its initial event was missed", async () => {
		state = productState(341531127, "patagonia", [
			{ id: 101, values: [{ es: "Rojo" }] }, { id: 102, values: [{ es: "Azul" }] },
		]);
		document.querySelector("section")!.insertAdjacentHTML("afterbegin", '<dl><dt>Color</dt><dd><button class="btn-variant" aria-pressed="true" title="Color Azul">Azul</button></dd></dl>');
		dispose = startPatagonia(runtime, vi.fn().mockResolvedValue(configuration));
		await ready(); field().value = "Pauli"; click();
		expect(vi.mocked(runtime.send).mock.calls[0][2]()).toMatchObject({ cart: { items: [{ variant_id: 102 }] } });
	});

	it("ignores stale configuration after navigating to an unconfigured product", async () => {
		let resolve!: (config: PersonalizationConfig) => void;
		const load = vi.fn().mockImplementationOnce(() => new Promise<PersonalizationConfig>((done) => { resolve = done; }))
			.mockResolvedValue(DISABLED);
		dispose = startPatagonia(runtime, load);
		state = productState(42);
		listeners.get("location:updated")!(state);
		resolve(configuration);
		await vi.waitFor(() => expect(load).toHaveBeenCalledTimes(2));
		expect(field()).toBeNull();
		expect(runtime.send).not.toHaveBeenCalled();
	});

	it("preserves entered values when React remounts the purchase section, then cleans up on navigation", async () => {
		dispose = startPatagonia(runtime, vi.fn().mockResolvedValue(configuration));
		await ready(); field().value = "Pauli";
		markup(); await ready();
		expect(field().value).toBe("Pauli");
		state = { ...state, location: { page: { type: "home" } } } as NubeSDKState;
		listeners.get("location:updated")!(state);
		expect(field()).toBeNull();
		const nativeAdd = vi.fn(); button().addEventListener("click", nativeAdd);
		click(); expect(nativeAdd).toHaveBeenCalledOnce();
	});

	it("does not interfere with other themes or products without fields", async () => {
		state = productState(1, "nuvem");
		const load = vi.fn().mockResolvedValue(DISABLED);
		dispose = startPatagonia(runtime, load);
		expect(load).not.toHaveBeenCalled();
		state = productState(); listeners.get("location:updated")!(state);
		await vi.waitFor(() => expect(load).toHaveBeenCalledOnce());
		expect(field()).toBeNull();
	});

	it("releases the button after timeout without retrying a possibly successful purchase", async () => {
		dispose = startPatagonia(runtime, vi.fn().mockResolvedValue(configuration));
		await ready(); vi.useFakeTimers(); field().value = "Pauli";
		click(); vi.advanceTimersByTime(15000);
		expect(button().disabled).toBe(false);
		expect(document.body.textContent).toContain("Revisá el carrito");
		expect(runtime.send).toHaveBeenCalledTimes(1);
	});
    it("selects image options and carries their names through the Patagonia cart", async () => {
        const images = {...configuration,fields:[{label:"Capa",propertyName:"Capa",fieldType:"IMAGE_SELECT" as const,required:true,
            maxLength:100,placeholder:null,validationPattern:null,options:["Floral"],
            imageOptions:[{label:"Floral",thumbnailUrl:"https://assets.test/thumb",imageUrl:"https://assets.test/large"}]}]};
        dispose=startPatagonia(runtime,vi.fn().mockResolvedValue(images));await vi.waitFor(()=>expect(document.querySelector("#ncf-patagonia-fields select")).not.toBeNull());
        click();expect(runtime.send).not.toHaveBeenCalled();
        document.querySelector<HTMLButtonElement>("#ncf-patagonia-fields button[data-value='Floral']")!.click();click();
        const update=vi.mocked(runtime.send).mock.calls[0][2];
        expect(update()).toMatchObject({cart:{items:[{properties:{Capa:"Floral"}}]}});
    });

});
