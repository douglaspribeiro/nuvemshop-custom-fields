import type { NubeSDKState } from "@tiendanube/nube-sdk-types";
import type { PersonalizationConfig } from "./config";

export function personalizationAllowed(config: PersonalizationConfig, variant: number | null | undefined): boolean {
    return !config.variantRestricted || (Number.isSafeInteger(variant) && (config.variantIds ?? []).includes(variant!));
}

export function eventVariant(state: NubeSDKState | null): number | null {
    const payload = state?.eventPayload as { product_id?: number; variant_id?: number; id?: number; variant?: {id?: number} } | undefined;
    const page = state?.location?.page;
    if (page?.type !== "product") return null;
    if (payload?.product_id != null && payload.product_id !== page.data.product.id) return null;
    const id = payload?.variant_id ?? payload?.variant?.id ?? payload?.id;
    return page.data.product.variants?.some(v => v.id === id) ? id! : null;
}

/** The bridge can merge additions by variant and discard new properties. Refuse conflicting drafts. */
export function conflictsWithCart(state: NubeSDKState | null, product: number | null, variant: number | null | undefined,
    properties: Record<string, string>): boolean {
    const signature = (props: Record<string, string>) => JSON.stringify(Object.entries(props).sort(([a], [b]) => a.localeCompare(b)));
    const entries = (properties: unknown): Record<string, string> => {
        if (Array.isArray(properties)) return Object.fromEntries(properties.filter(p => p && typeof p === "object")
            .map(p => [p.name ?? p.label ?? p.key, String(p.value ?? p.text ?? "")]).filter(([key, value]) => key && value));
        if (properties && typeof properties === "object") return Object.fromEntries(Object.entries(properties).map(([key, value]) => [key, String(value)]));
        return {};
    };
    return (state?.cart?.items ?? []).some(item => item.product_id === product && item.variant_id === variant &&
        signature(entries(item.properties)) !== signature(properties));
}
