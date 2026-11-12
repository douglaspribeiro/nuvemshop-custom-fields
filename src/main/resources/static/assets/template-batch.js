(() => {
 'use strict';
 const root=document.getElementById('template-batch');if(!root)return;
 const ui=window.ncfTemplateBatchUi||{}, get=id=>document.getElementById('batch-'+id), selected=new Set();
 const t=(key,...args)=>(ui[key]||key).replace(/\{(\d+)\}/g,(_,n)=>String(args[Number(n)]??''));
 let products=[],batch=null,busy=false;
 const say=text=>{get('status').textContent=text;};
 const node=(tag,text,cls)=>{const n=document.createElement(tag);if(text!=null)n.textContent=text;if(cls)n.className=cls;return n;};
 async function request(path,body) {
  const response=await fetch('/admin/templates/batch'+path,{method:body?'POST':'GET',credentials:'same-origin',headers:{Accept:'application/json',...(body?{'Content-Type':'application/json','X-NCF-Action':'template-write'}:{})},...(body?{body:JSON.stringify(body)}:{})});
  const data=await response.json();if(!response.ok)throw new Error(data.error||t('batch.operation.failed'));return data;
 }
 const filtered=()=>products.filter(p=>(p.name+' '+p.id).toLocaleLowerCase().includes(get('search').value.trim().toLocaleLowerCase())
  &&(!get('brand').value||p.brand===get('brand').value)&&(!get('category').value||Object.prototype.hasOwnProperty.call(p.categories,get('category').value))
  &&(get('config').value==='all'||get('config').value==='configured'&&p.configured||get('config').value==='unconfigured'&&!p.configured||get('config').value==='linked'&&p.sourceKey===root.dataset.key));
 function renderProducts() {
  const list=filtered();get('products').replaceChildren();
  if(!list.length)get('products').append(node('p',t('empty')));
  for(const p of list) {
   const label=node('label',null,'checkbox batch-product'),check=node('input');check.type='checkbox';check.checked=selected.has(p.id);check.disabled=busy;
   check.addEventListener('change',()=>{if(check.checked&&selected.size>=200){check.checked=false;say(t('batch.selection.invalid'));return;}if(check.checked)selected.add(p.id);else selected.delete(p.id);get('count').textContent=t('selection',selected.size);});
   const copy=node('span',p.name);if(p.sourceKey)copy.append(node('small',`${t('source')}: ${p.sourceKey===root.dataset.key?t('linked'):t('otherSource')} · ${t('revision')} ${p.sourceRevision}`,'muted'));
   label.append(check,copy);get('products').append(label);
  }
  get('count').textContent=t('selection',selected.size);get('review').disabled=busy;get('select-all').disabled=busy;
 }
 function options(id,values){get(id).replaceChildren(node('option',t('all')));get(id).firstChild.value='';for(const [value,label] of values){const option=node('option',label);option.value=value;get(id).append(option);}}
 async function catalog() {
  products=await request('/products');
  options('brand',[...new Set(products.map(p=>p.brand).filter(Boolean))].sort().map(b=>[b,b]));
  const categories=new Map();products.forEach(p=>Object.entries(p.categories).forEach(([id,name])=>categories.set(id,name)));
  options('category',[...categories].sort((a,b)=>a[1].localeCompare(b[1])));renderProducts();
 }
 function fields(list) {
  const ul=node('ul',null,'batch-fields');if(!list.length)ul.append(node('li',t('empty')));
  for(const field of list) {
   const li=node('li'),details=node('details'),summary=node('summary',field.label);details.append(summary);
   const lines=[t('type.'+field.fieldType),t(field.required?'required':'optional')];
   if(field.maxLength)lines.push(t('maxLength',field.maxLength));
   if(field.placeholder)lines.push(t('example',field.placeholder));
   if(field.validationPattern)lines.push(t('validation',field.validationPattern));
   if(field.optionsText)lines.push(t('options',field.fieldType==='COLOR_SELECT'?JSON.parse(field.optionsText).map(o=>o.label+' ('+o.color+')').join(', '):field.optionsText));
   if(field.fieldType==='FILE_UPLOAD')lines.push(t('maxFiles',field.maxFiles||1));
   if(field.fieldType==='IMAGE_SELECT'){
    try{lines.push(t('options',JSON.parse(field.imageOptionsJson||'[]').map(o=>o.label).join(', ')));}catch{}
    lines.push(t(field.showImageZoom?'zoomOn':'zoomOff'));if(field.mobileImageColumns)lines.push(t('columns',field.mobileImageColumns));
   }
   details.append(node('p',lines.join('\n'),'batch-field-details'));li.append(details);ul.append(li);
  }
  return ul;
 }
 function renderReview() {
  get('review-panel').hidden=false;get('review-items').replaceChildren();
  get('review-items').append(node('p',`${t(batch.mode.toLowerCase())} · ${t('revision')} ${batch.revision}`,'muted'));
  for(const item of batch.items) {
   const card=node('article',null,'batch-item'),label=node('label',null,'checkbox'),check=node('input');check.type='checkbox';check.dataset.product=String(item.productId);
   check.disabled=busy||item.state==='SUCCEEDED'||item.state==='BLOCKED';check.checked=!check.disabled&&!item.modified;
   label.append(check,node('strong',item.name));card.append(label,node('span',t(({READY:'ready',SUCCEEDED:'succeeded',FAILED:'failed',BLOCKED:'blocked'})[item.state]),'badge'));
   if(item.error)card.append(node('p',t(item.error),'notice warning'));
   if(item.modified) {
    card.append(node('p',t('modified'),'notice warning'));
    const choice=node('label',null,'checkbox'),ack=node('input');ack.type='checkbox';ack.dataset.modified=String(item.productId);ack.disabled=check.disabled;choice.append(ack,node('span',t('confirmModified')));card.append(choice);
   }
   const columns=node('div',null,'batch-comparison');
   for(const [name,list] of [['before',item.before],['after',item.after]]){const column=node('div');column.append(node('h3',t(name)),fields(list));columns.append(column);}
   card.append(columns);get('review-items').append(card);
  }
  get('execute').disabled=busy;get('new-review').disabled=busy;
  get('execute').textContent=t(batch.items.some(i=>i.state==='FAILED')?'retry':'execute');
 }
 async function history() {
  const list=await request('/history');get('history').replaceChildren();
  for(const b of list){const a=node('a',`${t('resume')} · ${b.succeeded}/${b.total} · ${t(b.mode.toLowerCase())}`,'button');
   a.href='/admin/templates/batch?'+new URLSearchParams({key:b.key,batchId:b.id});get('history').append(a);}
 }
 get('search').addEventListener('input',renderProducts);for(const id of ['brand','category','config'])get(id).addEventListener('change',renderProducts);
 get('select-all').addEventListener('click',()=>{const list=filtered();if(new Set([...selected,...list.map(p=>p.id)]).size>200){say(t('batch.selection.invalid'));return;}list.forEach(p=>selected.add(p.id));renderProducts();});
 get('review').addEventListener('click',async()=>{
  if(busy)return;busy=true;renderProducts();say(t('running'));
  try{batch=await request('/review',{key:root.dataset.key,mode:get('mode').value,productIds:[...selected]});
   get('confirm').checked=false;
   const url=new URL(location.href);url.searchParams.set('batchId',batch.id);window.history.replaceState(null,'',url);say('');
  }catch(error){say(error.message);}finally{busy=false;renderProducts();if(batch)renderReview();}
 });
 get('execute').addEventListener('click',async()=>{
  if(busy||!batch)return;if(!get('confirm').checked){say(t('template.confirm'));return;}
  const work=[...get('review-items').querySelectorAll('[data-product]:checked')].map(c=>({productId:Number(c.dataset.product),confirmed:true,allowModified:Boolean(get('review-items').querySelector(`[data-modified="${c.dataset.product}"]:checked`))}));
  if(!work.length){say(t('batch.selection.invalid'));return;}
  if(work.some(w=>batch.items.find(i=>i.productId===w.productId).modified&&!w.allowModified)){say(t('batch.modified.confirm'));return;}
  busy=true;renderReview();renderProducts();say(t('running'));
  try{for(const item of work){batch=await request('/'+batch.id+'/execute',item);renderReview();}say(t('complete'));await history();}
  catch(error){say(error.message);try{batch=await request('/'+batch.id);}catch{}}
  finally{busy=false;renderReview();renderProducts();}
 });
 get('new-review').addEventListener('click',()=>{if(busy||!batch)return;selected.clear();batch.items.filter(i=>i.state!=='SUCCEEDED').forEach(i=>selected.add(i.productId));if(!selected.size)batch.items.forEach(i=>selected.add(i.productId));get('mode').value=batch.mode;get('review').click();});
 (async()=>{try{await history();if(root.dataset.batchId){batch=await request('/'+root.dataset.batchId);get('mode').value=batch.mode;renderReview();}await catalog();}catch(error){say(error.message);}})();
})();
