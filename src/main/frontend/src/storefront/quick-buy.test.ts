import { afterEach, beforeEach, expect, it, vi } from "vitest";
import type { NubeSDK, NubeSDKState } from "@tiendanube/nube-sdk-types";
import { DISABLED } from "../shared/config";
import { installQuickBuy } from "./quick-buy";

let state:NubeSDKState;
let listeners:Map<string,(state:NubeSDKState)=>void>;
let navigate:ReturnType<typeof vi.fn>,send:ReturnType<typeof vi.fn>,render:ReturnType<typeof vi.fn>,fetchPolicy:ReturnType<typeof vi.fn>;
let nube:NubeSDK;
let quick:ReturnType<typeof installQuickBuy>;
const configured={...DISABLED,enabled:true,fields:[{label:"Moagem",propertyName:"Moagem",fieldType:"SELECT",required:true,options:["En Grano","Molido"]}]};
const product={id:456,canonical_url:"/produtos/cafe/",variants:[{id:789,values:[{pt:"Padrão"}]}]};
type Node={__internalId?:string;type?:string;name?:string;id?:string;value?:string;children?:Node[];onChange?:(event:{value:string})=>void;onClick?:()=>void};
function nodes(value:unknown):Node[]{
 if(!value || typeof value!=="object")return[];
 if(Array.isArray(value))return value.flatMap(nodes);
 const n=value as Node;return[n,...nodes(n.children)];
}
function tree(){return render.mock.calls.filter(([slot])=>slot==="before_quick_buy_add_to_cart").slice(-1)[0]?.[1];}
function control(name:string){return nodes(tree()).find(n=>n.name===name)!;}
function button(){return nodes(tree()).find(n=>n.type==="button" && n.onClick)!;}
async function open(p=product){const count=render.mock.calls.length;listeners.get("quickbuy:open")!({...state,eventPayload:p} as unknown as NubeSDKState);await vi.waitFor(()=>{expect(render.mock.calls.length).toBeGreaterThan(count);expect(control(`ncf-quickbuy-${p.id}-Moagem`)).toBeDefined();});}
function add(id=456,previous=0,next=previous+1,variant:number|null=789){return {...state,eventPayload:{request_id:"native",action:"ADD",item:{product_id:id,variant_id:variant,previous_quantity:previous,new_quantity:next}}} as unknown as NubeSDKState;}
function answer(){return send.mock.calls.filter(([event])=>event==="cart:before_update:result").slice(-1)[0]?.[1]().eventPayload;}
function additions(){return send.mock.calls.filter(([event])=>event==="cart:add").map(([,fn])=>fn().cart.items[0]);}
beforeEach(()=>{
 vi.useFakeTimers();
 vi.stubGlobal("self",{__APP_DATA__:{id:"quick-buy-test"}});
 state={store:{id:123,language:"pt",theme:"patagonia"},cart:{items:[]},location:{url:"https://store.test/",page:{type:"home",data:{sections:[{products:[product]}]}}}} as unknown as NubeSDKState;
 listeners=new Map();navigate=vi.fn();send=vi.fn();render=vi.fn();
 fetchPolicy=vi.fn().mockResolvedValue({ok:true,json:async()=>configured});vi.stubGlobal("fetch",fetchPolicy);
 nube={getState:()=>state,getBrowserAPIs:()=>({navigate}),send,render,clearSlot:vi.fn(),on:(e:string,fn:(s:NubeSDKState)=>void)=>listeners.set(e,fn)} as unknown as NubeSDK;
 quick=installQuickBuy(nube);
});
afterEach(()=>{vi.clearAllTimers();vi.useRealTimers();vi.unstubAllGlobals();});

