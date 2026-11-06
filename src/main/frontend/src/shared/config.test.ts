import {afterEach,expect,it,vi} from "vitest";
import {DISABLED,fetchConfig,fetchConfigRequired} from "./config";
afterEach(()=>vi.unstubAllGlobals());
it("preserves an explicit paused status and shop locale without exposing billing metadata",async()=>{
 vi.stubGlobal("fetch",vi.fn().mockResolvedValue({ok:true,json:async()=>({...DISABLED,paused:true,locale:"es-MX",billingReason:"private"})}));
 const config=await fetchConfigRequired(42,10);
 expect(config).toMatchObject({enabled:false,paused:true,fields:[],locale:"es-MX"});
 expect(config).not.toHaveProperty("billingReason");
});
it("keeps an ordinary unconfigured product silent",async()=>{
 vi.stubGlobal("fetch",vi.fn().mockResolvedValue({ok:true,json:async()=>DISABLED}));
 expect(await fetchConfigRequired(42,10)).toBe(DISABLED);
});
it("does not turn a network failure into instructions to send personalization to the shop",async()=>{
 vi.stubGlobal("fetch",vi.fn().mockRejectedValue(new Error("offline")));
 expect(await fetchConfig(42,10)).toBe(DISABLED);
});
