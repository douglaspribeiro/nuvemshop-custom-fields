import { Button, Column, Form, Text } from "@tiendanube/nube-sdk-jsx";
import type { NubeComponent, NubeSDK } from "@tiendanube/nube-sdk-types";
import type { PersonalizationField } from "../shared/config";
import { appOrigin } from "../shared/config";
import { createBuyerUploads } from "./buyer-uploads";

const state = new Map<string, { busy: boolean; message: string; form: NubeComponent; value: string }>();
export function resetBuyerUploadFields(nube:NubeSDK,store:number,product:number) {
    for(const [key,entry] of state) if(key.startsWith(`${store}-${product}-`)) {
        nube.getBrowserAPIs().resetForm(entry.form);entry.value="";entry.message="";entry.busy=false;state.delete(key);
    }
}
export function buyerUploadField(nube: NubeSDK, store: number, product: number, field: PersonalizationField,
    value: string, changed: (value: string) => void, refresh: () => void, restore = true): NubeComponent {
    const client = createBuyerUploads(store,product,nube.getBrowserAPIs().asyncSessionStorage);
    const key = `${store}-${product}-${field.id}`;
    let current = state.get(key);
    if (!current) {
        const next = { busy: false, message: "", form: null as unknown as NubeComponent, value: "" };
        const form = <Form.Root target={`${appOrigin()}/public/stores/${store}/buyer-uploads`} onChange={async () => {
            if (next.busy) return;
            const browser = nube.getBrowserAPIs() as ReturnType<NubeSDK["getBrowserAPIs"]> & {
                getFormFile?: (form: NubeComponent, name: string) => Promise<File | null>;
            };
            if (!browser.getFormFile) { next.message = "O envio está indisponível neste navegador. Entre em contato com a loja."; refresh(); return; }
            const file = await browser.getFormFile(form,"file");
            if (!file) return;
            next.busy = true; next.value="__uploading__"; changed(next.value);
            try {
                const uploaded = await client.upload(field,file,text => { next.message=text;refresh(); });
                next.value=uploaded;changed(uploaded);
            } catch (error) { next.message=error instanceof Error ? error.message : "Falha de rede. Tente novamente.";next.value="";changed(""); }
            finally { next.busy=false;refresh(); }
        }}>
            <Form.Field type="file" name="file" label={field.label + (field.required ? " *" : "")}
                accept={(field.options.length ? field.options : ["JPEG","PNG","PDF"]).map(format => format==="JPEG" ? ".jpg,.jpeg" : `.${format.toLowerCase()}`).join(",")}
                maxSize={20_000_000} />
        </Form.Root>;
        next.form=form;current=next;state.set(key,next);
        if (restore) void client.restore(field).then(restored => { if(restored && !next.busy) { next.value=restored;changed(restored); refresh(); } });
    }
    const entry=current;
    if(!value && entry.value) changed(entry.value);
    return <Column gap={4}>
        {entry.form}
        <Text>{field.placeholder || "JPG, PNG ou PDF, até 20 MB."}</Text>
        <Text>{entry.message || (value.startsWith("__") ? "Enviando…" : value.replace(/\s*—\s*NCF-FILE:.*$/, ""))}</Text>
        <Button variant="link" onClick={async () => {
            if(entry.busy) return;
            entry.busy=true;
            try { await client.remove(field,value || entry.value);entry.value=""; changed(""); entry.message="Arquivo removido."; }
            catch {entry.message="Não foi possível remover. Tente novamente.";}
            finally {entry.busy=false;refresh();}
        }}>Remover arquivo</Button>
    </Column>;
}
