import type { PersonalizationField } from "../shared/config";
import { messages } from "../shared/i18n";
import { type QuantityMap, quantityMap, quantityError, quantityText, quantityTotal } from "./quantity-options";

export function quantityOptionsControl(field: PersonalizationField, locale?: string | null, initial?: unknown) {
    const wrapper = document.createElement("fieldset");
    wrapper.className = "ncf-quantity-options";
    wrapper.style.cssText = "border:0;margin:0;padding:0;min-width:0;color:inherit";
    const style=document.createElement("style");style.textContent=QUANTITY_STYLES;wrapper.append(style);
    const legend = document.createElement("legend"); legend.textContent = field.label; wrapper.append(legend);
    const aggregate = document.createElement("input"); aggregate.type = "hidden";
    aggregate.name = `properties[${field.propertyName || field.label}]`; wrapper.append(aggregate);
    const previous = quantityMap(initial) ?? {};
    const list=document.createElement("div");list.className="ncf-quantity-list";wrapper.append(list);
    const inputs = [...new Set(field.options)].map(option => {
        const label = document.createElement("div"); label.className="ncf-quantity-row";
        const caption = document.createElement("span"); caption.className="ncf-quantity-name";caption.textContent = option;
        const input = document.createElement("input"); input.type = "number"; input.min = "0"; input.step = "1";
        input.max = String(field.maxSelections ?? Number.MAX_SAFE_INTEGER);
        input.value = String(Object.prototype.hasOwnProperty.call(previous, option) ? previous[option] : 0);
        input.setAttribute("aria-label", option); input.dataset.quantityOption = option;
        const stepper=document.createElement("div");stepper.className="ncf-quantity-stepper";
        const minus=document.createElement("button"),plus=document.createElement("button");minus.type=plus.type="button";
        minus.textContent="−";plus.textContent="+";
        minus.setAttribute("aria-label",(locale?.startsWith("es")?"Disminuir ":"Diminuir ")+option);
        plus.setAttribute("aria-label",(locale?.startsWith("es")?"Aumentar ":"Aumentar ")+option);
        minus.addEventListener("click",()=>{input.value=String(Math.max(0,(Number(input.value)||0)-1));sync();});
        plus.addEventListener("click",()=>{input.value=String(Math.max(0,(Number(input.value)||0))+1);sync();});
        stepper.append(minus,input,plus);
        input.addEventListener("input", sync); input.addEventListener("change", sync);
        label.append(caption, stepper); list.append(label); return input;
    });
    const counter = document.createElement("small");counter.className="ncf-quantity-counter"; counter.setAttribute("aria-live", "polite"); wrapper.append(counter);
    function read(): QuantityMap {
        return Object.fromEntries(inputs.map(input => [input.dataset.quantityOption!, input.value === "" && !input.validity.badInput ? 0 : input.valueAsNumber]));
    }
    function sync() {
        const values = read(); aggregate.value = quantityText(field, values); aggregate.disabled = !aggregate.value;
        inputs.forEach(input=>{
            const buttons=input.parentElement!.querySelectorAll("button");
            buttons[0].disabled=!(input.valueAsNumber>0);
            buttons[1].disabled=field.maxSelections!=null && quantityTotal(values)>=field.maxSelections;
        });
        inputs[0]?.setCustomValidity(quantityError(field, values, locale) ?? "");
        counter.textContent = messages(locale).quantityCount(quantityTotal(values), field.maxSelections)
            + (field.minSelections ? " · " + messages(locale).quantityMin(field.minSelections) : "");
    }
    sync(); return { wrapper, aggregate, validation: inputs[0], read };
}

const QUANTITY_STYLES='.ncf-quantity-options{font:inherit;color:inherit}.ncf-quantity-list{display:grid;grid-template-columns:1fr;gap:10px;margin:10px 0}.ncf-quantity-row{display:flex;align-items:center;justify-content:space-between;gap:12px;min-width:0;padding:12px;border:1px solid #ddd;border-color:color-mix(in srgb,currentColor 18%,transparent);border-radius:8px}.ncf-quantity-name{min-width:0;overflow-wrap:anywhere;line-height:1.4}.ncf-quantity-stepper{display:flex;align-items:center;flex-shrink:0;min-width:124px;border:1px solid #ccc;border-color:color-mix(in srgb,currentColor 28%,transparent);border-radius:6px;overflow:hidden}.ncf-quantity-stepper button{appearance:none;display:flex;align-items:center;justify-content:center;min-width:36px;width:36px;min-height:40px;padding:0;border:0;border-radius:0;background:transparent;color:inherit;font:inherit;font-size:20px;cursor:pointer}.ncf-quantity-stepper button:disabled{opacity:.35;cursor:default}.ncf-quantity-stepper input{appearance:textfield;-moz-appearance:textfield;box-sizing:border-box;width:52px;min-width:0;min-height:40px;margin:0;padding:4px;border:0;border-radius:0;background:transparent;color:inherit;font:inherit;text-align:center}.ncf-quantity-stepper input::-webkit-inner-spin-button,.ncf-quantity-stepper input::-webkit-outer-spin-button{-webkit-appearance:none;margin:0}.ncf-quantity-stepper :focus-visible{outline:2px solid currentColor;outline-offset:-3px}.ncf-quantity-counter{display:block;font-size:.9em;line-height:1.5;opacity:.85}@media(min-width:700px){.ncf-quantity-list{grid-template-columns:repeat(2,minmax(0,1fr))}}';
