(() => {
    function initialize(form) {
        const editor=form.querySelector('.color-options-editor');
        if(!editor || editor.dataset.initialized)return;
        editor.dataset.initialized='true';
        const type=form.querySelector('[name=fieldType]'),hidden=form.querySelector('[name=optionsText]');
        const rows=editor.querySelector('.color-options-rows'),add=editor.querySelector('.color-option-add'),status=editor.querySelector('.color-options-status');
        let options=[],previousType=type.value,otherText=hidden.value;
        if(type.value==='COLOR_SELECT'){try{options=JSON.parse(hidden.value || '[]');}catch{} if(!Array.isArray(options))options=[];otherText='';}
        const valid=()=> options.length>0 && options.length<=25 && options.every(o=>o && typeof o.label==='string' && o.label.trim() && o.label.trim().length<=100 && !/[\x00-\x1f\x7f]/.test(o.label) && /^#[0-9a-f]{6}$/i.test(o.color)) && new Set(options.map(o=>o.label.trim().toLowerCase())).size===options.length;
        function sync(){
            if(type.value==='COLOR_SELECT')hidden.value=JSON.stringify(options);
            status.textContent=valid()?'':editor.dataset.invalid;
            add.disabled=options.length>=25;
            hidden.dispatchEvent(new Event('input',{bubbles:true}));
        }
        function render(){
            rows.replaceChildren();
            options.forEach((option,index)=>{
                const row=document.createElement('div');row.className='color-option-row';row.dataset.index=String(index);
                const handle=document.createElement('button');handle.type='button';handle.dataset.orderHandle='';handle.textContent='⠿';handle.setAttribute('aria-label',editor.dataset.drag);
                const name=document.createElement('label'),caption=document.createElement('span'),input=document.createElement('input');
                caption.textContent=editor.dataset.name;input.value=option.label || '';input.maxLength=100;input.disabled=type.value!=='COLOR_SELECT';
                input.addEventListener('input',()=>{option.label=input.value;sync();});name.append(caption,input);
                const pickerLabel=document.createElement('label'),pickerCaption=document.createElement('span'),picker=document.createElement('input');
                pickerCaption.textContent=editor.dataset.color;picker.type='color';picker.value=option.color || '#000000';picker.disabled=type.value!=='COLOR_SELECT';
                picker.addEventListener('input',()=>{option.color=picker.value;sync();});pickerLabel.append(pickerCaption,picker);
                const actions=document.createElement('div');actions.className='row-actions';
                function button(key,action,disabled){const b=document.createElement('button');b.type='button';b.className='button small';b.textContent=editor.dataset[key];b.disabled=disabled;b.addEventListener('click',()=>{action();sync();render();const next=options.indexOf(option);rows.children[next<0?Math.min(index,options.length-1):next]?.querySelector('button')?.focus();});actions.append(b);}
                button('up',()=>{[options[index-1],options[index]]=[options[index],options[index-1]];},index===0);
                button('down',()=>{[options[index+1],options[index]]=[options[index],options[index+1]];},index===options.length-1);
                button('remove',()=>options.splice(index,1),false);
                row.append(handle,name,pickerLabel,actions);rows.append(row);
            });add.disabled=options.length>=25;
        }
        window.ncfOrderable?.(rows,()=>{options=[...rows.children].map(row=>options[Number(row.dataset.index)]);sync();render();});
        add.addEventListener('click',()=>{if(options.length>=25)return;options.push({label:'',color:'#000000'});sync();render();rows.lastElementChild?.querySelector('input')?.focus();});
        function visibility(){
            if(previousType!==type.value){
                if(type.value==='COLOR_SELECT'){otherText=hidden.value;hidden.value=JSON.stringify(options);}
                else if(previousType==='COLOR_SELECT')hidden.value=otherText;
                previousType=type.value;
            }
            editor.hidden=type.value!=='COLOR_SELECT';render();
        }
        type.addEventListener('change',visibility);
        form.addEventListener('submit',event=>{if(type.value==='COLOR_SELECT'){sync();if(!valid()){event.preventDefault();editor.querySelector('input')?.focus();}}});
        visibility();
    }
    window.ncfInitColorOptions=initialize;
    document.querySelectorAll('.field-editor-form').forEach(initialize);
})();
