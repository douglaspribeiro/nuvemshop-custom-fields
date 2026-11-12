import { Column, Iframe } from "@tiendanube/nube-sdk-jsx";
import type { NubeComponent, NubeSDK } from "@tiendanube/nube-sdk-types";
import type { PersonalizationField } from "../shared/config";
import { appOrigin } from "../shared/config";
import { createBuyerUploads, uploadValues } from "./buyer-uploads";

const frames=new Map<string,{frame:NubeComponent;changed:(value:string)=>void}>();
export function resetBuyerUploadFields(nube:NubeSDK,store:number,product:number) {
    for(const [key,entry] of frames) if(key.startsWith(`${store}-${product}-`))
        nube.getBrowserAPIs().postMessageToIframe(entry.frame,{type:"ncf-upload-consumed"});
}
/** Files remain in a native, app-hosted picker: binary data never crosses the SDK Worker. */
export function buyerUploadField(nube:NubeSDK,store:number,product:number,field:PersonalizationField,
    _value:string,changed:(value:string)=>void,_refresh:()=>void):NubeComponent {
    const key=`${store}-${product}-${field.id}`;
    let entry=frames.get(key);
    if(!entry) {
        const target={frame:null as unknown as NubeComponent,changed};
        let revision=0;
        const frame=<Iframe id={`ncf-upload-${key}`} src={`${appOrigin()}/buyer-upload.html?store=${store}&product=${product}&field=${field.id}` as `https://${string}`}
            width="100%" height="260px" autoresize sandbox="allow-scripts allow-same-origin"
            onMessage={async event=>{
                const data=event.value;
                if(data?.type==="ncf-upload-ready") {
                    try {
                        const token=await createBuyerUploads(store,product,nube.getBrowserAPIs().asyncSessionStorage).sessionToken();
                        nube.getBrowserAPIs().postMessageToIframe(target.frame,{type:"ncf-upload-session",token});
                    } catch {nube.getBrowserAPIs().postMessageToIframe(target.frame,{type:"ncf-upload-session-error"});}
                    return;
                }
                if(!data || data.type!=="ncf-upload-value" || typeof data.value!=="string") return;
                const value=data.value;const current=++revision;
                if(value && !value.startsWith("__") && (uploadValues(value).length!==value.split("\n").length || uploadValues(value).length>(field.maxFiles ?? 1))) return;
                const storage=nube.getBrowserAPIs().asyncSessionStorage;
                if(typeof data.token==="string" && /^[a-f0-9]{64}$/.test(data.token))
                    await storage.setItem(`ncf-upload-session-${store}`,data.token);
                await storage.setItem(`ncf-upload-${store}-${product}-${field.id}`,value.startsWith("__")?"":value);
                if(current===revision) target.changed(value);
            }} />;
        target.frame=frame;entry=target;frames.set(key,target);
        void createBuyerUploads(store,product,nube.getBrowserAPIs().asyncSessionStorage).restore(field).then(value=>{
            if(!revision && value) {target.changed(value);_refresh();}
        });
    }
    entry.changed=changed;
    return <Column>{entry.frame}</Column>;
}
