import type { PersonalizationField } from "../shared/config";
import { uploadDisplay, uploadValues, createBuyerUploads } from "./buyer-uploads";

export function buyerUploadControl(store: number, product: number, field: PersonalizationField) {
    const es=field.locale?.startsWith("es") === true;
    const t=(pt:string,translated:string)=>es?translated:pt;
    const wrapper=document.createElement("div");wrapper.className="ncf-field";
    const title=document.createElement("label");title.textContent=field.label+(field.required?" *":"");
    const picker=document.createElement("input");picker.type="file";picker.multiple=(field.maxFiles ?? 1)>1;
    picker.accept=(field.options.length?field.options:["JPEG","PNG","PDF"]).map(f=>f==="JPEG"?".jpg,.jpeg":`.${f.toLowerCase()}`).join(",");
    title.append(picker);
    const value=document.createElement("input");value.type="hidden";
    value.name=`properties[${field.propertyName || field.label}]`;value.required=field.required;
    value.setAttribute("aria-label",field.label);value.style.cssText="width:100%;overflow:hidden;text-overflow:ellipsis";
    const display=document.createElement("input");display.type="hidden";display.name=`properties[${field.label.replace(/\[/g,"(").replace(/\]/g,")").trim()}]`;
    const help=document.createElement("p");
    const status=document.createElement("p");status.setAttribute("role","status");status.setAttribute("aria-live","polite");
    help.className="ncf-upload-help";
    help.textContent=t(`JPG, PNG ou PDF · até ${(field.maxFileBytes ?? 20_000_000)/1_000_000} MB por arquivo${(field.maxFiles ?? 1)>1 ? ` · até ${field.maxFiles} arquivos` : ""}`,`JPG, PNG o PDF · hasta ${(field.maxFileBytes ?? 20_000_000)/1_000_000} MB por archivo${(field.maxFiles ?? 1)>1 ? ` · hasta ${field.maxFiles} archivos` : ""}`);
    const remove=document.createElement("button");remove.type="button";remove.textContent=(field.maxFiles ?? 1)>1 ? t("Remover todos os arquivos","Eliminar todos los archivos") : t("Remover arquivo","Eliminar archivo");
    const list=document.createElement("ul");
    const client=createBuyerUploads(store,product);
    let generation=0,busy=false;
    function set(text:string) {
        list.replaceChildren();
        for(const part of uploadValues(text)) {
            const item=document.createElement("li"),name=document.createElement("span"),button=document.createElement("button");
            name.textContent=part.split(" — ")[0];button.type="button";button.textContent=t("Remover","Eliminar");
            button.addEventListener("click",async()=>{
                if(busy)return;busy=true;picker.disabled=true;button.disabled=true;
                try {set(await client.remove(field,part));status.textContent=t("Arquivo removido.","Archivo eliminado.");}
                catch {status.textContent=t("Não foi possível remover. Tente novamente.","No se pudo eliminar. Inténtalo de nuevo.");}
                finally {busy=false;picker.disabled=false;button.disabled=false;}
            });
            item.append(name,button);list.append(item);
        }
        remove.hidden=uploadValues(text).length<2;
        value.value=text;display.value=text.startsWith("__")?"":uploadDisplay(text);value.dispatchEvent(new Event("change",{bubbles:true}));}
    void client.restore(field).then(restored=>{if(!busy && !generation) {set(restored);if(restored)status.textContent=display.value;}});
    picker.addEventListener("change",async()=>{
        const files=Array.from(picker.files || []);if(!files.length || busy) return;
        if(((field.maxFiles ?? 1)>1 ? files.length+uploadValues(value.value).length : files.length)>(field.maxFiles ?? 1)) {status.textContent=t(`Envie no máximo ${field.maxFiles ?? 1} arquivos.`,`Envía como máximo ${field.maxFiles ?? 1} archivos.`);picker.value="";return;}
        let completed=value.value;
        busy=true;generation++;set("__uploading__");value.setCustomValidity("Aguarde o envio e a validação do arquivo.");remove.disabled=true;picker.disabled=true;
        try {for(const file of files) {completed=await client.upload(field,file,text=>{status.textContent=text;},(field.maxFiles ?? 1)>1);}set(completed);value.setCustomValidity("");}
        catch(error){set(completed);status.textContent=error instanceof Error?error.message:"Falha de rede. Tente novamente.";value.setCustomValidity(field.required?status.textContent:"");}
        finally{busy=false;remove.disabled=false;picker.disabled=false;picker.value="";}
    });
    remove.addEventListener("click",async()=>{
        if(busy)return;generation++;busy=true;remove.disabled=true;picker.disabled=true;
        try{await client.remove(field,value.value);set("");value.setCustomValidity("");status.textContent=t("Arquivo removido.","Archivo eliminado.");}
        catch{status.textContent=t("Não foi possível remover. Tente novamente.","No se pudo eliminar. Inténtalo de nuevo.");}
        finally{busy=false;remove.disabled=false;picker.disabled=false;}
    });
    wrapper.append(title,value,display,help,status,list,remove);
    Object.assign(wrapper,{ncfValidateUpload:()=>client.ensure(value.value)});
    wrapper.addEventListener("ncf-upload-consumed",()=>{
        generation++;set("");void client.forget(field);status.textContent="Escolha um arquivo para personalizar outro item.";
    });
    return {wrapper,value};
}
