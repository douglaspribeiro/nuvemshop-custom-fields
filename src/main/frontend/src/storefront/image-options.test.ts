// @vitest-environment jsdom
import { describe, it, expect } from "vitest";
import { imageChoices } from "./image-options";
import { validate, toCartProperties } from "./values";
import type { PersonalizationField } from "../shared/config";

const field: PersonalizationField={label:"Capa",propertyName:"Capa",fieldType:"IMAGE_SELECT",required:true,maxLength:100,
    placeholder:null,validationPattern:null,options:["Floral","Azul"],imageOptions:[
        {label:"Floral",thumbnailUrl:"https://assets.test/floral.jpg",imageUrl:"https://assets.test/floral-large.jpg"},
        {label:"Azul",thumbnailUrl:"https://assets.test/azul.jpg",imageUrl:"https://assets.test/azul-large.jpg"}]};

describe("image choices",()=>{
    it("selects a named option, updates accessible state, allows enlargement and sends the name",()=>{
        const select=document.createElement("select");select.append(new Option("—",""));
        field.options.forEach(label=>select.append(new Option(label,label)));
        const grid=imageChoices(field,select,"pt-BR");const buttons=grid.querySelectorAll("button");
        expect(validate([field],{})).toHaveLength(1);buttons[0].click();
        expect(select.value).toBe("Floral");expect(buttons[0].getAttribute("aria-pressed")).toBe("true");
        expect(toCartProperties([field],{Capa:select.value})).toEqual({Capa:"Floral"});
        expect(grid.querySelector("a")?.href).toBe("https://assets.test/floral-large.jpg");
        select.value="Azul";select.dispatchEvent(new Event("change"));
        expect(buttons[0].getAttribute("aria-pressed")).toBe("false");expect(buttons[1].getAttribute("aria-pressed")).toBe("true");
        expect(validate([field],{Capa:"Inexistente"})).toHaveLength(1);
    });
});
