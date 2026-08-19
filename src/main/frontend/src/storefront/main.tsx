import type { NubeSDK, NubeSDKState } from "@tiendanube/nube-sdk-types";
import {
	DISABLED,
	type PersonalizationConfig,
	appOrigin,
	fetchConfig,
	safeState,
	storeId,
} from "../shared/config";
import { normalizedColor } from "../shared/properties";
import { PersonalizationFields } from "./PersonalizationFields";
import { type FieldError, type ValueMap, hasAnyValue, toCartProperties, validate } from "./values";

const SLOT = "before_product_detail_add_to_cart";

/**
 * Temas que ainda nao entregam `cart:before_update` (issue TiendaNube/nube-sdk#394).
 * Sem o gate nao ha como anexar `properties`, e renderizar os campos criaria um
 * formulario que nao envia nada. Melhor nao renderizar e deixar o script legado agir.
 */
const THEMES_WITHOUT_GATE = new Set(["patagonia"]);

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

let inFlightProduct: number | null = null;

/** Ultimo desfecho do syncProduct, enviado no beacon de diagnostico. */
let outcome = "boot";

export function App(nube: NubeSDK) {
	// Sem este opt-in a plataforma nunca dispara cart:before_update.
	nube.send("config:set", () => ({ config: { handle_cart_before_update: true } }));

	startDiagnostics(nube);

	// Tres gatilhos de proposito. O script roda com event=onfirstinteraction, entao sobe
	// depois da pagina montada: `location:updated` pode nunca disparar (nada navegou) e o
	// getState() imediato pode ainda nao estar hidratado. `page:loaded` e o sinal oficial
	// de "SDK pronto". syncProduct e idempotente, then chamar de varios lugares e seguro.
	void syncProduct(nube, safeState(nube));
	nube.on("page:loaded", (state) => void syncProduct(nube, state));
	nube.on("location:updated", (state) => void syncProduct(nube, state));
	nube.on("product:variant_selected", (state) => {
		variantId = readVariantId(state) ?? variantId;
	});
	nube.on("cart:before_update", (state) => gate(nube, state));
	nube.on("cart:add:success", () => {
		pendingSelfAdds = 0;
		values = {};
		errors = [];
	});
	nube.on("cart:add:fail", () => {
		pendingSelfAdds = 0;
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

	if (THEMES_WITHOUT_GATE.has(String(state.store?.theme ?? "").toLowerCase())) {
		clear(nube);
		report(nextStore, nextProduct, "gate_unsupported_theme");
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
	nube.render(
		SLOT,
		<PersonalizationFields
			fields={config.fields}
			errors={errors}
			color={textColor}
			onValueChange={(key, value) => {
				values[key] = value;
			}}
		/>,
	);
}

function clear(nube: NubeSDK) {
	config = DISABLED;
	productId = null;
	variantId = null;
	values = {};
	errors = [];
	nube.clearSlot(SLOT);
}

/**
 * Intercepta a adicao nativa ao carrinho. `properties` nao pode ser anexada ao
 * evento nativo, entao cancelamos e reemitimos via `cart:add`, que aceita properties.
 * Fail-open em qualquer duvida: bloquear a compra e pior que perder a personalizacao.
 */
function gate(nube: NubeSDK, state: NubeSDKState | null) {
	const payload = (state?.eventPayload ?? null) as BeforeUpdatePayload | null;
	const requestId = payload?.request_id;
	if (!requestId) {
		return;
	}

	// Este ADD e o nosso proprio reenvio: libera sem reprocessar.
	if (pendingSelfAdds > 0) {
		pendingSelfAdds--;
		respond(nube, requestId, true);
		return;
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

	const found = validate(config.fields, values);
	if (found.length > 0) {
		errors = found;
		render(nube);
		respond(nube, requestId, false, "validation_failed");
		return;
	}

	const properties = toCartProperties(config.fields, values);
	if (!hasAnyValue(properties)) {
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

function respond(nube: NubeSDK, requestId: string, proceed: boolean, reason?: string) {
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

	pendingSelfAdds++;
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
