import type { NubeSDK } from "@tiendanube/nube-sdk-types";

export type PersonalizedAdd = { product_id: number; variant_id: number; quantity: number; properties: Record<string, string> };
export type CartMutation = { request_id?: string; action?: string; item?: {
    product_id?: number | null; variant_id?: number | null; previous_quantity?: number; new_quantity?: number;
} };

/** The SDK gates our own adds too. Match their product/delta, never an unrelated operation. */
export function createCartAdder(nube: NubeSDK) {
    let pending: { product: number; variant: number; quantity: number; expires: number }[] = [];
    return {
        add(item: PersonalizedAdd) {
            pending.push({product: item.product_id, variant: item.variant_id, quantity: item.quantity, expires: Date.now() + 3000});
            nube.send("cart:add", () => ({cart: {items: [item]}}));
        },
        settle(product?: number, variant?: number) {
            pending = product == null ? [] : pending.filter(item => item.product !== product || (variant != null && item.variant !== variant));
        },
        claim(payload: CartMutation | null | undefined) {
            pending = pending.filter(item => item.expires > Date.now());
            const item = payload?.item;
            if (payload?.action !== "ADD" || !item?.product_id) return false;
            const delta = (item.new_quantity ?? 0) - (item.previous_quantity ?? 0);
            const index = pending.findIndex(sent => sent.product === item.product_id && sent.quantity === delta
                && (item.variant_id == null || sent.variant === item.variant_id));
            if (index < 0) return false;
            pending.splice(index, 1);
            return true;
        },
    };
}
