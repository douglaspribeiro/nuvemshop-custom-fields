(() => {
    const box=document.getElementById('merchant-rate-box');if(!box)return;
    const form=box.querySelector('form'),send=document.getElementById('rate-box-submit'),feedback=document.getElementById('rate-box-feedback');
    const error=document.getElementById('rate-box-error'),thanks=document.getElementById('rate-box-thanks');
    let id=null,busy=false,started=false;
    async function post(path,value){
        const response=await fetch('/admin/rate-box/'+path,{method:'POST',credentials:'same-origin',keepalive:true,headers:{'Content-Type':'application/json','X-NCF-Action':'rate-box'},body:JSON.stringify(value || {})});
        if(response.status===204)return null;
        const data=await response.json();if(!response.ok)throw new Error(data.error || box.dataset.error);return data;
    }
    async function display(){
        if(started || document.visibilityState==='hidden')return;started=true;
        try{const data=await post('display');if(data?.id){id=data.id;box.hidden=false;}}
        catch{/* An optional opinion box must never interrupt merchant work. */}
    }
    document.addEventListener('visibilitychange',display);display();
    function lock(value){busy=value;form.querySelectorAll('button[type=submit],input,textarea').forEach(control=>control.disabled=value);}
    async function submit(){
        if(busy || !id || !form.reportValidity())return;
        const score=Number(form.querySelector('[name=rating]:checked')?.value);if(!score)return;
        lock(true);error.textContent='';
        try{
            const data=await post(id+'/rate',{rating:score,comment:score<5?document.getElementById('rate-box-comment').value:null});
            form.hidden=true;thanks.hidden=false;
            if(data.rating===5 && data.reviewUrl){
                const link=document.getElementById('rate-box-public-link');link.href=data.reviewUrl;document.getElementById('rate-box-public').hidden=false;
            }
            thanks.focus();
        }catch(failure){error.textContent=failure.message || box.dataset.error;send.hidden=false;}
        finally{lock(false);}
    }
    form.addEventListener('change',event=>{
        if(event.target.name!=='rating' || busy)return;
        const score=Number(event.target.value);feedback.hidden=score===5;send.hidden=false;
        form.querySelectorAll('.rate-box-stars label').forEach(label=>label.classList.toggle('is-selected',Number(label.querySelector('input').value)<=score));
        document.getElementById('rate-box-selection').textContent=box.dataset.selected.replace('{0}',String(score));
    });
    form.addEventListener('submit',event=>{event.preventDefault();submit();});
    document.getElementById('rate-box-public-link').addEventListener('click',()=>{
        if(id && !thanks.hidden && !document.getElementById('rate-box-public').hidden)
            post(id+'/public-review').catch(()=>{/* Keep the public link usable if recording fails. */});
    });
    box.querySelectorAll('[data-rate-dismiss]').forEach(button=>button.addEventListener('click',()=>{
        if(!id)return;box.hidden=true;
        post(id+'/dismiss').catch(()=>{/* Display cooldown is already persisted even if closing fails offline. */});
    }));
})();
