(() => {
    const hex = /^#[0-9a-f]{6}$/i;
    const locale = document.documentElement.lang;

    for (const details of document.querySelectorAll(".complement-appearance")) {
        const form = details.closest("form");
        const preview = details.querySelector(".complement-preview");
        const card = preview.querySelector(".complement-preview-card");
        const image = card.querySelector("img");
        const status = preview.querySelector("[data-preview-status]");
        const label = card.querySelector("[data-preview-label]");
        const price = card.querySelector("[data-preview-price]");
        const checkbox = card.querySelector("input[type='checkbox']");
        const quantity = card.querySelector(".complement-preview-quantity");
        const units = quantity.querySelector("[data-preview-units]");
        const subtotal = quantity.querySelector("[data-preview-subtotal]");
        let loaded = false;
        let count = 1;
        let unitPrice;
        let formatter;

        function render() {
            label.textContent = form.elements.namedItem("label").value;
            const background = form.elements.namedItem("backgroundColor").value;
            const text = form.elements.namedItem("textColor").value;
            card.style.backgroundColor = hex.test(background) ? background : "";
            card.style.color = hex.test(text) ? text : "";
            card.classList.toggle("is-borderless", form.elements.namedItem("showBorder").value === "false");
            card.classList.toggle("is-square", form.elements.namedItem("roundedCorners").value === "false");
            image.hidden = !image.hasAttribute("src") || form.elements.namedItem("showImage").value === "false";
            quantity.hidden = !checkbox.checked;
            units.textContent = count;
            subtotal.textContent = formatter && unitPrice != null ? "Subtotal: " + formatter.format(unitPrice * count) : "";
        }

        for (const row of details.querySelectorAll(".complement-color-input")) {
            const picker = row.querySelector("input[type='color']");
            const field = row.querySelector("input[type='text']");
            picker.value = hex.test(field.value) ? field.value : field.name === "backgroundColor" ? "#ffffff" : "#1f2933";
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
                    price.textContent = formatter.format(unitPrice);
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
