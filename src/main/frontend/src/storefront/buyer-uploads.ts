import { appOrigin, type PersonalizationField } from "../shared/config";

export const FILE_REFERENCE = /NCF-FILE:[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/;
type Storage = { getItem(key: string): Promise<string | null> | string | null; setItem(key: string, value: string): unknown };
const fallbackStorage: Storage = {
    getItem: key => { try { return sessionStorage.getItem(key); } catch { return null; } },
    setItem: (key,value) => { try { sessionStorage.setItem(key,value); } catch { /* Session still lives in memory. */ } },
};
const pendingSessions=new Map<number,Promise<string>>();
export function createBuyerUploads(store: number, product: number, storage: Storage = fallbackStorage) {
    let token: Promise<string> | null = null;
    const prefix = `${appOrigin()}/public/stores/${store}/buyer-uploads`;
    async function session(): Promise<string> {
        if (!token) {
            const pending=pendingSessions.get(store);
            if(pending) { token=pending;return pending; }
            token = (async () => {
            const key = `ncf-upload-session-${store}`;
            const previous = await storage.getItem(key);
            if (previous) return previous;
            const response = await fetch(`${prefix}/session`, { method: "POST", credentials: "omit" });
            const body = await result(response);
            const value = body.token as string;
            await storage.setItem(key, value);
            return value;
            })().catch(error => { token = null; throw error; }).finally(()=>pendingSessions.delete(store));
            pendingSessions.set(store,token);
        }
        return token;
    }
    async function result(response: Response) {
        const body = await response.json();
        if (body.code === "upload.session.invalid") { token=null; await storage.setItem(`ncf-upload-session-${store}`, ""); }
        if (!response.ok) throw Object.assign(new Error(body.error || "Não foi possível enviar. Tente novamente."),{code:body.code});
        return body;
    }
    return {
        async ensure(value: string) {
            const id=value.match(/NCF-FILE:(.*)$/)?.[1];
            if(!id) return;
            await result(await fetch(`${prefix}/${id}/cart`,{method:"POST",headers:{"X-Upload-Session":await session()}}));
        },
        async restore(field: PersonalizationField): Promise<string> {
            const value = await storage.getItem(`ncf-upload-${store}-${product}-${field.id}`);
            if (!value || !FILE_REFERENCE.test(value)) return "";
            try {
                const id = value.match(/NCF-FILE:(.*)$/)![1];
                await result(await fetch(`${prefix}/${id}/validate`, { method: "POST", headers: { "X-Upload-Session": await session() } }));
                return value;
            } catch { await storage.setItem(`ncf-upload-${store}-${product}-${field.id}`, "");return ""; }
        },
        async upload(field: PersonalizationField, file: File, progress: (text: string) => void): Promise<string> {
            if (!file.size || file.size > 20_000_000) throw new Error("Envie um arquivo de até 20 MB.");
            const request = crypto.randomUUID();
            const previous = await storage.getItem(`ncf-upload-${store}-${product}-${field.id}`);
            const previousId = previous?.match(/NCF-FILE:(.*)$/)?.[1];
            if(previousId) {
                try {await result(await fetch(`${prefix}/${previousId}`, { method: "DELETE", headers: { "X-Upload-Session": await session() } }));}
                catch(error) {if(!["upload.not.found","upload.expired","upload.not.temporary"].includes((error as {code?:string}).code || ""))throw error;}
                await storage.setItem(`ncf-upload-${store}-${product}-${field.id}`, "");
            }
            const data = new FormData();
            data.append("file", file); data.append("productId", String(product)); data.append("fieldId", String(field.id));
            progress("Enviando e validando arquivo…");
            const headers = { "X-Upload-Session": await session(), "X-Upload-Request": request };
            let uploaded;
            if(typeof XMLHttpRequest !== "undefined") {
                uploaded=await new Promise<Record<string, string | number>>((resolve,reject)=>{
                    const xhr=new XMLHttpRequest();xhr.open("POST",prefix);xhr.timeout=120000;
                    for(const [name,value] of Object.entries(headers))xhr.setRequestHeader(name,value);
                    xhr.upload.onprogress=event=>{if(event.lengthComputable)progress(`Enviando: ${Math.round(event.loaded*100/event.total)}% — aguarde a validação.`);};
                    xhr.onerror=xhr.ontimeout=()=>reject(new Error("Falha de rede. Tente novamente."));
                    xhr.onload=()=>{try{const body=JSON.parse(xhr.responseText);if(body.code==="upload.session.invalid"){token=null;void storage.setItem(`ncf-upload-session-${store}`,"");}if(xhr.status<200||xhr.status>=300)reject(Object.assign(new Error(body.error||"Falha no envio."),{code:body.code}));else resolve(body);}catch{reject(new Error("Resposta incompleta. Tente novamente."));}};
                    xhr.send(data);
                });
            } else uploaded = await result(await fetch(prefix, { method: "POST", headers, body: data, credentials: "omit" }));
            await result(await fetch(`${prefix}/${uploaded.id}/validate`, { method: "POST", headers: { "X-Upload-Session": await session() } }));
            const value = `${uploaded.name} (${uploaded.size} bytes) — ${uploaded.reference}`;
            await storage.setItem(`ncf-upload-${store}-${product}-${field.id}`, value);
            progress(`Enviado: ${uploaded.name} (${uploaded.size} bytes). Disponível à loja por 90 dias após o pedido.`);
            return value;
        },
        async remove(field: PersonalizationField, value: string) {
            const id = value.match(/NCF-FILE:(.*)$/)?.[1];
            if (id) await result(await fetch(`${prefix}/${id}`, { method: "DELETE", headers: { "X-Upload-Session": await session() } }));
            await storage.setItem(`ncf-upload-${store}-${product}-${field.id}`, "");
        },
        async forget(field: PersonalizationField) { await storage.setItem(`ncf-upload-${store}-${product}-${field.id}`, ""); },
    };
}
