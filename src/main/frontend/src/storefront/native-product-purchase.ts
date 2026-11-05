import type { NubeSDK } from "@tiendanube/nube-sdk-types";

type Result = {ok:true; applied?:number; values?:{disabled?:boolean}} | {ok:false;code:string;reason:string};
type Customization = {
    set(target:"add-to-cart-button", options:{disabled:boolean}):Promise<Result>;
    get(target:"add-to-cart-button"):Promise<Result>;
};

/** Older SDK types/runtimes lack Customization. Detect it without assuming support. */
export function createNativeProductPurchase(nube:NubeSDK, report:(product:number|null,reason:string)=>void=()=>{}) {
    let desired:number|null=null;
    let owned=false;
    let generation=0;
    let pending:Promise<void>=Promise.resolve();
    function update(product:number|null,force=false):Promise<void> {
        if(product===desired&&!force)return pending;
        desired=product;
        const current=++generation;
        pending=pending.catch(()=>{}).then(async()=>{
            if(current!==generation || (product===null&&!owned))return;
            const api=nube.api as unknown as {getCustomization?:()=>Customization}|undefined;
            if(!api?.getCustomization){report(product,"native_purchase_api_unavailable");return;}
            try {
                const customization=api.getCustomization();
                // Patagonia's product button has no form/product identifier. Its resolver
                // cannot select it. The unfiltered target covers the PDP's native buttons;
                // catalog buttons are separate controls and require theme compatibility CSS.
                const result=await customization.set("add-to-cart-button",{disabled:product!==null});
                if(!result.ok){report(product,`native_purchase_${result.code}`);return;}
                owned=product!==null;
                const read=await customization.get("add-to-cart-button");
                if(product!==null && (!read.ok || read.values?.disabled!==true))report(product,"native_purchase_unconfirmed");
            } catch {report(product,"native_purchase_bridge_error");}
        });
        return pending;
    }
    return {update,refresh:()=>update(desired,true)};
}