it.each([123,959276,5656805])("renders quick-buy fields without navigating for store %s",async id=>{
 state.store.id=id;await open();expect(navigate).not.toHaveBeenCalled();expect(nodes(tree()).some(n=>n.id==="ncf-sdk-quickbuy-fields")).toBe(true);
 expect(fetchPolicy.mock.calls[0][0]).toContain(`/stores/${id}/personalization?productId=456`);
 expect(additions()).toHaveLength(0);
});
it("validates the native add and reissues the selected properties and quantity delta once",async()=>{
 await open();quick.intercept(add());expect(answer().proceed).toBe(false);expect(additions()).toHaveLength(0);
 control("ncf-quickbuy-456-Moagem").onChange!({value:"Molido"});quick.intercept(add(456,2,7,null));
 expect(additions()).toEqual([{product_id:456,variant_id:789,quantity:5,properties:{Moagem:"Molido"}}]);
 quick.intercept(add(456,2,7,null));expect(answer().proceed).toBe(true);expect(additions()).toHaveLength(1);
});
it("provides a direct personalized add with validated quantity and single variant",async()=>{
 await open();control("ncf-quickbuy-456-Moagem").onChange!({value:"En Grano"});control("ncf-quickbuy-quantity").onChange!({value:"0"});button().onClick!();
 expect(additions()).toHaveLength(0);control("ncf-quickbuy-quantity").onChange!({value:"3"});button().onClick!();
 expect(additions()[0]).toMatchObject({quantity:3,properties:{Moagem:"En Grano"}});button().onClick!();expect(additions()).toHaveLength(1);
});
it("preserves an existing choice while sending a different personalization",async()=>{
 state.cart.items=[{id:1,product_id:456,variant_id:789,quantity:1,properties:{Moagem:"En Grano"}}] as unknown as NubeSDKState["cart"]["items"];
 await open();control("ncf-quickbuy-456-Moagem").onChange!({value:"Molido"});control("ncf-quickbuy-quantity").onChange!({value:"6"});button().onClick!();
 expect(additions()[0]).toMatchObject({quantity:6,properties:{Moagem:"Molido"}});
 expect(state.cart.items[0].properties).toEqual({Moagem:"En Grano"});
});
it("requires an explicit variant when the theme does not report one",async()=>{
 await open({...product,variants:[{id:789,values:[{pt:"Pequeno"}]},{id:790,values:[{pt:"Grande"}]}]});
 control("ncf-quickbuy-456-Moagem").onChange!({value:"Molido"});button().onClick!();expect(additions()).toHaveLength(0);
 control("ncf-quickbuy-variant").onChange!({value:"790"});button().onClick!();expect(additions()[0].variant_id).toBe(790);
});
it("keeps drafts isolated across products and ignores callbacks from a closed form",async()=>{
 await open();const stale=control("ncf-quickbuy-456-Moagem");stale.onChange!({value:"Molido"});listeners.get("quickbuy:close")!(state);
 await open({...product,id:999});stale.onChange!({value:"En Grano"});expect(control("ncf-quickbuy-999-Moagem").value).toBe("");
 listeners.get("quickbuy:close")!(state);await open();expect(control("ncf-quickbuy-456-Moagem").value).toBe("Molido");
});
it("retains input on failed or unconfirmed adds, clearing only a confirmed matching choice",async()=>{
 await open();control("ncf-quickbuy-456-Moagem").onChange!({value:"Molido"});button().onClick!();
 quick.finished({...state,eventPayload:{product_id:456,variant_id:789}} as NubeSDKState,false);expect(control("ncf-quickbuy-456-Moagem").value).toBe("Molido");
 button().onClick!();quick.finished({...state,eventPayload:{product_id:456,variant_id:789}} as NubeSDKState,true);expect(control("ncf-quickbuy-456-Moagem").value).toBe("Molido");
 button().onClick!();quick.finished({...state,eventPayload:{product_id:456,variant_id:789,properties:{Moagem:"Molido"}}} as NubeSDKState,true);
 expect(control("ncf-quickbuy-456-Moagem").value).toBe("");
});
it("falls back to the PDP when a theme lacks the quick-buy slot",async()=>{
 state.store.theme="idea";listeners.get("quickbuy:open")!({...state,eventPayload:product} as unknown as NubeSDKState);
 await vi.waitFor(()=>expect(navigate).toHaveBeenCalledWith("/produtos/cafe/"));expect(tree()).toBeUndefined();
});
it("discovers and uses the quick-buy slot on another compatible theme",async()=>{
 state.store.theme="other";Object.assign(nube,{api:{getAvailableSlots:()=>({getStatic:async()=>[{slotId:"before_quick_buy_add_to_cart"}]})}});
 await open();expect(navigate).not.toHaveBeenCalled();
});
it("retains native purchase for products without fields or with paused configuration",async()=>{
 fetchPolicy.mockResolvedValue({ok:true,json:async()=>({...DISABLED,paused:true})});
 listeners.get("quickbuy:open")!({...state,eventPayload:product} as unknown as NubeSDKState);quick.intercept(add());
 await vi.waitFor(()=>expect(answer().proceed).toBe(true));expect(navigate).not.toHaveBeenCalled();expect(tree()).toBeUndefined();
});
it("keeps direct grid additions safe when no quick-buy form is open",async()=>{
 quick.intercept(add());await vi.waitFor(()=>expect(navigate).toHaveBeenCalledWith("/produtos/cafe/"));expect(answer().proceed).toBe(false);
});
it("leaves PDP purchase and removals to their existing handlers",()=>{
 state.location.page={type:"product",data:{product}} as unknown as NubeSDKState["location"]["page"];
 expect(quick.intercept(add())).toBe(false);
 expect(quick.intercept({...state,eventPayload:{request_id:"remove",action:"REMOVE",item:{product_id:456}}} as NubeSDKState)).toBe(false);
 expect(fetchPolicy).not.toHaveBeenCalled();
});
it("cancels safely on policy failure without adding an unpersonalized item",async()=>{
 fetchPolicy.mockRejectedValue(Error("offline"));quick.intercept(add());
 await vi.waitFor(()=>expect(answer()).toMatchObject({proceed:false,reason:"personalization_policy_unavailable"}));expect(additions()).toHaveLength(0);
});
it("ignores late responses after the modal closes or location changes",async()=>{
 let resolve!:(response:unknown)=>void;fetchPolicy.mockReturnValue(new Promise(done=>{resolve=done;}));
 listeners.get("quickbuy:open")!({...state,eventPayload:product} as unknown as NubeSDKState);listeners.get("quickbuy:close")!(state);
 resolve({ok:true,json:async()=>configured});await vi.advanceTimersByTimeAsync(1);expect(tree()).toBeUndefined();expect(navigate).not.toHaveBeenCalled();
});
it("cancels a pending grid add if the customer navigates during lookup",async()=>{
 let resolve!:(response:unknown)=>void;fetchPolicy.mockReturnValue(new Promise(done=>{resolve=done;}));quick.intercept(add());
 state={...state,location:{...state.location,url:"https://store.test/contact/"}};quick.locationChanged();resolve({ok:true,json:async()=>configured});
 await vi.waitFor(()=>expect(answer()).toMatchObject({proceed:false,reason:"quick_buy_context_changed"}));expect(navigate).not.toHaveBeenCalled();
});

