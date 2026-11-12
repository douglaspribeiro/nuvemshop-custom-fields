import type { PersonalizationField } from '../shared/config';

/** Named native select remains available for fallback and form validation. */
export function colorChoices(field: PersonalizationField, select: HTMLSelectElement): HTMLElement {
    const group=document.createElement('div');group.className='ncf-color-options';
    group.style.cssText='display:flex;flex-wrap:wrap;gap:10px;margin:8px 0';
    const buttons: HTMLButtonElement[]=[];
    for(const option of field.colorOptions ?? []) {
        if(!/^#[0-9a-f]{6}$/i.test(option.color))continue;
        const button=document.createElement('button');button.type='button';button.dataset.value=option.label;
        button.style.cssText='display:flex;align-items:center;gap:8px;min-height:44px;max-width:100%;overflow-wrap:anywhere;border:1px solid currentColor;border-radius:8px;background:transparent;color:inherit;font:inherit;padding:8px 12px;cursor:pointer';
        const chip=document.createElement('span');chip.setAttribute('aria-hidden','true');
        chip.style.cssText='display:inline-block;width:28px;height:28px;flex-shrink:0;border:1px solid #777;border-radius:50%';chip.style.backgroundColor=option.color;
        const name=document.createElement('span');name.textContent=option.label;button.append(chip,name);
        button.addEventListener('click',()=>{select.value=option.label;select.dispatchEvent(new Event('change',{bubbles:true}));});
        buttons.push(button);group.append(button);
    }
    const sync=()=>buttons.forEach(button=>{
        const selected=button.dataset.value===select.value;button.setAttribute('aria-pressed',String(selected));
        button.style.outline=selected?'2px solid currentColor':'';button.style.outlineOffset='2px';
    });
    select.addEventListener('change',sync);sync();return group;
}
