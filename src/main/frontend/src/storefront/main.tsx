import { Button, Column, NumberField, Text } from "@tiendanube/nube-sdk-jsx";
import type { NubeSDK, NubeSDKState } from "@tiendanube/nube-sdk-types";
import {
	DISABLED,
	type PersonalizationConfig,
	appOrigin,
	fetchConfig,
	safeState,
	storeId,
} from "../shared/config";
import { normalizedColor, normalizeProperties } from "../shared/properties";
import { createCartImageRenderer } from "./cart-images";
import { PersonalizationFields } from "./PersonalizationFields";
import { type FieldError, type ValueMap, hasAnyValue, toCartProperties, validate } from "./values";

const SLOT = "before_product_detail_add_to_cart";

type BeforeUpdatePayload = {
	request_id?: string;
	action?: "ADD" | "REMOVE";
	item?: {
		product_id?: number | null;
		variant_id?: number | null;
		previous_quantity?: number;
		new_quantity?: number;
	};
};

let config: PersonalizationConfig = DISABLED;
let store: number | null = null;
let productId: number | null = null;
let variantId: number | null = null;
let values: ValueMap = {};
let errors: FieldError[] = [];
let textColor: string | undefined;

/** Contador de `cart:add` disparados por nos, para nao reprocessar o proprio evento. */
let pendingSelfAdds = 0;
let reportedNativeGate = false;
let draftGeneration = 0;
let directPending = false;
let directQuantity = 1;
let directMessage = "";
let submitted: { product: number; variant: number | undefined; generation: number;
    values: string; properties: Record<string, string> } | null = null;



let inFlightProduct: number | null = null;

/** Ultimo desfecho do syncProduct, enviado no beacon de diagnostico. */
let outcome = "boot";

export function App(nube: NubeSDK) {
	// Sem este opt-in a plataforma nunca dispara cart:before_update.
	nube.send("config:set", () => ({ config: { handle_cart_before_update: true } }));

	startDiagnostics(nube);
    const renderCartImages = createCartImageRenderer(nube);
    void renderCartImages(safeState(nube));

	// Tres gatilhos de proposito. O script roda com event=onfirstinteraction, entao sobe
	// depois da pagina montada: `location:updated` pode nunca disparar (nada navegou) e o
	// getState() imediato pode ainda nao estar hidratado. `page:loaded` e o sinal oficial
	// de "SDK pronto". syncProduct e idempotente, then chamar de varios lugares e seguro.
	void syncProduct(nube, safeState(nube));
	nube.on("page:loaded", (state) => { void syncProduct(nube, state); void renderCartImages(state); });
	nube.on("location:updated", (state) => { void syncProduct(nube, state); void renderCartImages(state); });
    nube.on("cart:update", state => void renderCartImages(state));
	nube.on("product:variant_selected", (state) => {
		variantId = readVariantId(state) ?? variantId;
	});
	nube.on("cart:before_update", (state) => gate(nube, state));
	nube.on("cart:add:success", (state) => {
        void renderCartImages(state);
        const added = state.eventPayload as {product_id?: number; variant_id?: number; properties?: unknown} | undefined;
        const sent = submitted;
        // Native additions and late acknowledgements must never erase an unsent draft.
        if (!sent || added?.product_id !== sent.product || added?.variant_id !== sent.variant
            || sent.product !== productId || sent.variant !== (variantId ?? undefined) || sent.generation !== draftGeneration) return;
        submitted = null;
        pendingSelfAdds = 0;
        directPending = false;
        const properties = Array.isArray(added.properties)
            ? Object.fromEntries(added.properties.filter(p => p && typeof p === "object")
                .map(p => [p.name ?? p.key ?? p.label, p.value ?? p.text]))
            : added.properties as Record<string, unknown> | undefined;
        const confirmed = properties && Object.entries(sent.properties).every(([key, value]) => properties[key] === value);
        if (!confirmed || sent.values !== JSON.stringify(values)) {
            directMessage = config.locale.startsWith("es")
                ? "Revisá la personalización en el carrito. Conservamos los datos ingresados."
                : "Confira a personalização no carrinho. Mantivemos os dados preenchidos.";
            render(nube);
            return;
        }
        directMessage = config.locale.startsWith("es") ? "Revisá la personalización en el carrito." : "Confira a personalização no carrinho.";
        void renderCartImages(state);
		pendingSelfAdds = 0;
		values = {};
		errors = [];
        if (isPatagonia(nube) || config.fields.some(field => field.fieldType === "IMAGE_SELECT" || field.fieldType === "CHECKBOX")) render(nube);
	});
	nube.on("cart:add:fail", () => {
        submitted = null;
		pendingSelfAdds = 0;
        directPending = false;
        directMessage = config.locale.startsWith("es") ? "No pudimos agregar el producto. Revisá el carrito antes de intentar nuevamente." : "Não foi possível adicionar o produto. Confira o carrinho antes de tentar novamente.";
        render(nube);
	});
}