it("releases a failed add with the SDK's null payload and preserves its choices",async()=>{
 await open();control("ncf-quickbuy-456-Moagem").onChange!({value:"Molido"});button().onClick!();
 quick.finished({...state,eventPayload:null},false);
 expect(control("ncf-quickbuy-456-Moagem").value).toBe("Molido");button().onClick!();expect(additions()).toHaveLength(2);
});
it("times out without resending or erasing the draft",async()=>{
 await open();control("ncf-quickbuy-456-Moagem").onChange!({value:"Molido"});button().onClick!();
 await vi.advanceTimersByTimeAsync(15001);expect(additions()).toHaveLength(1);expect(control("ncf-quickbuy-456-Moagem").value).toBe("Molido");
 expect(JSON.stringify(tree())).toContain("Confira o carrinho");
});
it("does not infer one personalized line from several matching cart lines",async()=>{
 state.cart.items=[{product_id:456,variant_id:789,properties:{Moagem:'En Grano'}},{product_id:456,variant_id:789,properties:{Moagem:'Molido'}}] as unknown as NubeSDKState['cart']['items'];
 quick.intercept(add(456,2,3));await vi.waitFor(()=>expect(answer()?.proceed).toBe(false));expect(additions()).toHaveLength(0);
});

it("falls back instead of rendering a form with no resolvable variant",async()=>{
 listeners.get("quickbuy:open")!({...state,eventPayload:{id:456,canonical_url:'/produtos/cafe/'}} as unknown as NubeSDKState);
 await vi.waitFor(()=>expect(navigate).toHaveBeenCalledWith('/produtos/cafe/'));expect(tree()).toBeUndefined();
});

