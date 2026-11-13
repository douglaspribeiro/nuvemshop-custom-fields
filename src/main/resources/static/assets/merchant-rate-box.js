(() => {
    const box=document.getElementById('merchant-rate-box');if(!box)return;
    const form=box.querySelector('form'),send=document.getElementById('rate-box-submit'),feedback=document.getElementById('rate-box-feedback');
    const error=document.getElementById('rate-box-error'),thanks=document.getElementById('rate-box-thanks');
    let id=null,busy=false,started=false,rated=false;
    function track(name,details={}){
        try{if(typeof window.gtag==='function')window.gtag('event',name,{app_area:'merchant_admin',store_id:box.dataset.storeId,plan:box.dataset.plan,...details});}
        catch{/* Analytics must never interrupt the rating flow. */}
    }
    async function post(path,value){
        const response=await fetch('/admin/rate-box/'+path,{method:'POST',credentials:'same-origin',keepalive:true,headers:{'Content-Type':'application/json','X-NCF-Action':'rate-box'},body:JSON.stringify(value || {})});
        if(response.status===204)return null;
        const data=await response.json();if(!response.ok)throw new Error(data.error || box.dataset.error);return data;
    }
    async function display(){
        if(started || document.visibilityState==='hidden')return;started=true;
        try{const data=await post('display');if(data?.id){id=data.id;box.hidden=false;track('rate_box_view');}}
        catch{track('rate_box_error',{operation:'display'});}
    }
    document.addEventListener('visibilitychange',display);display();
    function lock(value){busy=value;form.querySelectorAll('button[type=submit],input,textarea').forEach(control=>control.disabled=value);}
    async function submit(){
        if(busy || rated || !id || !form.reportValidity())return;
        const score=Number(form.querySelector('[name=rating]:checked')?.value);if(!score)return;
        const comment=score<5?document.getElementById('rate-box-comment').value:null;
        lock(true);error.textContent='';
        try{
            const data=await post(id+'/rate',{rating:score,comment});
            rated=true;track('rate_box_submitted',{rating:data.rating,has_comment:Boolean(comment?.trim())});
            form.hidden=true;thanks.hidden=false;
            if(data.rating===5 && data.reviewUrl){
                const link=document.getElementById('rate-box-public-link');link.href=data.reviewUrl;document.getElementById('rate-box-public').hidden=false;
            }
            thanks.focus();
        }catch(failure){track('rate_box_error',{operation:'submit'});error.textContent=failure.message || box.dataset.error;send.hidden=false;}
        finally{lock(false);}
    }
    form.addEventListener('change',event=>{
        if(event.target.name!=='rating' || busy)return;
        const score=Number(event.target.value);track('rate_box_star_selected',{rating:score});feedback.hidden=score===5;send.hidden=false;
        form.querySelectorAll('.rate-box-stars label').forEach(label=>label.classList.toggle('is-selected',Number(label.querySelector('input').value)<=score));
        document.getElementById('rate-box-selection').textContent=box.dataset.selected.replace('{0}',String(score));
    });
    form.addEventListener('submit',event=>{event.preventDefault();submit();});
    document.getElementById('rate-box-public-link').addEventListener('click',()=>{
        if(id && !thanks.hidden && !document.getElementById('rate-box-public').hidden){
            track('rate_box_public_review_clicked',{rating:5});
            post(id+'/public-review').catch(()=>{track('rate_box_error',{operation:'public_review'});});
        }
    });
    box.querySelectorAll('[data-rate-dismiss]').forEach(button=>button.addEventListener('click',()=>{
        if(!id || box.hidden)return;box.hidden=true;
        track('rate_box_dismissed',{after_rating:rated});
        post(id+'/dismiss').catch(()=>{/* Display cooldown is already persisted even if closing fails offline. */});
    }));
})();