async function syncProduct(nube: NubeSDK, state: NubeSDKState | null) {
	const page = state?.location?.page;
	if (!state || page?.type !== "product") {
		outcome = state ? `page_type_${page?.type ?? "unknown"}` : "no_state";
		clear(nube);
		return;
	}

	const nextStore = storeId(state);
	const nextProduct = page.data?.product?.id ?? null;
	if (!nextStore || !nextProduct) {
		outcome = !nextStore ? "no_store_id" : "no_product_id";
		clear(nube);
		return;
	}

	if (nextProduct === productId && config.enabled) {
		render(nube);
		outcome = "rendered";
		return;
	}

	// Tres gatilhos podem chamar isto quase juntos; sem o guard sao dois fetch e o
	// segundo zera os valores que o cliente ja digitou.
	if (inFlightProduct === nextProduct) {
		return;
	}

	store = nextStore;
	productId = nextProduct;
	variantId = readVariantId(state);
	values = {};
	errors = [];
    draftGeneration++; submitted = null;
    directPending = false; directQuantity = 1; directMessage = "";

	inFlightProduct = nextProduct;
	try {
		config = await fetchConfig(nextStore, nextProduct);
	} finally {
		inFlightProduct = null;
	}
	if (!config.enabled || config.fields.length === 0) {
		outcome = config.enabled ? "config_without_fields" : "config_disabled";
		clear(nube);
		return;
	}
	textColor = normalizedColor(config.style?.productTextColor);
	render(nube);
	outcome = "rendered";
}

function render(nube: NubeSDK) {
    if (!config.enabled) { nube.clearSlot(SLOT); return; }
    const renderProduct = productId;
    const renderGeneration = draftGeneration;
    const fields = <PersonalizationFields
			fields={config.fields}
			errors={errors}
			color={textColor}
            values={values}
            locale={config.locale}
			onValueChange={(key, value) => {
                if (productId !== renderProduct || draftGeneration !== renderGeneration) return;
				values[key] = value;
                if (config.fields.some(field => (field.fieldType === "IMAGE_SELECT" || field.fieldType === "SELECT" || field.fieldType === "CHECKBOX") && (field.propertyName || field.label) === key)) { errors = errors.filter(error => error.propertyName !== key); render(nube); }
			}}
		/>;
    if (!isPatagonia(nube)) { nube.render(SLOT, fields); return; }
    const es = config.locale.startsWith("es");
    nube.render(SLOT, <Column gap={8}>{fields}
        <Text>{es ? "Completá los datos y usá este botón para agregar tu producto personalizado." : "Preencha os dados e use este botão para adicionar seu produto personalizado."}</Text>
        <NumberField name="ncf-personalized-quantity" label={es ? "Cantidad personalizada" : "Quantidade personalizada"}
            min={1} step={1} value={directQuantity} disabled={directPending}
            onChange={event => { directQuantity = Number(event.value); }} />
        <Button disabled={directPending} onClick={() => {
            if (productId !== renderProduct || draftGeneration !== renderGeneration || directPending) return;
            if (!Number.isSafeInteger(directQuantity) || directQuantity < 1 || variantId == null) {
                directMessage = es ? "Seleccioná una variante y una cantidad entera mayor que cero." : "Selecione uma variação e uma quantidade inteira maior que zero.";
                render(nube); return;
            }
            const state = safeState(nube);
            if (!state) return;
            directMessage = "";
            gate(nube, {...state, eventPayload: {action: "ADD", item: {product_id: productId, variant_id: variantId,
                previous_quantity: 0, new_quantity: directQuantity}}} as NubeSDKState, true);
        }}>{directPending ? (es ? "Agregando…" : "Adicionando…") : (es ? "Agregar con personalización" : "Adicionar com personalização")}</Button>
        <Text>{directMessage}</Text>
    </Column>);
}

