(() => {
    "use strict";
    const ui=document.getElementById("template-ui")?.dataset || {};
    const status=document.getElementById("template-status");
    const show=text=>{if(status)status.textContent=text;};
    async function post(url,data) {
        const response=await fetch(url,{method:"POST",credentials:"same-origin",headers:{"Content-Type":"application/json","X-NCF-Action":"template-write",Accept:"application/json"},body:JSON.stringify(data)});
        const body=await response.json();if(!response.ok)throw new Error(body.error || ui.failed);return body;
    }
    document.querySelectorAll("[data-template-copy]").forEach(button=>button.addEventListener("click",async()=>{
        const name=prompt(ui.copyPrompt,button.dataset.name+(ui.copySuffix || ""));if(!name)return;button.disabled=true;
        try{const copy=await post("/admin/templates/duplicate",{key:button.dataset.key,name});location.assign(`/admin/templates/${copy.id}/edit`);}
        catch(error){show(error.message || ui.failed);button.disabled=false;}
    }));
    document.querySelectorAll("[data-template-delete]").forEach(button=>button.addEventListener("click",async()=>{
        if(!confirm(ui.deleteConfirm))return;button.disabled=true;
        try{await post(`/admin/templates/${button.dataset.id}/delete`,{revision:Number(button.dataset.revision),confirmed:true});location.reload();}
        catch(error){show(error.message || ui.failed);button.disabled=false;}
    }));
    document.querySelectorAll("[data-save-product-template]").forEach(button=>button.addEventListener("click",async()=>{
        const name=prompt(ui.copyPrompt,button.dataset.name || "");if(!name)return;button.disabled=true;
        try{const model=await post(`/admin/products/${button.dataset.productId}/save-template`,{name});location.assign(`/admin/templates/${model.id}/edit`);}
        catch(error){show(error.message || ui.failed);button.disabled=false;}
    }));
    const form=document.getElementById("template-editor");
    if(form) {
        const model=window.ncfTemplateEditor?.template;
        const get=name=>form.querySelector(`[name=${name}]`);
        get("templateName").value=model?.name || "";get("templateDescription").value=model?.description || "";
        const fields=document.getElementById("template-fields");
        function renumber(){[...fields.children].forEach((row,index)=>row.querySelector("[data-template-heading]").textContent=`${ui.field} ${index+1}`);}
        function add(definition={}) {
            const row=document.getElementById("template-field-row").content.firstElementChild.cloneNode(true);
            for(const name of ["label","fieldType","maxLength","placeholder","validationPattern","optionsText","imageOptionsJson","mobileImageColumns"]) {
                const input=row.querySelector(`[name=${name}]`);let value=definition[name];
                if(value!=null) {
                    if(name==="fieldType" && ![...input.options].some(option=>option.value===value))input.append(new Option(ui.unsupported,value));
                    input.value=String(value);
                }
            }
            row.querySelector("[name=required]").checked=Boolean(definition.required);
            row.querySelector("[name=showImageZoom]").checked=definition.showImageZoom!==false;
            row.querySelector("[data-field-remove]").addEventListener("click",()=>{if(Number(row.querySelector(".image-options-editor").dataset.uploadingCount || 0))return;row.remove();renumber();});
            row.querySelector("[data-field-up]").addEventListener("click",()=>{if(row.previousElementSibling)fields.insertBefore(row,row.previousElementSibling);renumber();});
            row.querySelector("[data-field-down]").addEventListener("click",()=>{if(row.nextElementSibling)fields.insertBefore(row.nextElementSibling,row);renumber();});
            fields.append(row);window.ncfInitFieldEditor(row);window.ncfInitOptionImages(row);renumber();
        }
        (model?.fields?.length?model.fields:[{}]).forEach(add);
        document.getElementById("template-add-field").addEventListener("click",()=>add());
        form.addEventListener("submit",async event=>{
            event.preventDefault();
            if([...fields.querySelectorAll(".image-options-editor")].some(editor=>Number(editor.dataset.uploadingCount)>0)){show(ui.imageWait);return;}
            if(!form.reportValidity())return;
            const definitions=[...fields.children].map(row=>{
                const input=name=>row.querySelector(`[name=${name}]`);
                return {label:input("label").value,fieldType:input("fieldType").value,required:input("required").checked,maxLength:Number(input("maxLength").value),
                    placeholder:input("placeholder").value,validationPattern:input("validationPattern").value,optionsText:input("optionsText").value,
                    imageOptionsJson:input("imageOptionsJson").value,showImageZoom:input("showImageZoom").checked,mobileImageColumns:input("mobileImageColumns").value?Number(input("mobileImageColumns").value):null};
            });
            const button=form.querySelector("button[type=submit]");button.disabled=true;
            try{const saved=await post(model?`/admin/templates/${model.id}`:"/admin/templates",{name:get("templateName").value,description:get("templateDescription").value,revision:model?.revision,fields:definitions});location.assign(`/admin/templates/${saved.id}/edit`);}
            catch(error){show(error.message || ui.failed);button.disabled=false;}
        });
    }
    const apply=document.getElementById("template-apply");
    if(apply){
        const confirmation=apply.querySelector("[name=confirmed]");
        apply.addEventListener("change",()=>{const replace=apply.querySelector("[name=mode]:checked")?.value==="REPLACE";document.getElementById("template-replace-confirm").hidden=!replace;confirmation.required=replace;});
        apply.addEventListener("submit",async event=>{
            event.preventDefault();if(!apply.reportValidity())return;
            const button=apply.querySelector("button[type=submit]");button.disabled=true;
            try{const result=await post("/admin/templates/apply",{key:apply.dataset.key,productId:Number(apply.dataset.productId),mode:apply.querySelector("[name=mode]:checked")?.value,
                confirmed:confirmation.checked,requestId:apply.dataset.requestId,reviewToken:apply.dataset.reviewToken});location.assign(result.redirect);}
            catch(error){show(error.message || ui.failed);button.disabled=false;}
        });
    }
})();
