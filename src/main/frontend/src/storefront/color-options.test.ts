// @vitest-environment jsdom
import {expect,it} from 'vitest';
import {colorChoices} from './color-options';
import {validate,toCartProperties} from './values';
import type {PersonalizationField} from '../shared/config';
const field:PersonalizationField={label:'Cor',propertyName:'Cor',fieldType:'COLOR_SELECT',required:true,maxLength:100,placeholder:null,validationPattern:null,
    options:['Azul marinho','Branco'],colorOptions:[{label:'Azul marinho',color:'#123ABC'},{label:'Branco',color:'#FFFFFF'}]};
it('requires an offered color and serializes its name to the item',()=>{
    const select=document.createElement('select');select.append(new Option('Escolha',''));field.options.forEach(name=>select.append(new Option(name,name)));
    const group=colorChoices(field,select),buttons=group.querySelectorAll('button');
    expect(validate([field],{})).toHaveLength(1);buttons[1].click();
    expect(select.value).toBe('Branco');expect(buttons[1].getAttribute('aria-pressed')).toBe('true');expect(buttons[0].getAttribute('aria-pressed')).toBe('false');
    expect(toCartProperties([field],{Cor:select.value})).toEqual({Cor:'Branco'});
    expect(validate([field],{Cor:'#FFFFFF'})).toHaveLength(1);expect(validate([field],{Cor:'Cor livre'})).toHaveLength(1);
    expect(validate([{...field,required:false}],{})).toHaveLength(0);
    select.value='';select.dispatchEvent(new Event('change'));expect(buttons[1].getAttribute('aria-pressed')).toBe('false');
    expect(buttons[0].querySelector('span')!.style.backgroundColor).toBe('rgb(18, 58, 188)');
});
it('keeps malicious names as text and ignores unsafe color styles',()=>{
    const select=document.createElement('select');const group=colorChoices({...field,colorOptions:[{label:'<img src=x onerror=alert(1)>',color:'#000000'},{label:'Invalid',color:'url(x)'}]},select);
    expect(group.querySelector('img')).toBeNull();expect(group.querySelectorAll('button')).toHaveLength(1);expect(group.textContent).toContain('<img');
});
