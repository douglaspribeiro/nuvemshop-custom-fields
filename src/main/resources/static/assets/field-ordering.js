(() => {
    // Only the handle drags: typing, native color picking and text selection remain usable.
    function orderable(container, changed) {
        container.dataset.orderContainer='true';
        let dragged=null;
        function move(row,target) {
            if(!row || !target || row===target || target.parentElement!==container)return;
            const rows=[...container.children];
            container.insertBefore(row,rows.indexOf(row)<rows.indexOf(target)?target.nextSibling:target);
            changed();
        }
        container.addEventListener('dragstart',event=>{
            const handle=event.target.closest('[data-order-handle]');
            if(!handle || handle.closest('[data-order-container]')!==container)return;
            event.stopPropagation();
            dragged=[...container.children].find(row=>row.contains(handle));
            event.dataTransfer?.setData('text/plain','reorder');
        });
        container.addEventListener('dragover',event=>{if(dragged)event.preventDefault();});
        container.addEventListener('drop',event=>{
            if(!dragged)return;event.preventDefault();event.stopPropagation();move(dragged,[...container.children].find(row=>row.contains(event.target)));dragged=null;
        });
        container.addEventListener('dragend',()=>{dragged=null;});
        // Pointer events provide the same operation on touchscreens.
        let touchRow=null;
        container.addEventListener('pointerdown',event=>{
            const handle=event.target.closest('[data-order-handle]');if(!handle || handle.closest('[data-order-container]')!==container || event.pointerType==='mouse')return;
            event.stopPropagation();
            touchRow=[...container.children].find(row=>row.contains(handle));handle.setPointerCapture?.(event.pointerId);
        });
        container.addEventListener('pointerup',event=>{
            if(!touchRow)return;event.stopPropagation();
            const target=document.elementFromPoint(event.clientX,event.clientY);
            move(touchRow,[...container.children].find(row=>target && row.contains(target)));touchRow=null;
        });
        container.addEventListener('pointercancel',()=>{touchRow=null;});
        // Delegated so template rows added later work too.
        container.addEventListener('click',event=>{
            if(event.target.closest('[data-order-handle]')){event.preventDefault();return;}
            const button=event.target.closest('[data-order-up],[data-order-down]');if(!button)return;
            event.preventDefault();
            const row=[...container.children].find(row=>row.contains(button));
            move(row,button.hasAttribute('data-order-up')?row.previousElementSibling:row.nextElementSibling);
            button.focus();
        });
        const observer=new MutationObserver(()=>container.querySelectorAll('[data-order-handle]').forEach(h=>h.draggable=true));
        observer.observe(container,{childList:true,subtree:true});
        container.querySelectorAll('[data-order-handle]').forEach(h=>h.draggable=true);
    }
    window.ncfOrderable=orderable;
    const list=document.querySelector('.field-list[data-order-url]');
    if(list){
        const save=document.getElementById('save-field-order'),status=document.getElementById('field-order-status');
        let generation=0,busy=false;
        orderable(list,()=>{generation++;status.textContent='';save.disabled=busy;});
        save.addEventListener('click',async()=>{
            if(busy)return;
            const rows=[...list.children],ids=rows.map(row=>Number(row.id.replace('configured-field-',''))),at=generation;
            busy=true;save.disabled=true;
            try{
                const response=await fetch(list.dataset.orderUrl,{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-NCF-Action':'field-order'},body:JSON.stringify(ids)});
                if(!response.ok)throw new Error();
                rows.forEach((row,index)=>{
                    row.querySelector('[name=sortOrder]').value=String(index);row.querySelector('.field-position').value=String(index+1);
                });
                status.textContent=generation===at?list.dataset.saved:'';
            }catch{status.textContent=list.dataset.failed;}
            finally{busy=false;save.disabled=false;}
        });
    }
})();
