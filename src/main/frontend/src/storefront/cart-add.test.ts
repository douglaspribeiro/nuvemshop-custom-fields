import { afterEach, beforeEach, expect, it, vi } from "vitest";
import type { NubeSDK } from "@tiendanube/nube-sdk-types";
import { createCartAdder } from "./cart-add";
beforeEach(()=>vi.useFakeTimers());afterEach(()=>vi.useRealTimers());
const item={product_id:10,variant_id:20,quantity:5,properties:{Moagem:"Molido"}};
const mutation=(product=10,variant:number|null=20,quantity=5)=>({action:"ADD",item:{product_id:product,variant_id:variant,previous_quantity:7,new_quantity:7+quantity}});
it("matches only its own product, quantity delta and variant, allowing a null quick-buy variant",()=>{
 const send=vi.fn(),adder=createCartAdder({send} as unknown as NubeSDK);adder.add(item);
 expect(adder.claim(mutation(11))).toBe(false);expect(adder.claim(mutation(10,21))).toBe(false);expect(adder.claim(mutation(10,20,1))).toBe(false);
 expect(adder.claim(mutation(10,null))).toBe(true);expect(adder.claim(mutation())).toBe(false);
 expect(send.mock.calls[0][1]().cart.items).toEqual([item]);
});
it("expires unused echoes so a later genuine purchase still requires personalization",()=>{
 const adder=createCartAdder({send:vi.fn()} as unknown as NubeSDK);adder.add(item);vi.advanceTimersByTime(3001);expect(adder.claim(mutation())).toBe(false);
});
it("tracks simultaneous identical sends separately and never claims removals or unresolved products",()=>{
 const adder=createCartAdder({send:vi.fn()} as unknown as NubeSDK);adder.add(item);adder.add(item);
 expect(adder.claim({...mutation(),action:"REMOVE"})).toBe(false);expect(adder.claim({action:"ADD",item:{product_id:null}})).toBe(false);
 expect(adder.claim(mutation())).toBe(true);expect(adder.claim(mutation())).toBe(true);expect(adder.claim(mutation())).toBe(false);
});
