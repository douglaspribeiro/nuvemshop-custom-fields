import { afterEach, beforeEach, expect, it, vi } from "vitest";
import type { NubeSDK, NubeSDKState } from "@tiendanube/nube-sdk-types";
import { DISABLED } from "../shared/config";

let state: NubeSDKState;
let listeners: Map<string, (state: NubeSDKState) => void>;
let nube: NubeSDK;
const config = {...DISABLED, enabled:true, fields:[{
    label:"Capa",propertyName:"Capa",fieldType:"IMAGE_SELECT",required:true,maxLength:100,
    placeholder:null,validationPattern:null,options:["Floral","Azul"],imageOptions:[
        {label:"Floral",thumbnailUrl:"/public/floral",imageUrl:"/public/floral-large"},
        {label:"Azul",thumbnailUrl:"/public/azul",imageUrl:"/public/azul-large"},
    ],
}]};
function cartItem(id:number, value:string) {
    return {id,product_id:100,name:"Caderno",properties:{Capa:value,Nome:"Ana",_interna:"oculta"}};
}
const currentTree = () => JSON.stringify(vi.mocked(nube.render).mock.calls.slice(-1)[0]?.[1]);
const response = (body:unknown) => ({ok:true,json:async()=>body}) as Response;
async function boot() { const {App}=await import("./main");App(nube); }

beforeEach(()=>{
    vi.resetModules();
    vi.stubGlobal("self",{__APP_DATA__:{id:"test-checkout"}});
    state={store:{id:42},cart:{items:[cartItem(1,"Floral"),cartItem(2,"Azul")]}} as unknown as NubeSDKState;
    listeners=new Map();
    nube={getState:()=>state,render:vi.fn(),clearSlot:vi.fn(),send:vi.fn(),
        on:(event:string,listener:(state:NubeSDKState)=>void)=>listeners.set(event,listener)} as unknown as NubeSDK;
    vi.stubGlobal("fetch",vi.fn(async (url:string)=>response(url.includes("/style") ? {locale:"pt-BR",checkoutTextColor:"#123456"} : config)));
});
afterEach(()=>vi.unstubAllGlobals());

it("shows text and each item's selected thumbnail in the checkout summary",async()=>{
    await boot();
    await vi.waitFor(()=>expect(currentTree()).toContain("https://app.test/public/azul"));
    expect(currentTree()).toContain("https://app.test/public/floral");
    expect(currentTree()).toContain("Capa: Floral");expect(currentTree()).toContain("Capa: Azul");
    expect(currentTree()).toContain("Nome: Ana");expect(currentTree()).not.toContain("oculta");
    expect(currentTree()).toContain("#123456");
    expect(vi.mocked(fetch).mock.calls.filter(([url])=>String(url).includes("personalization"))).toHaveLength(1);
    expect(nube.send).not.toHaveBeenCalled();
});

it("refreshes selections and clears the summary when the cart becomes empty",async()=>{
    await boot();await vi.waitFor(()=>expect(currentTree()).toContain("https://app.test/public/floral"));
    state={...state,cart:{items:[cartItem(2,"Azul")]}} as unknown as NubeSDKState;
    listeners.get("cart:update")!(state);
    await vi.waitFor(()=>{expect(currentTree()).toContain("https://app.test/public/azul");expect(currentTree()).not.toContain("https://app.test/public/floral");});
    state={...state,cart:{items:[]}} as unknown as NubeSDKState;listeners.get("cart:update")!(state);
    expect(nube.clearSlot).toHaveBeenCalledWith("after_line_items");
});

it("keeps custom field names visible during configuration failures",async()=>{
    vi.mocked(fetch).mockImplementation(async (url)=>{
        if(String(url).includes("personalization")) throw new Error("offline");
        return response({locale:"pt-BR"});
    });
    await boot();await vi.waitFor(()=>expect(currentTree()).toContain("Nome: Ana"));
    expect(currentTree()).toContain("Capa: Floral");expect(currentTree()).not.toContain("/public/floral");
    expect(nube.send).not.toHaveBeenCalled();
});

it("ignores slow image and style responses after an item is removed",async()=>{
    const pending = new Map<string,(response:Response)=>void>();
    vi.mocked(fetch).mockImplementation(url=>new Promise<Response>(resolve=>pending.set(String(url),resolve)));
    await boot();await vi.waitFor(()=>expect(pending.size).toBe(2));
    state={...state,cart:{items:[]}} as unknown as NubeSDKState;listeners.get("cart:update")!(state);
    const calls=vi.mocked(nube.render).mock.calls.length;
    for(const [url,resolve] of pending) resolve(response(url.includes("/style") ? {locale:"pt-BR"} : config));
    await new Promise(resolve=>setTimeout(resolve,0));
    expect(vi.mocked(nube.render).mock.calls).toHaveLength(calls);
    expect(nube.clearSlot).toHaveBeenCalledWith("after_line_items");
});
