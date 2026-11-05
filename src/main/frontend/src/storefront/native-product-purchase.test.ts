import { expect,it,vi } from "vitest";
import type { NubeSDK } from "@tiendanube/nube-sdk-types";
import { createNativeProductPurchase } from "./native-product-purchase";
function setup(){
 const set=vi.fn().mockResolvedValue({ok:true,applied:1,refused:0});
 const get=vi.fn().mockResolvedValue({ok:true,values:{disabled:true}});
 const report=vi.fn();
 const guard=createNativeProductPurchase({api:{getCustomization:()=>({set,get})}} as unknown as NubeSDK,report);
 return{guard,set,get,report};
}
it('protects a configured PDP once and releases its native action when configuration is removed',async()=>{
 const {guard,set}=setup();await guard.update(123);await guard.update(123);
 expect(set.mock.calls).toEqual([['add-to-cart-button',{disabled:true}]]);
 await guard.update(null);expect(set.mock.calls[set.mock.calls.length-1]).toEqual(['add-to-cart-button',{disabled:false}]);
});
it('does not change native buttons before a configured replacement is available',async()=>{
 const {guard,set}=setup();await guard.update(null);expect(set).not.toHaveBeenCalled();
});
it('ignores an obsolete request when the customer navigates before it starts',async()=>{
 const {guard,set}=setup();const pending=guard.update(123);await guard.update(null);await pending;expect(set).not.toHaveBeenCalled();
});
it('does not treat a successful command that ignored disabled as protection',async()=>{
 const {guard,get,report}=setup();get.mockResolvedValue({ok:true,values:{disabled:false}});
 await guard.update(123);expect(report).toHaveBeenCalledWith(123,'native_purchase_unconfirmed');
});
it('handles missing API, unmatched markup and bridge errors without blocking the SDK app',async()=>{
 const report=vi.fn(),missing=createNativeProductPurchase({} as NubeSDK,report);await missing.update(123);
 expect(report).toHaveBeenCalledWith(123,'native_purchase_api_unavailable');
 const test=setup();test.set.mockResolvedValue({ok:false,code:'not_found',reason:'missing native selector'});await test.guard.update(123);
 expect(test.report).toHaveBeenCalledWith(123,'native_purchase_not_found');
 const broken=setup();broken.set.mockRejectedValue(Error('offline'));await broken.guard.update(123);
 expect(broken.report).toHaveBeenCalledWith(123,'native_purchase_bridge_error');
});
