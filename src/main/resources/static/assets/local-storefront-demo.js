(() => {
    "use strict";
    const form = document.getElementById("product_form");
    const variant = document.getElementById("demo-variant");
    if (!form || !variant) return;
    const money = cents => (cents / 100).toLocaleString("pt-BR", {style: "currency", currency: "BRL"});
    const selected = () => variant.options[variant.selectedIndex];
    const price = () => Math.round(Number(selected().dataset.price) * 100);
    const status = document.getElementById("demo-config-status");
    const requestedVariant = new URL(window.location.href).searchParams.get("variant");
    if ([...variant.options].some(option => option.value === requestedVariant)) variant.value = requestedVariant;
    const rows = [];
    const render = () => {
        const cart = document.getElementById("demo-cart");
        cart.replaceChildren();
        for (const row of rows) {
            const line = document.createElement("div");
            line.className = "demo-cart-line";
            const title = document.createElement("strong");
            title.textContent = `${row.quantity} × ${row.variantName} — ${money(row.price * row.quantity)}`;
            const details = document.createElement("pre");
            details.style.whiteSpace = "pre-wrap";
            details.textContent = JSON.stringify(row.properties, null, 2);
            line.append(title, details); cart.append(line);
        }
        document.getElementById("demo-total").textContent = money(rows.reduce((total, row) => total + row.price * row.quantity, 0));
    };
    const showPrice = () => { document.getElementById("demo-price").textContent = money(price()); };
    variant.addEventListener("change", () => {
        showPrice();
        const url = new URL(window.location.href);
        url.searchParams.set("variant", variant.value);
        window.history.replaceState(null, "", url);
    });
    showPrice();
    form.addEventListener("submit", event => {
        event.preventDefault();
        // The personalization script validates in capture phase before this handler.
        // Its stopImmediatePropagation prevents reaching this simulated cart when invalid.
        if (!form.checkValidity()) { form.reportValidity(); return; }
        if (form.dataset.ncfInjected !== "true") { status.textContent = "Configure ao menos um campo no editor e recarregue esta página para testar."; return; }
        const quantity = Number(form.elements.namedItem("quantity").value);
        if (!Number.isSafeInteger(quantity) || quantity < 1) return;
        const properties = Object.fromEntries([...new FormData(form)].filter(([key, value]) => key.startsWith("properties[") && String(value).trim()));
        const row = {productId: Number(form.dataset.productId), variantId: Number(variant.value), variantName: selected().textContent,
            price: price(), quantity, properties};
        rows.push(row);
        document.getElementById("demo-properties").textContent = JSON.stringify(row, null, 2);
        status.textContent = "Item adicionado ao carrinho de teste. Nenhum pedido ou cobrança foi criado.";
        render();
    });
    document.getElementById("demo-clear").addEventListener("click", () => { rows.length = 0; render(); });
    status.textContent = "Se os campos não aparecerem, confira a condição da variante no editor. Salvar no editor exige recarregar esta página.";
})();
