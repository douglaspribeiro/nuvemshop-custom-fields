import { appOrigin, type PersonalizationConfig } from "../shared/config";
import { buyerUploadControl } from "./buyer-upload-dom";
import { setUploadSession, createBuyerUploads } from "./buyer-uploads";

async function boot() {
    const query=new URLSearchParams(location.search);
    const store=Number(query.get("store")),product=Number(query.get("product")),id=Number(query.get("field"));
    if(![store,product,id].every(value=>Number.isSafeInteger(value)&&value>0)) return;
    const root=document.getElementById("upload-root")!;
    try {
        // One SDK-owned session is shared by all fields, even across separate iframes.
        await new Promise<void>((resolve,reject)=>{
            const timer=setTimeout(()=>{window.removeEventListener("message",receive);reject(new Error("session_timeout"));},30000);
            function receive(event:MessageEvent) {
                if(event.source!==parent) return;
                if(event.data?.type==="ncf-upload-session-error") {clearTimeout(timer);window.removeEventListener("message",receive);reject(new Error("session_unavailable"));}
                if(event.data?.type!=="ncf-upload-session" || !/^[a-f0-9]{64}$/.test(event.data.token || "")) return;
                setUploadSession(store,event.data.token);clearTimeout(timer);window.removeEventListener("message",receive);resolve();
            }
            window.addEventListener("message",receive);
            parent.postMessage({type:"ncf-upload-ready"},"*");
        });
        const response=await fetch(`${appOrigin()}/public/stores/${store}/personalization?productId=${product}`);
        if(!response.ok) throw new Error();
        const config:PersonalizationConfig=await response.json();
        const field=config.enabled && config.fields.find(field=>field.id===id && field.fieldType==="FILE_UPLOAD");
        if(!field) throw new Error();
        document.documentElement.lang=config.locale || "pt-BR";
        if(config.style?.productTextColor && /^#[0-9a-f]{3,8}$/i.test(config.style.productTextColor)) document.body.style.color=config.style.productTextColor;
        const control=buyerUploadControl(store,product,{...field,locale:config.locale});
        const client=createBuyerUploads(store,product);
        let sequence=0;
        control.value.addEventListener("change",async()=>{
            const current=++sequence,value=(control.wrapper as HTMLDivElement & {ncfUploadValue:string}).ncfUploadValue;
            const token=value && !value.startsWith("__") ? await client.sessionToken() : "";
            if(current!==sequence) return;
            parent.postMessage({type:"ncf-upload-value",value,token},"*");
        });
        window.addEventListener("message",event=>{
            if(event.source===parent && event.data?.type==="ncf-upload-consumed") control.wrapper.dispatchEvent(new Event("ncf-upload-consumed"));
        });
        root.append(control.wrapper);
        new ResizeObserver(()=>parent.postMessage({type:"resize",height:Math.ceil(root.getBoundingClientRect().height)},"*")).observe(root);
    } catch {root.textContent="Não foi possível carregar o envio de arquivos. Atualize a página para tentar novamente.";}
}
void boot();