function isPatagonia(nube: NubeSDK): boolean {
    return safeState(nube)?.store?.theme?.trim().toLowerCase() === "patagonia";
}

function clear(nube: NubeSDK) {
	config = DISABLED;
	productId = null;
	variantId = null;
	values = {};
	errors = [];
    draftGeneration++; submitted = null;
    directPending = false; directQuantity = 1; directMessage = "";
	nube.clearSlot(SLOT);
}

/**
 * Intercepta a adicao nativa ao carrinho. `properties` nao pode ser anexada ao
 * evento nativo, entao cancelamos e reemitimos via `cart:add`, que aceita properties.
 * O checkout confere os valores persistidos mesmo quando o tema não dispara o gate.
 */
function gate(nube: NubeSDK, state: NubeSDKState | null, direct = false) {
	const payload = (state?.eventPayload ?? null) as BeforeUpdatePayload | null;
	const requestId = payload?.request_id;
	if (!requestId && !direct) {
		return;
	}

	// Este ADD e o nosso proprio reenvio: libera sem reprocessar.
	if (!direct && pendingSelfAdds > 0) {
		pendingSelfAdds--;
		respond(nube, requestId, true);
		return;
	}
	if (!direct && !reportedNativeGate) {
		reportedNativeGate = true;
		report(store, productId, "cart_before_update_received");
	}

	if (payload?.action !== "ADD" || !config.enabled || config.fields.length === 0) {
		respond(nube, requestId, true);
		return;
	}

	const itemProductId = payload.item?.product_id ?? null;
	if (itemProductId != null && productId != null && itemProductId !== productId) {
		respond(nube, requestId, true);
		return;
	}

	const found = validate(config.fields, values, config.locale);
	if (found.length > 0) {
		errors = found;
		render(nube);
		respond(nube, requestId, false, "validation_failed");
		return;
	}

	const properties = toCartProperties(config.fields, values);
    // The bridge may merge by variant without replacing properties. Never submit
    // different personalization into that existing line.
    const requestedVariant = payload.item?.variant_id ?? variantId;
    const existing = state?.cart?.items?.find(item => item.product_id === productId && item.variant_id === requestedVariant);
    const saved = Object.fromEntries(normalizeProperties(existing?.properties).map(p => [p.name, p.value]));
    if (existing && (Object.keys(saved).length !== Object.keys(properties).length
        || Object.entries(properties).some(([key, value]) => saved[key] !== value))) {
        errors = [{propertyName: config.fields[0].propertyName || config.fields[0].label,
            label: config.fields[0].label, message: config.locale.startsWith("es")
                ? "Este producto ya está en el carrito con otros datos. Retiralo del carrito antes de agregar la nueva personalización."
                : "Este produto já está no carrinho com outros dados. Remova-o do carrinho antes de adicionar a nova personalização."}];
        render(nube);
        respond(nube, requestId, false, "personalization_cart_conflict");
        return;
    }

	if (!direct && !hasAnyValue(properties)) {
		respond(nube, requestId, true);
		return;
	}

	// Limitacao conhecida da plataforma: se o produto ja esta no carrinho, o bridge
	// resolve o cart:add como bump de quantidade e descarta properties.
	if (alreadyInCart(state, itemProductId ?? productId)) {
		report(store, productId, "reissue_properties_dropped_item_in_cart");
	}

	errors = [];
	respond(nube, requestId, false, "reissued_with_properties");
	reissue(nube, payload, properties);
}

