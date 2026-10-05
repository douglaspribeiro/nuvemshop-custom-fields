(() => {
    document.querySelectorAll(".image-options-form").forEach(form => {
        const editor = form.querySelector(".image-options-editor");
        const hidden = editor.querySelector("[name=imageOptionsJson]");
        const rows = editor.querySelector(".image-options-rows");
        const status = editor.querySelector(".image-options-status");
        const add = editor.querySelector(".image-option-add");
        const type = form.querySelector("[name=fieldType]");
        let options;
        try { options = JSON.parse(hidden.value || "[]"); } catch (_) { options = []; }
        if (!Array.isArray(options)) options = [];
        options = options.filter(option => option && typeof option.label === "string" && typeof option.id === "string");
        const maxOptions = Number(editor.dataset.maxOptions || 25);
        let uploads = 0;
        function sync() {
            hidden.value = JSON.stringify(options.map(({id, label}) => ({id, label})));
            hidden.dispatchEvent(new Event("change", {bubbles:true}));
        }
        function lock() {
            form.querySelectorAll("button[type=submit]").forEach(button => button.disabled = uploads > 0);
            add.disabled = uploads > 0 || options.length >= maxOptions || editor.dataset.enabled !== "true";
        }
        function render() {
            rows.replaceChildren();
            options.forEach((option, index) => {
                const row = document.createElement("div"); row.className = "image-option-row";
                const nameLabel = document.createElement("label");
                const title = document.createElement("span"); title.textContent = editor.dataset.label;
                const input = document.createElement("input"); input.type = "text"; input.maxLength = 100;
                input.value = option.label || "";
                input.addEventListener("input", () => {option.label=input.value;sync();});
                nameLabel.append(title,input);
                const image = document.createElement("img"); image.alt = option.label || "";
                image.width=96; image.height=96;
                if(option.id) image.src="/admin/images/" + encodeURIComponent(option.id) + "?size=thumbnail";
                else image.hidden=true;
                const fileLabel=document.createElement("label");
                const fileTitle=document.createElement("span");fileTitle.textContent=editor.dataset.file;
                const file=document.createElement("input");file.type="file";file.accept="image/jpeg,image/png";
                file.disabled=editor.dataset.enabled!=="true";
                file.addEventListener("change", async () => {
                    if(!file.files[0]) return;
                    uploads++; lock(); status.textContent=editor.dataset.uploading;
                    const body=new FormData();body.append("file",file.files[0]);
                    try {
                        const response=await fetch(editor.dataset.uploadUrl,{method:"POST",body,credentials:"same-origin",headers:{Accept:"application/json"}});
                        const data=await response.json().catch(()=>{throw new Error(editor.dataset.failed);});
                        if(!response.ok || !data.id) {const failure=new Error(data.error || editor.dataset.failed);failure.localized=true;throw failure;}
                        // Replace only after a successful upload. Previous asset is retained until form save.
                        option.id=data.id; image.src="/admin/images/" + encodeURIComponent(data.id) + "?size=thumbnail"; image.hidden=false;
                        sync();status.textContent="";
                    } catch(error) {status.textContent=error.localized ? error.message : editor.dataset.failed;}
                    finally {uploads--;lock();file.value="";}
                });
                fileLabel.append(fileTitle,file);
                const actions=document.createElement("div");actions.className="row-actions";
                function button(label,action,disabled=false) {
                    const b=document.createElement("button");b.type="button";b.className="button small";b.textContent=label;b.disabled=disabled;
                    b.addEventListener("click",() => {if(uploads) return;action();sync();render();});actions.append(b);
                }
                button(editor.dataset.up,()=>{[options[index-1],options[index]]=[options[index],options[index-1]];},index===0);
                button(editor.dataset.down,()=>{[options[index+1],options[index]]=[options[index],options[index+1]];},index===options.length-1);
                button(editor.dataset.remove,()=>options.splice(index,1));
                row.append(image,nameLabel,fileLabel,actions);rows.append(row);
            });lock();
        }
        function visibility() {
            editor.hidden=type.value!=="IMAGE_SELECT";
            ["optionsText","maxLength","placeholder","validationPattern"].forEach(name=>{
                const input=form.querySelector("[name="+name+"]");if(input) input.closest("label").hidden=type.value==="IMAGE_SELECT";
            });
        }
        add.addEventListener("click",()=>{if(editor.dataset.enabled !== "true") return; if(options.length>=maxOptions){status.textContent=editor.dataset.limit;return;}options.push({id:"",label:""});sync();render();});
        type.addEventListener("change",visibility);
        form.addEventListener("submit",event=>{
            if(type.value!=="IMAGE_SELECT") return;
            if (options.length > maxOptions) { event.preventDefault(); status.textContent=editor.dataset.limit; return; }
            const labels=options.map(o=>(o.label||"").trim().toLocaleLowerCase());
            if(uploads || !options.length || options.some(o=>!o.id || !(o.label||"").trim()) || new Set(labels).size!==labels.length){
                event.preventDefault();status.textContent=editor.dataset.incomplete;
            } else sync();
        });
        render();visibility();
    });
})();