it("replaces configured Patagonia card actions with a personalized modal, preserving native actions for unconfigured products",async()=>{
 state.location.page={type:'products',data:{products:[product,{...product,id:999}]}} as unknown as NubeSDKState['location']['page'];
 fetchPolicy.mockImplementation(async(url:string)=>({ok:true,json:async()=>url.includes('productId=999')?DISABLED:configured}));
 await quick.syncCatalog(state);
 const cards=render.mock.calls.filter(([slot])=>slot==='before_product_grid_item_name').slice(-1)[0][1];
 expect(cards).toHaveLength(1);expect(cards[0].key).toBe(456);
 nodes(cards).find(n=>n.type==='button')!.onClick!();
 const modal=render.mock.calls.filter(([slot])=>slot==='modal_content').slice(-1)[0][1];
 expect(nodes(modal).some(n=>n.name==='ncf-quickbuy-456-Moagem')).toBe(true);
 expect(navigate).not.toHaveBeenCalled();
});
it("routes a failed card policy to the product page instead of adding without fields",async()=>{
 fetchPolicy.mockRejectedValue(Error('offline'));
 state.location.page={type:'products',data:{products:[product]}} as unknown as NubeSDKState['location']['page'];
 await quick.syncCatalog(state);
 const cards=render.mock.calls.filter(([slot])=>slot==='before_product_grid_item_name').slice(-1)[0][1];
 nodes(cards).find(n=>n.type==='button')!.onClick!();expect(navigate).toHaveBeenCalledWith('/produtos/cafe/');expect(additions()).toHaveLength(0);
});
it("does not restore catalog actions after navigating away during policy lookup",async()=>{
 let resolve!:(response:unknown)=>void;fetchPolicy.mockReturnValue(new Promise(done=>{resolve=done;}));
 state.location.page={type:'products',data:{products:[product]}} as unknown as NubeSDKState['location']['page'];
 const pending=quick.syncCatalog(state);const count=render.mock.calls.length;
 quick.locationChanged();resolve({ok:true,json:async()=>configured});await pending;
 expect(render.mock.calls).toHaveLength(count);
});

it("keeps action event identifiers separate for two configured cards",async()=>{
 state.location.page={type:'products',data:{products:[product,{...product,id:999}]}} as unknown as NubeSDKState['location']['page'];
 await quick.syncCatalog(state);
 const cards=render.mock.calls.filter(([slot])=>slot==='before_product_grid_item_name').slice(-1)[0][1];
 const actions=cards.map((card:unknown)=>nodes(card).find(n=>n.type==='button'));
 expect(actions[0].__internalId).not.toBe(actions[1].__internalId);
 actions[0].onClick!();
 const first=render.mock.calls.filter(([slot])=>slot==='modal_content').slice(-1)[0][1];
 expect(nodes(first).some(n=>n.name==='ncf-quickbuy-456-Moagem')).toBe(true);
});