function respond(nube: NubeSDK, requestId: string | undefined, proceed: boolean, reason?: string) {
    if (!requestId) return;
	nube.send("cart:before_update:result", () => ({
		eventPayload: { request_id: requestId, proceed, reason },
	}));
}

function reissue(
	nube: NubeSDK,
	payload: BeforeUpdatePayload,
	properties: Record<string, string>,
) {
	const product_id = payload.item?.product_id ?? productId;
	if (product_id == null) {
		return;
	}
	const previous = payload.item?.previous_quantity ?? 0;
	const next = payload.item?.new_quantity ?? previous + 1;
	const quantity = Math.max(1, next - previous);
	const variant_id = payload.item?.variant_id ?? variantId ?? undefined;

    submitted = {product: product_id, variant: variant_id, generation: draftGeneration,
        values: JSON.stringify(values), properties: {...properties}};
	pendingSelfAdds++;
    if (!payload.request_id && isPatagonia(nube)) { directPending = true; render(nube); }
	nube.send("cart:add", () => ({
		cart: { items: [{ product_id, variant_id, quantity, properties }] },
	}));
}

function alreadyInCart(state: NubeSDKState | null, product: number | null): boolean {
	if (product == null) {
		return false;
	}
	return (state?.cart?.items ?? []).some((item) => item?.product_id === product);
}

function readVariantId(state: NubeSDKState | null): number | null {
	const fromEvent = (state?.eventPayload ?? null) as Record<string, unknown> | null;
	const direct = fromEvent?.variant_id;
	if (typeof direct === "number" && direct > 0) {
		return direct;
	}
	const nested = (fromEvent?.variant as Record<string, unknown> | undefined)?.id;
	if (typeof nested === "number" && nested > 0) {
		return nested;
	}
	const page = state?.location?.page;
	if (page?.type === "product") {
		const variants = page.data?.product?.variants ?? [];
		if (variants.length === 1) {
			return variants[0]?.id ?? null;
		}
	}
	return null;
}

/**
 * Instrumento temporario de rollout. Escuta tudo, acumula os nomes dos eventos e manda
 * UM beacon com o desfecho do syncProduct. Serve para responder, pelos logs do app, se o
 * App subiu e quais eventos a plataforma entregou — sem isso o debug e cego.
 * Remover quando o storefront estiver estavel.
 */
function startDiagnostics(nube: NubeSDK) {
	const seen = new Set<string>();
	// O nome do evento vem no SEGUNDO parametro do listener, nao no state.
	nube.on("*", (_state, event) => {
		seen.add(String(event));
	});
	if (typeof setTimeout !== "function") {
		return;
	}
	setTimeout(() => {
		report(store, productId, outcome, Array.from(seen).sort().join("|"));
	}, 5000);
}

/** Telemetria best-effort no endpoint que o script legado ja usa. */
function report(
	storeIdValue: number | null,
	product: number | null,
	reason: string,
	detail?: string,
) {
	if (typeof fetch !== "function") {
		return;
	}
	const url = new URL(`${appOrigin()}/public/script-events`);
	url.searchParams.set("event", "storefront_sdk");
	url.searchParams.set("reason", reason);
	// storeId ausente e informacao relevante (foi um dos desfechos possiveis), nao motivo
	// para engolir o beacon.
	if (storeIdValue) {
		url.searchParams.set("storeId", String(storeIdValue));
	}
	if (product) {
		url.searchParams.set("productId", String(product));
	}
	if (detail) {
		url.searchParams.set("path", detail);
	}
	void fetch(url.toString(), { credentials: "omit" }).catch(() => undefined);
}
