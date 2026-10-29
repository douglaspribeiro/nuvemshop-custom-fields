import { expect, it } from "vitest";
import type { NubeSDKState } from "@tiendanube/nube-sdk-types";
import { DISABLED } from "./config";
import { personalizationAllowed, eventVariant, conflictsWithCart } from "./variants";

it("keeps old products unrestricted and fails closed for missing, removed or unselected IDs", () => {
    expect(personalizationAllowed(DISABLED,null)).toBe(true);
    const config={...DISABLED,variantRestricted:true,variantIds:[102]};
    for (const id of [null,undefined,101,0,999]) expect(personalizationAllowed(config,id)).toBe(false);
    expect(personalizationAllowed(config,102)).toBe(true);
    expect(personalizationAllowed({...config,variantIds:[]},102)).toBe(false);
});
it("accepts only variant IDs from the current product catalogue", () => {
    const state={location:{page:{type:"product",data:{product:{id:10,variants:[{id:102}]}}}}} as unknown as NubeSDKState;
    for(const eventPayload of [{variant_id:102},{id:102},{variant:{id:102}}])
        expect(eventVariant({...state,eventPayload})).toBe(102);
    expect(eventVariant({...state,eventPayload:{product_id:20,variant_id:102}})).toBeNull();
    expect(eventVariant({...state,eventPayload:{variant_id:999}})).toBeNull();
});
it("distinguishes products, variants, visible text and private upload references", () => {
    const state={cart:{items:[{product_id:10,variant_id:102,properties:{Arte:"arte.png",_ncf_file_1:"NCF-FILE:one"}}]}} as unknown as NubeSDKState;
    expect(conflictsWithCart(state,10,102,{Arte:"arte.png",_ncf_file_1:"NCF-FILE:two"})).toBe(true);
    expect(conflictsWithCart(state,10,102,{Arte:"arte.png",_ncf_file_1:"NCF-FILE:one"})).toBe(false);
    expect(conflictsWithCart(state,10,101,{})).toBe(false);
    expect(conflictsWithCart(state,20,102,{})).toBe(false);
    expect(conflictsWithCart(state,10,102,{})).toBe(true);
});
