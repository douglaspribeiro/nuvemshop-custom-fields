import { imageChoices } from "./image-options";
import type { NubeSDKState } from "@tiendanube/nube-sdk-types";
import { fetchConfig, type PersonalizationConfig } from "../shared/config";
import { langOf } from "../shared/i18n";
import { normalizedColor } from "../shared/properties";
import { keyOf, toCartProperties, validate } from "./values";

type Listener = (state: NubeSDKState) => void;
/** Interface do barramento na página; diferente do NubeSDK recebido pelo Worker. */
export interface StorefrontRuntime {
	getState(): NubeSDKState;
	on(event: string, listener: Listener): void;
	off(event: string, listener: Listener): void;
	send(source: string, event: string, update: () => unknown): void;
}

const SOURCE = "campos-personalizados-patagonia";
const SLOT = '[data-nubesdk-slot="before_product_detail_add_to_cart"]';
const BUY = '[data-component="product.add-to-cart"], [data-component="wallet.buy-now"]';

/** Transição DOM para o Patagonia, que ainda não emite cart:before_update. */
export function startPatagonia(runtime: StorefrontRuntime, load = fetchConfig,
	report: (reason: string, product: number | null) => void = () => {}) {
	let identity = "";
	let generation = 0;
	let config: PersonalizationConfig | null = null;
	let productId: number | null = null;
	let variantId: number | null = null;
	let container: HTMLDivElement | null = null;
	let scope: Element | null = null;
	let pending: { product: number; variant: number; button: HTMLButtonElement; disabled: boolean } | null = null;
	let timer: ReturnType<typeof setTimeout> | undefined;
	let disposed = false;
	const controls = new Map<string, HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>();
	const strings = () => langOf(config?.locale) === "pt" ? {
		variant: "Selecione a variação do produto antes de adicionar.",
		quantity: "Informe uma quantidade inteira maior que zero.",
		pending: "Adicionando ao carrinho…",
		success: "Produto adicionado ao carrinho com a personalização.",
		failure: "Não foi possível adicionar o produto. Revise a disponibilidade e tente novamente.",
		timeout: "Não foi possível confirmar a inclusão. Confira o carrinho antes de tentar novamente.",
	} : {
		variant: "Seleccioná la variante del producto antes de agregarlo.",
		quantity: "Ingresá una cantidad entera mayor que cero.",
		pending: "Agregando al carrito…",
		success: "Producto agregado al carrito con la personalización.",
		failure: "No se pudo agregar el producto. Revisá la disponibilidad e intentá nuevamente.",
		timeout: "No se pudo confirmar la operación. Revisá el carrito antes de intentarlo nuevamente.",
	};
	function status(message: string) {
		const element = container?.querySelector<HTMLElement>('[role="status"]');
		if (element) element.textContent = message;
	}
	function release() {
		clearTimeout(timer);
		if (pending) pending.button.disabled = pending.disabled;
		pending = null;
	}
	function reset() {
		release();
		scope?.removeAttribute("data-ncf-patagonia");
		container?.remove();
		container = null;
		scope = null;
		controls.clear();
		config = null;
		variantId = null;
	}
	function selected(state: NubeSDKState) {
		const payload = state.eventPayload as { id?: number; variant_id?: number; product_id?: number } | undefined;
		if (payload?.product_id === productId) variantId = payload.variant_id ?? payload.id ?? null;
	}
	function variantFromControls(state: NubeSDKState): number | null {
		const page = state.location?.page;
		if (page?.type !== "product") return null;
		// A seleção inicial pode ter sido emitida antes do carregamento do app.
		// Lê a seleção explícita do tema; não escolhe a primeira variante do catálogo.
		const buttons = [...(scope?.querySelectorAll<HTMLButtonElement>('button.btn-variant[aria-pressed="true"]') ?? [])];
		if (!buttons.length) return null;
		const selectedValues = buttons.map((button) => {
			const attribute = button.closest("dl")?.querySelector("dt")?.textContent?.trim();
			const prefix = attribute ? `${attribute} ` : "";
			return prefix && button.title.startsWith(prefix) ? button.title.slice(prefix.length) : null;
		});
		const matches = (page.data?.product?.variants ?? []).filter((variant) =>
			variant.values?.length === selectedValues.length && variant.values.every((value, index) =>
				selectedValues[index] != null && (typeof value === "string" ? value === selectedValues[index]
					: Object.values(value).includes(selectedValues[index]!))));
		return matches.length === 1 ? matches[0].id : null;
	}
	function render() {
		if (!config?.enabled || !config.fields.length || container?.isConnected) return;
		const slot = document.querySelector(SLOT);
		const buy = document.querySelector('[data-component="product.add-to-cart"]');
		const root = slot?.parentElement ?? buy?.closest("section");
		if (!root?.querySelector(BUY)) return;
		// Patagonia não cria o elemento do slot enquanto nenhum app SDK renderiza nele.
		let anchor = slot ?? buy;
		while (anchor?.parentElement && anchor.parentElement !== root) anchor = anchor.parentElement;
		if (!anchor || anchor.parentElement !== root) return;
		// O slot pertence ao React/SDK. Nosso nó fica adjacente, para clearSlot não apagá-lo.
		const previous = new Map([...controls].map(([key, input]) => [key, input.value]));
		controls.clear();
		container = document.createElement("div");
		container.id = "ncf-patagonia-fields";
		container.className = "ncf-personalization";
		container.style.cssText = "display:flex;flex-direction:column;gap:12px;margin:12px 0";
		// Comprar agora adiciona outro item sem properties. O fluxo personalizado passa pelo carrinho.
		const style = document.createElement("style");
		style.textContent = '[data-ncf-patagonia] [data-component="wallet.buy-now"]{display:none!important}';
		container.append(style);
		const color = normalizedColor(config.style?.productTextColor);
		if (color) container.style.color = color;
		config.fields.forEach((field, index) => {
			const label = document.createElement(field.fieldType === "IMAGE_SELECT" ? "div" : "label");
			label.textContent = field.label + (field.required ? " *" : "");
			label.style.cssText = "display:flex;flex-direction:column;gap:4px";
			const input = document.createElement((field.fieldType === "SELECT" || field.fieldType === "IMAGE_SELECT") ? "select"
				: field.fieldType === "TEXTAREA" ? "textarea" : "input");
			input.id = `ncf-patagonia-${index}`;
			input.name = `properties[${keyOf(field)}]`;
			input.className = "form-control";
			input.required = field.required;
			if (input instanceof HTMLSelectElement) {
				input.append(new Option("—", ""));
				for (const option of field.options) input.append(new Option(option, option));
			} else {
				input.placeholder = field.placeholder ?? "";
				if (field.maxLength != null && field.maxLength >= 0) input.maxLength = field.maxLength;
			}
			input.value = previous.get(keyOf(field)) ?? "";
			input.addEventListener("input", () => input.setCustomValidity(""));
			input.addEventListener("change", () => input.setCustomValidity(""));
			controls.set(keyOf(field), input);
            if (field.fieldType === "IMAGE_SELECT" && input instanceof HTMLSelectElement) {
                input.setAttribute("aria-label",field.label);
                label.append(imageChoices(field,input,config?.locale));
            }
			label.append(input);
			container!.append(label);
		});
		const feedback = document.createElement("p");
		feedback.setAttribute("role", "status");
		feedback.setAttribute("aria-live", "polite");
		container.append(feedback);
		root.insertBefore(container, anchor);
		root.setAttribute("data-ncf-patagonia", "true");
		scope = root;
		report("patagonia_transition_rendered", productId);
	}
	async function sync() {
		if (disposed) return;
		const state = runtime.getState();
		const page = state.location?.page;
		const product = page?.type === "product" ? page.data?.product : null;
		const active = String(state.store?.theme ?? "").toLowerCase() === "patagonia";
		const next = active && product?.id && state.store?.id ? `${state.store.id}:${product.id}` : "";
		if (next === identity) { render(); return; }
		identity = next;
		const request = ++generation;
		reset();
		productId = product?.id ?? null;
		if (!next || !product) return;
		const variants = product.variants ?? [];
		const queryVariant = Number(new URL(window.location.href).searchParams.get("variant"));
		variantId = variants.length === 1 ? variants[0].id
			: variants.find((variant) => variant.id === queryVariant)?.id ?? null;
		selected(state);
		const loaded = await load(state.store.id, product.id);
		if (disposed || request !== generation) return;
		config = loaded;
		if (!config.enabled || !config.fields.length) report("patagonia_transition_disabled", productId);
		render();
	}
	function click(event: MouseEvent) {
		const button = event.target instanceof Element ? event.target.closest<HTMLButtonElement>(BUY) : null;
		if (!button || button.disabled || !scope?.contains(button) || !container?.isConnected || !config?.enabled) return;
		const state = runtime.getState();
		const page = state.location?.page;
		if (page?.type !== "product" || page.data?.product?.id !== productId) return;
		// Capture no document precede o onClick React. Nunca dispara o add nativo junto do personalizado.
		event.preventDefault();
		event.stopImmediatePropagation();
		if (pending) return;
		const values = Object.fromEntries([...controls].map(([key, input]) => [key, input.value]));
		for (const input of controls.values()) input.setCustomValidity("");
		const errors = validate(config.fields, values, config.locale);
		for (const error of errors) controls.get(error.propertyName)?.setCustomValidity(error.message);
		if (errors.length) {
			const input = controls.get(errors[0].propertyName);
			input?.focus();
			input?.reportValidity();
			return;
		}
		variantId = variantFromControls(state) ?? variantId;
		if (!variantId || !page.data.product.variants?.some((variant) => variant.id === variantId)) {
			status(strings().variant); return;
		}
		const quantityInput = scope.querySelector<HTMLInputElement>('input[name="quantity"]');
		const quantity = quantityInput ? Number(quantityInput.value) : 1;
		if (!Number.isSafeInteger(quantity) || quantity < 1) { status(strings().quantity); return; }
		const item = { product_id: productId, variant_id: variantId, quantity, properties: toCartProperties(config.fields, values) };
		pending = { product: productId!, variant: variantId, button, disabled: button.disabled };
		button.disabled = true;
		status(strings().pending);
		timer = setTimeout(() => { release(); status(strings().timeout); }, 15000);
		try {
			report("patagonia_transition_add", productId);
			runtime.send(SOURCE, "cart:add", () => ({ cart: { items: [item] } }));
		} catch {
			release(); status(strings().failure);
		}
	}
	function finished(state: NubeSDKState, success: boolean) {
		const item = state.eventPayload as { product_id?: number; variant_id?: number } | undefined;
		if (!pending || item?.product_id !== pending.product || item.variant_id !== pending.variant) return;
		release();
		status(success ? strings().success : strings().failure);
		report(success ? "patagonia_transition_success" : "patagonia_transition_failed", productId);
	}
	const listeners: [string, Listener][] = [
		["location:updated", () => { void sync(); }],
		["page:loaded", () => { void sync(); }],
		["product:variant_selected", selected],
		["cart:add:success", (state) => finished(state, true)],
		["cart:add:fail", (state) => finished(state, false)],
	];
	for (const [event, listener] of listeners) runtime.on(event, listener);
	document.addEventListener("click", click, true);
	const observer = new MutationObserver(() => { void sync(); });
	observer.observe(document.documentElement, { childList: true, subtree: true });
	void sync();
	return () => {
		disposed = true;
		generation++;
		observer.disconnect();
		document.removeEventListener("click", click, true);
		for (const [event, listener] of listeners) runtime.off(event, listener);
		reset();
	};
}
