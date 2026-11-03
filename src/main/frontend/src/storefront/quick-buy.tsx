import { Text } from "@tiendanube/nube-sdk-jsx";
import type { NubeSDK, NubeSDKState } from "@tiendanube/nube-sdk-types";
import { fetchConfigRequired, safeState, storeId } from "../shared/config";
import { normalizeProperties } from "../shared/properties";
import { validate } from "./values";

type Product = { id: number; canonical_url?: string };
type Payload = { request_id?: string; action?: string; item?: {
    product_id?: number | null; variant_id?: number | null; previous_quantity?: number;
} };

/** Quick-buy policies must finish before the storefront's five-second handshake deadline. */
export function installQuickBuyRedirect(nube: NubeSDK) {
    const policies = new Map<string, ReturnType<typeof fetchConfigRequired>>();
    let active: Product | null = null;
    let generation = 0;
    let navigated: string | null = null;

    function policy(store: number, product: number) {
        const key = `${store}:${product}`;
        if (!policies.has(key)) {
            const pending = fetchConfigRequired(store, product, 3500);
            policies.set(key, pending);
            void pending.catch(() => { policies.delete(key); });
        }
        return policies.get(key)!;
    }

    function route(state: NubeSDKState, id: number): `/${string}` | null {
        const data = state.location?.page?.data as {
            product?: Product; products?: Product[]; sections?: { products?: Product[] }[];
        } | undefined;
        const candidates = [active, data?.product, ...(data?.products ?? []),
            ...(data?.sections ?? []).flatMap(section => section.products ?? [])];
        const canonical = candidates.find(product => product?.id === id && product.canonical_url)?.canonical_url
            ?? state.cart?.items?.find(item => item.product_id === id)?.url;
        if (!canonical) return null;
        try {
            const base = new URL(state.location.url);
            const url = new URL(canonical, base);
            if (url.origin !== base.origin || url.pathname === base.pathname) return null;
            return `${url.pathname}${url.search}${url.hash}` as `/${string}`;
        } catch { return null; }
    }

    function redirect(state: NubeSDKState, id: number) {
        const path = route(state, id);
        if (path) {
            if (navigated !== path) {
                navigated = path;
                nube.getBrowserAPIs().navigate(path);
            }
        } else {
            const es = state.store?.language?.startsWith("es");
            nube.render("before_main_content", <Text>{es
                ? "Abrí la página del producto para completar la personalización antes de agregarlo al carrito."
                : "Abra a página do produto para preencher a personalização antes de adicionar ao carrinho."}</Text>);
        }
    }

    nube.on("quickbuy:open", state => {
        const product = state.eventPayload as Product | undefined;
        active = typeof product?.id === "number" ? product : null;
        const ownGeneration = ++generation;
        const shop = storeId(state);
        if (!shop || !active) return;
        const id = active.id;
        void policy(shop, id).then(config => config.enabled && config.fields.length > 0, () => true)
            .then(needsFields => {
                if (needsFields && generation === ownGeneration && safeState(nube)?.location.url === state.location.url) redirect(state, id);
            });
    });
    nube.on("quickbuy:close", () => { active = null; generation++; });
    // App owns location:updated; share its listener rather than replacing it in SDK adapters.
    function locationChanged() {
        active = null; generation++; navigated = null; policies.clear();
        nube.clearSlot("before_main_content");
    }

    function intercept(state: NubeSDKState | null): boolean {
        if (!state) return false;
        const payload = state.eventPayload as Payload | undefined;
        const id = payload?.item?.product_id;
        const shop = storeId(state);
        if (!payload?.request_id || payload.action !== "ADD" || !id || !shop) return false;
        const currentProduct = state.location?.page?.type === "product" ? state.location.page.data.product.id : null;
        if (currentProduct === id) return false;
        const requestId = payload.request_id;
        const origin = state.location.url;
        const ownGeneration = generation;
        const answer = (proceed: boolean, reason?: string) => nube.send("cart:before_update:result", () => ({
            eventPayload: {request_id: requestId, proceed, reason},
        }));
        void policy(shop, id).then(config => {
            if (safeState(nube)?.location.url !== origin || ownGeneration !== generation) {
                answer(false, "quick_buy_context_changed"); return;
            }
            const existing = state.cart?.items?.find(item => item.product_id === id
                && (!payload.item?.variant_id || item.variant_id === payload.item.variant_id));
            const saved = Object.fromEntries(normalizeProperties(existing?.properties).map(property => [property.name, property.value]));
            // Increasing a line that already has valid personalization needs no new form.
            if (!config.enabled || config.fields.length === 0 || ((payload.item?.previous_quantity ?? 0) > 0
                && Object.keys(saved).length > 0 && validate(config.fields, saved, config.locale).length === 0)) {
                answer(true); return;
            }
            answer(false, "personalization_requires_product_page");
            redirect(state, id);
        }, () => {
            answer(false, "personalization_policy_unavailable");
            if (safeState(nube)?.location.url === origin && ownGeneration === generation) redirect(state, id);
        });
        return true;
    }

    return {intercept, locationChanged};
}
