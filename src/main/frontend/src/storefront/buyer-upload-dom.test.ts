// @vitest-environment jsdom
import { beforeEach, afterEach, it, expect, vi } from "vitest";
import { buyerUploadControl } from "./buyer-upload-dom";
import { validate } from "./values";
import type { PersonalizationField } from "../shared/config";

const field:PersonalizationField={id:11,label:"Arte",propertyName:"_ncf_file_11",fieldType:"FILE_UPLOAD",required:true,options:["PNG"],maxLength:null,placeholder:"Envie sua foto",validationPattern:null};
beforeEach(()=>{sessionStorage.clear();document.body.replaceChildren();vi.stubGlobal("XMLHttpRequest",undefined);vi.stubGlobal("fetch",vi.fn());});
afterEach(()=>vi.unstubAllGlobals());
it("shows a file picker and keeps the item invalid until upload and cart linkage finish",async()=>{
    const id="00000000-0000-4000-8000-000000000001";
    const fetch=vi.mocked(globalThis.fetch);
    fetch.mockResolvedValueOnce({ok:true,json:async()=>({token:"a".repeat(64)})} as Response)
        .mockResolvedValueOnce({ok:true,json:async()=>({id,name:"arte.png",size:3,reference:`NCF-FILE:${id}`})} as Response)
        .mockResolvedValueOnce({ok:true,json:async()=>({ready:true})} as Response);
    const control=buyerUploadControl(1,123,field);document.body.append(control.wrapper);
    expect(validate([field],{[field.propertyName]:control.value.value})).toHaveLength(1);
    const picker=control.wrapper.querySelector<HTMLInputElement>("input[type=file]")!;
    expect(picker.accept).toBe(".png");
    Object.defineProperty(picker,"files",{value:[new File(["abc"],"arte.png",{type:"image/png"})]});
    picker.dispatchEvent(new Event("change"));
    expect(control.value.value).toBe("__uploading__");
    await vi.waitFor(()=>expect(control.value.value).toContain(`NCF-FILE:${id}`));
    expect(validate([field],{[field.propertyName]:control.value.value})).toHaveLength(0);
    expect(control.wrapper.textContent).toContain("arte.png");
    control.wrapper.dispatchEvent(new Event("ncf-upload-consumed"));
    expect(control.value.value).toBe("");
    expect(validate([field],{[field.propertyName]:control.value.value})).toHaveLength(1);
});
it("allows another try after a network error without leaving a valid artwork reference",async()=>{
    vi.mocked(globalThis.fetch).mockRejectedValueOnce(new Error("Falha de rede"));
    const control=buyerUploadControl(2,123,field);document.body.append(control.wrapper);
    const picker=control.wrapper.querySelector<HTMLInputElement>("input[type=file]")!;
    Object.defineProperty(picker,"files",{value:[new File(["abc"],"arte.png")]});picker.dispatchEvent(new Event("change"));
    await vi.waitFor(()=>expect(control.wrapper.textContent).toContain("Falha de rede"));
    expect(control.value.value).toBe("");expect(picker.disabled).toBe(false);
});
