import type { PersonalizationField } from "../shared/config";

/** The select remains the native form control and readable fallback when images fail. */
export function imageChoices(field: PersonalizationField, select: HTMLSelectElement, locale?: string): HTMLElement {
    const grid=document.createElement("div");
    grid.style.cssText="display:grid;grid-template-columns:repeat(auto-fit,minmax(120px,1fr));gap:8px;margin-bottom:8px";
    const buttons: HTMLButtonElement[]=[];
    for (const option of field.imageOptions ?? []) {
        const card=document.createElement("div");
        const button=document.createElement("button");button.type="button";button.dataset.value=option.label;
        button.style.cssText="display:flex;flex-direction:column;align-items:center;gap:4px;width:100%;padding:8px;border:1px solid #c8d3d8;border-radius:8px;background:transparent;color:inherit;font:inherit";
        const image=document.createElement("img");image.src=option.thumbnailUrl;image.alt=option.label;
        image.loading="lazy";image.width=120;image.height=120;image.style.objectFit="contain";
        const text=document.createElement("span");text.textContent=option.label;
        button.append(image,text);
        button.addEventListener("click",()=>{select.value=option.label;select.dispatchEvent(new Event("change",{bubbles:true}));});
        const link=document.createElement("a");link.href=option.imageUrl;link.target="_blank";link.rel="noopener";
        link.textContent=locale?.startsWith("es") ? "Ampliar imagen" : "Ampliar imagem";
        link.setAttribute("aria-label",`${link.textContent}: ${option.label}`);
        card.append(button,link);grid.append(card);buttons.push(button);
    }
    function highlight() {for(const button of buttons){const selected=button.dataset.value===select.value;button.setAttribute("aria-pressed",String(selected));button.style.border=selected?"2px solid #1976d2":"1px solid #c8d3d8";}}
    select.addEventListener("change",highlight);highlight();
    return grid;
}
