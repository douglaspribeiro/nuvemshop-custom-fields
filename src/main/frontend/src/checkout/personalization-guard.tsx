import { Button, Column, Text } from "@tiendanube/nube-sdk-jsx";
import type { CartItem, NubeSDK, NubeSDKState } from "@tiendanube/nube-sdk-types";
import { fetchConfigRequired, storeId, type PersonalizationConfig } from "../shared/config";
import { langOf } from "../shared/i18n";

const SLOT = "before_line_items";

/** Check persisted cart lines, never the product form's transient values. */
export function missingRequiredFields(item: CartItem, config: PersonalizationConfig): string[] {
    if (!config.enabled) return [];
    const properties = Array.isArray(item.properties)
        ? Object.fromEntries(item.properties.filter((p): p is Record<string, unknown> => !!p && typeof p === "object")
            .map(p => [p.name ?? p.key ?? p.label, p.value ?? p.text]))
        : item.properties ?? {};
    return config.fields.filter(field => field.required || field.fieldType === "SELECT")
        .filter(field => {
            const value = properties[field.propertyName || field.label];
            if (Array.isArray(value)) return !value.some(v => typeof v === "string" && v.trim());
            return !["string", "number", "boolean"].includes(typeof value) || !String(value).trim();
        }).map(field => field.label);
}

export function installPersonalizationGuard(nube: NubeSDK, load = fetchConfigRequired) {
    let generation = 0;
    let latest: NubeSDKState | null = null;
    const cache = new Map<string, {expires: number; promise: Promise<PersonalizationConfig>}>();
    const policy = (store: number, product: number) => {
        const key = `${store}:${product}`;
        const cached = cache.get(key);
        if (cached && cached.expires > Date.now()) return cached.promise;
        const promise = load(store, product);
        cache.set(key, {expires: Date.now() + 60_000, promise});
        void promise.catch(() => { if (cache.get(key)?.promise === promise) cache.delete(key); });
        return promise;
    };
    const publish = (validation: {status: "pending" | "success"} | {status: "fail"; reason: string}) =>
        nube.send("cart:validate", () => ({cart: {validation}}));
    const refresh = async (state: NubeSDKState | null) => {
        latest = state;
        const current = ++generation;
        publish({status: "pending"});
        const pt = langOf(state?.store?.language) === "pt";
        const unavailable = pt ? "Não foi possível verificar a personalização. Tente novamente antes de continuar."
            : "No pudimos verificar la personalización. Intentá nuevamente antes de continuar.";
        try {
            const store = storeId(state);
            if (!store || !Array.isArray(state?.cart?.items)) throw new Error("cart_unavailable");
            const issues = await Promise.all(state.cart.items.map(async item => {
                if (!Number.isSafeInteger(item.product_id) || item.product_id <= 0) throw new Error("product_unavailable");
                return {item, fields: missingRequiredFields(item, await policy(store, item.product_id))};
            }));
            if (current !== generation) return;
            const missing = issues.filter(issue => issue.fields.length);
            if (!missing.length) { publish({status: "success"}); nube.clearSlot(SLOT); return; }
            const reason = pt ? "Faltam dados obrigatórios da personalização. Volte à loja e revise os produtos antes de pagar."
                : "Faltan datos obligatorios de personalización. Volvé a la tienda y revisá los productos antes de pagar.";
            publish({status: "fail", reason});
            nube.render(SLOT, <Column gap={8}><Text>{reason}</Text>
                <Column gap={4}>{missing.map(({item, fields}) => <Text>{`${item.name}: ${fields.join(", ")}`}</Text>)}</Column>
            </Column>);
        } catch {
            if (current !== generation) return;
            publish({status: "fail", reason: unavailable});
            nube.render(SLOT, <Column gap={8}><Text>{unavailable}</Text>
                <Button onClick={() => { cache.clear(); void refresh(latest); }}>{pt ? "Tentar novamente" : "Intentar nuevamente"}</Button>
            </Column>);
        }
    };
    nube.send("config:set", () => ({config: {has_cart_validation: true}}));
    return Object.assign(refresh, {configFor: policy});
}
