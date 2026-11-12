import { appOrigin, type PersonalizationField } from "../shared/config";

export const FILE_REFERENCE = /NCF-FILE:[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/;
export function uploadValues(value: string): string[] { return value.split("\n").filter(part => FILE_REFERENCE.test(part)); }
export function uploadReferences(value:string):string { return uploadValues(value).map(part=>`NCF-FILE:${uploadId(part)}`).join("\n"); }
export function uploadId(value: string): string | undefined { return value.match(/NCF-FILE:([a-f0-9-]{36})$/)?.[1]; }
export function uploadDisplay(value: string): string { return uploadValues(value).map(part => part.replace(/\s*—\s*NCF-FILE:.*$/, "").replace(/\s*—\s*https?:\/\/\S+\/admin\/buyer-uploads\/[a-f0-9-]{36}\/download$/, "").replace(/\s+\(\d+ bytes\)$/, "")).join("; "); }
type Storage = { getItem(key: string): Promise<string | null> | string | null; setItem(key: string, value: string): unknown };
const fallbackMemory=new Map<string,string>();
const fallbackStorage: Storage = {
    getItem: key => { try { return sessionStorage.getItem(key); } catch { return fallbackMemory.get(key) || null; } },
    setItem: (key,value) => { fallbackMemory.set(key,value);try { sessionStorage.setItem(key,value); } catch { /* Session still lives in memory. */ } },
};
export function setUploadSession(store:number,token:string) { fallbackStorage.setItem(`ncf-upload-session-${store}`,token); }
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
        sessionToken: session,
        async ensure(value: string) {
            for (const part of uploadValues(value)) {
                await result(await fetch(`${prefix}/${uploadId(part)}/cart`,{method:"POST",headers:{"X-Upload-Session":await session()}}));
            }
        },
        async restore(field: PersonalizationField): Promise<string> {
            const value = await storage.getItem(`ncf-upload-${store}-${product}-${field.id}`);
            if (!value || !uploadValues(value).length) return "";
            try {
                for (const part of uploadValues(value)) await result(await fetch(`${prefix}/${uploadId(part)}/validate`, { method: "POST", headers: { "X-Upload-Session": await session() } }));
                return value;
            } catch { await storage.setItem(`ncf-upload-${store}-${product}-${field.id}`, "");return ""; }
        },
        async upload(field: PersonalizationField, file: File, progress: (text: string) => void, append = false): Promise<string> {
            const maxBytes = field.maxFileBytes ?? 20_000_000;
            if (!file.size || file.size > maxBytes) throw new Error(field.locale?.startsWith("es") ? `Envía un archivo de hasta ${maxBytes / 1_000_000} MB.` : `Envie um arquivo de até ${maxBytes / 1_000_000} MB.`);
            const request = crypto.randomUUID();
            const previous = await storage.getItem(`ncf-upload-${store}-${product}-${field.id}`);
            const previousId = previous ? uploadId(previous) : undefined;
            if (append && uploadValues(previous || "").length >= (field.maxFiles ?? 1)) throw new Error(`Envie no máximo ${field.maxFiles ?? 1} arquivos.`);
            if(previousId && !append) {
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
            const single = `${uploaded.name} (${uploaded.size} bytes) — ${appOrigin()}/admin/buyer-uploads/${uploaded.id}/download — ${uploaded.reference}`;
            const value = append && previous ? previous + "\n" + single : single;
            await storage.setItem(`ncf-upload-${store}-${product}-${field.id}`, value);
            progress(`Enviado: ${uploaded.name} (${uploaded.size} bytes).`);
            return value;
        },
        async remove(field: PersonalizationField, value: string) {
            for (const part of uploadValues(value)) await result(await fetch(`${prefix}/${uploadId(part)}`, { method: "DELETE", headers: { "X-Upload-Session": await session() } }));
            const previous = await storage.getItem(`ncf-upload-${store}-${product}-${field.id}`) || "";
            const remaining = uploadValues(previous).filter(part => !uploadValues(value).some(removed => uploadId(removed) === uploadId(part))).join("\n");
            await storage.setItem(`ncf-upload-${store}-${product}-${field.id}`, remaining);
            return remaining;
        },
        async forget(field: PersonalizationField) { await storage.setItem(`ncf-upload-${store}-${product}-${field.id}`, ""); },
    };
}
