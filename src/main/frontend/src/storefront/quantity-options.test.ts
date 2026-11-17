import { quantityColumns } from './quantity-options';
import { expect, it } from "vitest";
import type { PersonalizationField } from "../shared/config";
import { validate, toCartProperties } from "./values";
const flowers:PersonalizationField={label:'Flores',propertyName:'Flores',fieldType:'QUANTITY_SELECT',required:false,
    minSelections:7,maxSelections:7,maxLength:1,validationPattern:'[',placeholder:null,options:['Rosa','Margarida','Girassol']};
it.each(['pt-BR','es-AR'])('requires seven total units and serializes quantities (%s)',locale=>{
    expect(validate([flowers],{Flores:{Rosa:3,Margarida:4}},locale)).toEqual([]);
    expect(validate([flowers],{Flores:{Rosa:7}},locale)).toEqual([]);
    expect(validate([flowers],{Flores:{Rosa:3,Margarida:3}},locale)[0].message).toContain('7');
    expect(validate([flowers],{Flores:{Rosa:3,Margarida:5}},locale)[0].message).toContain('7');
    expect(toCartProperties([flowers],{Flores:{Margarida:4,Rosa:3,Girassol:0}})).toEqual({Flores:'Rosa × 3; Margarida × 4'});
    expect(toCartProperties([{...flowers,minSelections:null}],{})).toEqual({});
});
it('rejects negative fractional unsafe or unknown quantities without accepting a string as a composition',()=>{
    for(const raw of [{Rosa:-1},{Rosa:3.5},{Rosa:Infinity},{Rosa:NaN},{Rosa:Number.MAX_SAFE_INTEGER,Margarida:1},
        {Rosa:'7'},{Outro:7},['Rosa'],null,'Rosa × 7'])expect(validate([flowers],{Flores:raw as never})).toHaveLength(1);
    expect(validate([{...flowers,minSelections:null,maxSelections:null,required:true}],{})).toHaveLength(1);
    expect(validate([{...flowers,minSelections:null,maxSelections:null}],{})).toEqual([]);
    expect(toCartProperties([flowers],{Flores:{Outro:7,Rosa:3}})).toEqual({Flores:'Rosa × 3'});
});
it('preserves option names that coincide with object prototype keys',()=>{
    const f={...flowers,options:['__proto__','constructor'],minSelections:null};
    const raw=JSON.parse('{"__proto__":3,"constructor":4}');
    expect(validate([f],{Flores:raw})).toEqual([]);
    expect(toCartProperties([f],{Flores:raw})).toEqual({Flores:'__proto__ × 3; constructor × 4'});
});

it('defaults to a list and accepts only the supported column counts',()=>{
    expect(quantityColumns(flowers)).toBe(1);
    expect(quantityColumns({...flowers,quantityColumns:2})).toBe(2);
    expect(quantityColumns({...flowers,quantityColumns:3})).toBe(3);
    expect(quantityColumns({...flowers,quantityColumns:9 as never})).toBe(1);
});
