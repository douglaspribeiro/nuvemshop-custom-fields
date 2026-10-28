import { beforeEach, afterEach, describe, it, expect, vi } from "vitest";
import { createBuyerUploads } from "./buyer-uploads";
import { validate, toCartProperties } from "./values";
import type { PersonalizationField } from "../shared/config";

const id="00000000-0000-4000-8000-000000000001";
const field:PersonalizationField={id:11,label:"Arte",propertyName:"_ncf_file_11",fieldType:"FILE_UPLOAD",required:true,options:["PNG"],maxLength:null,placeholder:null,validationPattern:null};
const cache=new Map<string,string>();
const storage={getItem:(key:string)=>cache.get(key)||null,setItem:(key:string,value:string)=>cache.set(key,value)};
beforeEach(()=>{cache.clear();vi.stubGlobal("XMLHttpRequest",undefined);vi.stubGlobal("fetch",vi.fn());});
afterEach(()=>vi.unstubAllGlobals());
function reply(body:unknown,status=200){return Promise.resolve(new Response(JSON.stringify(body),{status,headers:{"Content-Type":"application/json"}}));}
describe("buyer upload",()=>{
    it("keeps mandatory and optional items blocked during an incomplete upload",()=>{
        expect(validate([field],{})).toHaveLength(1);
        expect(validate([{...field,required:false}],{})).toHaveLength(0);
        expect(validate([{...field,required:false}],{[field.propertyName]:"__uploading__"})).toHaveLength(1);
        const value=`arte.png (10 bytes) — NCF-FILE:${id}`;
        expect(validate([field],{[field.propertyName]:value})).toHaveLength(0);
        expect(toCartProperties([field],{[field.propertyName]:value})).toEqual({[field.propertyName]:value, Arte:"arte.png (10 bytes)"});
    });
    it("reserves a session and confirms cart linkage before exposing the reference",async()=>{
        const fetch=vi.mocked(globalThis.fetch);
        fetch.mockImplementationOnce(()=>reply({token:"a".repeat(64)}))
            .mockImplementationOnce(()=>reply({id,name:"arte.png",size:10,reference:`NCF-FILE:${id}`}))
            .mockImplementationOnce(()=>reply({ready:true}));
        const progress=vi.fn();const value=await createBuyerUploads(1,123,storage).upload(field,new File(["1234567890"],"arte.png"),progress);
        expect(fetch.mock.calls.map(call=>String(call[0]))).toEqual(["https://app.test/public/stores/1/buyer-uploads/session","https://app.test/public/stores/1/buyer-uploads","https://app.test/public/stores/1/buyer-uploads/"+id+"/validate"]);
        expect(value).toContain(`NCF-FILE:${id}`);expect(progress).toHaveBeenCalled();
        expect(cache.get("ncf-upload-1-123-11")).toBe(value);
    });
    it("does not persist a file when its cart confirmation fails",async()=>{
        const fetch=vi.mocked(globalThis.fetch);
        fetch.mockImplementationOnce(()=>reply({token:"a".repeat(64)}))
            .mockImplementationOnce(()=>reply({id,name:"arte.png",size:10,reference:`NCF-FILE:${id}`}))
            .mockImplementationOnce(()=>reply({error:"Sessão expirada."},400));
        await expect(createBuyerUploads(1,123,storage).upload(field,new File(["1234567890"],"arte.png"),()=>{})).rejects.toThrow("Sessão expirada.");
        expect(cache.has("ncf-upload-1-123-11")).toBe(false);
    });
    it("rejects a large file without sending it",async()=>{
        const fake={size:20_000_001} as File;
        await expect(createBuyerUploads(1,123,storage).upload(field,fake,()=>{})).rejects.toThrow("20 MB");
        expect(globalThis.fetch).not.toHaveBeenCalled();
    });
});
