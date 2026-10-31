import {afterEach,beforeEach,expect,it,vi} from "vitest";
import type {CartItem,NubeSDK,NubeSDKState} from "@tiendanube/nube-sdk-types";
import {DISABLED,type PersonalizationConfig} from "../shared/config";
import {installPersonalizationGuard,missingRequiredFields} from "./personalization-guard";
const config:PersonalizationConfig={...DISABLED,enabled:true,fields:[{label:"Nome",propertyName:"Nome",required:true,fieldType:"TEXT",maxLength:100,placeholder:null,validationPattern:null,options:[]}]};
const item=(properties:unknown,variant=1)=>({product_id:10,variant_id:variant,name:"Caneca",properties}) as CartItem;
const state=(items:CartItem[])=>({store:{id:42,language:"es"},cart:{items}}) as unknown as NubeSDKState;
let nube:NubeSDK;
const validations=()=>vi.mocked(nube.send).mock.calls.filter(([event])=>event==="cart:validate").map(([,update])=>(update!({} as NubeSDKState) as {cart:{validation:unknown}}).cart.validation);
beforeEach(()=>{vi.stubGlobal("self",{__APP_DATA__:{id:"guard"}});nube={send:vi.fn(),render:vi.fn(),clearSlot:vi.fn()} as unknown as NubeSDK;});
afterEach(()=>vi.unstubAllGlobals());
it("checks every persisted line, accepts both API property shapes",()=>{
 expect(missingRequiredFields(item([]),config)).toEqual(["Nome"]);
 expect(missingRequiredFields(item({Nome:"  "}),config)).toEqual(["Nome"]);
 expect(missingRequiredFields(item({Nome:"Ana"}),config)).toEqual([]);
 expect(missingRequiredFields(item([{name:"Nome",value:"Ana"}]),config)).toEqual([]);
 expect(missingRequiredFields(item([null]),config)).toEqual(["Nome"]);
 expect(missingRequiredFields(item([]),DISABLED)).toEqual([]);
});
it("blocks payment for a missing required field even when another line is personalized",async()=>{
 const guard=installPersonalizationGuard(nube,vi.fn().mockResolvedValue(config));
 await guard(state([item({Nome:"Ana"}),item([])]));
 expect(validations()).toEqual([{status:"pending"},{status:"fail",reason:expect.stringContaining("Faltan datos")}]);
 expect(JSON.stringify(vi.mocked(nube.render).mock.calls)).toContain("Caneca: Nome");
});
it("releases a complete cart and an empty cart",async()=>{
 const guard=installPersonalizationGuard(nube,vi.fn().mockResolvedValue(config));
 await guard(state([item({Nome:"Ana"})]));
 expect(validations().slice(-1)[0]).toEqual({status:"success"});
 await guard(state([]));expect(validations().slice(-1)[0]).toEqual({status:"success"});
});
it("does not treat a failed configuration lookup as an unconfigured product and retries",async()=>{
 const load=vi.fn().mockRejectedValueOnce(new Error("offline")).mockResolvedValue(config);
 const guard=installPersonalizationGuard(nube,load);
 await guard(state([item([])]));
 expect(validations().slice(-1)[0]).toEqual({status:"fail",reason:expect.stringContaining("verificar")});
 await guard(state([item({Nome:"Ana"})]));
 expect(validations().slice(-1)[0]).toEqual({status:"success"});expect(load).toHaveBeenCalledTimes(2);
});
it("cannot release a newer incomplete cart using an older complete-cart response",async()=>{
 let resolve!:(c:PersonalizationConfig)=>void;
 const guard=installPersonalizationGuard(nube,()=>new Promise(r=>{resolve=r;}));
 const old=guard(state([item({Nome:"Ana"})]));const current=guard(state([item([])]));
 resolve(config);await Promise.all([old,current]);
 expect(validations()).toEqual([{status:"pending"},{status:"pending"},{status:"fail",reason:expect.any(String)}]);
});
it("fails when the cart or store identity is unavailable",async()=>{
 const guard=installPersonalizationGuard(nube);
 await guard(null);expect(validations().slice(-1)[0]).toEqual({status:"fail",reason:expect.any(String)});
});
