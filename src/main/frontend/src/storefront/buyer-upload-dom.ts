import type { PersonalizationField } from "../shared/config";
import { createBuyerUploads } from "./buyer-uploads";

export function buyerUploadControl(store: number, product: number, field: PersonalizationField, restore = true) {
    const wrapper=document.createElement("div");wrapper.className="ncf-field";
    const title=document.createElement("label");title.textContent=field.label+(field.required?" *":"");
    const picker=document.createElement("input");picker.type="file";
    picker.accept=(field.options.length?field.options:["JPEG","PNG","PDF"]).map(f=>f==="JPEG"?".jpg,.jpeg":`.${f.toLowerCase()}`).join(",");
    title.append(picker);
    const value=document.createElement("input");value.type="hidden";
    value.name=`properties[${field.propertyName || field.label}]`;value.required=field.required;
    value.setAttribute("aria-label",field.label);value.style.cssText="width:100%;overflow:hidden;text-overflow:ellipsis";
    const display=document.createElement("input");display.type="hidden";display.name=`properties[${field.label.replace(/\[/g,"(").replace(/\]/g,")").trim()}]`;
    const status=document.createElement("p");status.setAttribute("role","status");status.setAttribute("aria-live","polite");
    status.textContent=field.placeholder || "JPG, PNG ou PDF, até 20 MB. Disponível à loja por 90 dias após o pedido.";
    const remove=document.createElement("button");remove.type="button";remove.textContent="Remover arquivo";
    const client=createBuyerUploads(store,product);
    let generation=0,busy=false;
    function set(text:string) {value.value=text;display.value=text.startsWith("__")?"":text.replace(/\s*—\s*NCF-FILE:.*$/, "");value.dispatchEvent(new Event("change",{bubbles:true}));}
    if (restore) void client.restore(field).then(restored=>{if(!busy && !generation) {set(restored);if(restored)status.textContent=display.value;}});
    picker.addEventListener("change",async()=>{
        const file=picker.files?.[0];if(!file || busy) return;
        busy=true;const request=++generation;set("__uploading__");value.setCustomValidity("Aguarde o envio e a validação do arquivo.");remove.disabled=true;picker.disabled=true;
        try {
            const uploaded=await client.upload(field,file,text=>{if(request===generation)status.textContent=text;});
            if(request!==generation){void client.forget(field);return;}
            set(uploaded);value.setCustomValidity("");
        }
        catch(error){if(request!==generation)return;set("");status.textContent=error instanceof Error?error.message:"Falha de rede. Tente novamente.";value.setCustomValidity(field.required?status.textContent:"");}
        finally{busy=false;remove.disabled=false;picker.disabled=false;picker.value="";}
    });
    remove.addEventListener("click",async()=>{
        if(busy)return;generation++;busy=true;remove.disabled=true;picker.disabled=true;
        try{await client.remove(field,value.value);set("");value.setCustomValidity("");status.textContent="Arquivo removido.";}
        catch{status.textContent="Não foi possível remover. Tente novamente.";}
        finally{busy=false;remove.disabled=false;picker.disabled=false;}
    });
    wrapper.append(title,value,display,status,remove);
    Object.assign(wrapper,{ncfValidateUpload:()=>client.ensure(value.value)});
    wrapper.addEventListener("ncf-upload-consumed",()=>{
        generation++;set("");void client.forget(field);status.textContent="Escolha um arquivo para personalizar outro item.";
    });
    return {wrapper,value};
}
