(() => {
    const hex = /^#[0-9a-f]{6}$/i;
    const locale = document.documentElement.lang;

    function luminance(color) {
        const [r, g, b] = [1, 3, 5].map(offset => {
            const channel = Number.parseInt(color.slice(offset, offset + 2), 16) / 255;
            return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
        });
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    function checkboxColor(background, text) {
        if (!hex.test(background)) return hex.test(text) ? text : "";
        const surface = luminance(background);
        const color = hex.test(text) ? text : "#25323d";
        const ink = luminance(color);
        return (Math.max(surface, ink) + 0.05) / (Math.min(surface, ink) + 0.05) >= 4.5
            ? color : surface >= 0.18 ? "#000000" : "#ffffff";
    }

    for (const details of document.querySelectorAll(".complement-appearance")) {
        const form = details.closest("form");
        const preview = details.querySelector(".complement-preview");
        const card = preview.querySelector(".complement-preview-card");
        const image = card.querySelector("img");
        const status = preview.querySelector("[data-preview-status]");
        const label = card.querySelector("[data-preview-label]");
        const subtitle = card.querySelector("[data-preview-subtitle]");
        const price = card.querySelector("[data-preview-price]");
        const checkbox = card.querySelector("input[type='checkbox']");
        const quantity = card.querySelector(".complement-preview-quantity");
        const units = quantity.querySelector("[data-preview-units]");
        const subtotal = quantity.querySelector("[data-preview-subtotal]");
        const decrease = quantity.querySelector('[data-preview-step="-1"]');
        let loaded = false;
        let count = 1;
        let unitPrice;
        let formatter;

        function render() {
            label.textContent = form.elements.namedItem("label").value;
            subtitle.textContent = form.elements.namedItem("subtitle").value.trim();
            subtitle.hidden = !subtitle.textContent;
            const background = form.elements.namedItem("backgroundColor").value;
            const text = form.elements.namedItem("textColor").value;
            const accent = form.elements.namedItem("accentColor").value;
            card.style.backgroundColor = hex.test(background) ? background : "";
            card.style.color = hex.test(text) ? text : "";
            card.style.setProperty("--ncf-complement-accent", hex.test(accent) ? accent : "");
            card.style.setProperty("--ncf-complement-check", checkboxColor(background, hex.test(accent) ? accent : text));
            card.classList.toggle("is-borderless", form.elements.namedItem("showBorder").value === "false");
            card.classList.toggle("is-square", form.elements.namedItem("roundedCorners").value === "false");
            image.hidden = !image.hasAttribute("src") || form.elements.namedItem("showImage").value === "false";
            quantity.hidden = !checkbox.checked;
            decrease.disabled = count <= 1;
            units.textContent = count;
            subtotal.textContent = formatter && unitPrice != null
                ? preview.dataset.total + ": " + formatter.format(unitPrice * count) : "";
            subtotal.hidden = !subtotal.textContent;
        }

        for (const row of details.querySelectorAll(".complement-color-input")) {
            const picker = row.querySelector("input[type='color']");
            const field = row.querySelector("input[type='text']");
            picker.value = hex.test(field.value) ? field.value : field.name === "backgroundColor" ? "#ffffff" : "#25323d";
            picker.addEventListener("input", () => { field.value = picker.value; render(); });
            field.addEventListener("input", () => {
                if (hex.test(field.value)) picker.value = field.value;
            });
        }
        for (const button of quantity.querySelectorAll("[data-preview-step]")) {
            button.addEventListener("click", () => {
                count = Math.max(1, count + Number(button.dataset.previewStep));
                render();
            });
        }
        form.addEventListener("input", render);
        form.addEventListener("change", render);
        details.addEventListener("toggle", async () => {
            if (!details.open || loaded) return;
            loaded = true;
            try {
                const response = await fetch(preview.dataset.url, {headers: {Accept: "application/json"}});
                if (!response.ok) throw new Error("catalog_preview_unavailable");
                const data = await response.json();
                const currency = data.currency && {style: "currency", currency: data.currency};
                formatter = new Intl.NumberFormat(locale, currency || {});
                if (data.price) {
                    unitPrice = Number(data.price);
                    price.textContent = formatter.format(unitPrice) + " " + preview.dataset.unit;
                }
                if (data.image) image.src = data.image;
                status.hidden = true;
            } catch {
                loaded = false;
                status.textContent = preview.dataset.error;
                status.hidden = false;
            }
            render();
        });
        render();
    }
})();
