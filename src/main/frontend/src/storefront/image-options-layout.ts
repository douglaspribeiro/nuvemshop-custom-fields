/** Keep in sync with the traditional script's mobile image styles. */
export const MOBILE_IMAGE_OPTIONS_CSS = `
@media (max-width:767px) {
    .ncf-image-options[data-mobile-columns="2"] {grid-template-columns:repeat(2,minmax(0,1fr))!important}
    .ncf-image-options[data-mobile-columns="3"] {grid-template-columns:repeat(3,minmax(0,1fr))!important}
    .ncf-image-options[data-mobile-columns] > div {min-width:0;overflow-wrap:anywhere}
    .ncf-image-options[data-mobile-columns] button {box-sizing:border-box;min-width:0;padding:6px!important;white-space:normal;overflow-wrap:anywhere}
    .ncf-image-options[data-mobile-columns] img {width:100%;max-width:120px;height:auto;aspect-ratio:1;object-fit:contain}
    .ncf-image-options[data-mobile-columns] span {max-width:100%;white-space:normal;overflow-wrap:anywhere}
}
`;

export function applyMobileImageLayout(grid: HTMLElement, columns?: number | null): void {
    grid.classList.add("ncf-image-options");
    if (columns !== 2 && columns !== 3) return;
    grid.dataset.mobileColumns = String(columns);
    const doc = grid.ownerDocument;
    if (!doc.getElementById("ncf-mobile-image-styles")) {
        const style = doc.createElement("style");
        style.id = "ncf-mobile-image-styles";
        style.textContent = MOBILE_IMAGE_OPTIONS_CSS;
        doc.head.append(style);
    }
}
