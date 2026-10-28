(function(){
    "use strict";
    async function load(){
        const status=document.getElementById("buyer-usage");
        try{
            const response=await fetch("/admin/buyer-uploads/data",{headers:{Accept:"application/json"}});
            if(!response.ok)throw new Error();
            const data=await response.json(),usage=data.usage;
            status.textContent=`Ocupado: ${(usage.occupiedBytes/1e9).toFixed(3)} GB. Espaço livre: ${Math.max(0,(usage.quotaBytes-usage.occupiedBytes)/1e9).toFixed(3)} GB. Franquia para novos envios: ${(usage.quotaBytes/1e9).toFixed(0)} GB. ${usage.acceptsUploads&&data.enabled?"Envios habilitados.":"Novos envios indisponíveis. Os arquivos dos pedidos permanecem disponíveis até o vencimento."}`;
            const warning=document.getElementById("buyer-warning");warning.hidden=usage.warningPercent<80;
            warning.textContent=usage.warningPercent>=100?"Armazenamento cheio. Baixe e exclua arquivos para liberar espaço ou altere o plano.":`Ocupação acima de ${usage.warningPercent}%. Organize os arquivos ou altere o plano.`;
            const body=document.getElementById("buyer-files");body.replaceChildren();document.getElementById("buyer-empty").hidden=data.files.length>0;
            for(const file of data.files){
                const row=document.createElement("tr");
                for(const text of [`${file.order_id} / ${file.order_item_id}`,file.original_name||"Arquivo removido",`${file.size_bytes} bytes`,new Date(file.expires_at).toLocaleString(),({ORDERED:"Disponível",DELETING:"Em exclusão",DELETED:"Excluído",EXPIRED:"Expirado"}[file.state]||"Indisponível")]){
                    const cell=document.createElement("td");cell.textContent=text;row.append(cell);
                }
                const actions=document.createElement("td");
                if(file.state==="ORDERED"&&new Date(file.expires_at)>new Date()){
                    const download=document.createElement("a");download.href=`/admin/buyer-uploads/${encodeURIComponent(file.id)}/download`;download.textContent="Baixar";
                    const remove=document.createElement("button");remove.type="button";remove.textContent="Excluir";
                    remove.addEventListener("click",async()=>{
                        if(!confirm("Excluir este arquivo definitivamente? A arte não poderá ser recuperada. Baixe uma cópia antes de continuar."))return;
                        remove.disabled=true;
                        try{const result=await fetch(`/admin/buyer-uploads/${encodeURIComponent(file.id)}/delete`,{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded","X-NCF-Action":"delete"},body:"confirmed=true"});if(!result.ok)throw new Error();await load();}
                        catch{status.textContent="Não foi possível excluir. Tente novamente.";remove.disabled=false;}
                    });actions.append(download,document.createTextNode(" "),remove);
                }
                row.append(actions);body.append(row);
            }
        }catch{status.textContent="Não foi possível carregar os arquivos. Atualize a página para tentar novamente.";}
    }
    void load();
})();
