(function () {
    "use strict";
    const ui = window.ncfBuyerFilesUi || {};
    const get = id => document.getElementById(`buyer-${id}`);
    if (!get("orders")) return;
    const t = (key, ...args) => (ui[key] || key).replace(/\{(\d+)\}/g, (_, index) => String(args[Number(index)] ?? ""));
    const size = bytes => {
        let value = Number(bytes), unit = 0;const units = ["B", "KB", "MB", "GB"];
        while (value >= 1000 && unit < units.length - 1) { value /= 1000;unit++; }
        return new Intl.NumberFormat(document.documentElement.lang || "pt", {maximumFractionDigits: 2}).format(value) + " " + units[unit];
    };
    const date = value => value ? new Date(value).toLocaleDateString(document.documentElement.lang || "pt") : "—";
    const selected = new Map();
    let files = [], page = 1, pages = 1, busy = false, loading = false, generation = 0, refreshTimer, review = [];
    const available = file => file.state === "ORDERED" && new Date(file.expires_at) > new Date();
    const deletable = file => file.state === "ORDERED";
    const total = list => list.reduce((sum, file) => sum + Number(file.size_bytes), 0);
    const show = text => { get("status").textContent = text; };
    function checkboxState(input, list) {
        const count = list.filter(file => selected.has(file.id)).length;
        input.checked = list.length > 0 && count === list.length;
        input.indeterminate = count > 0 && count < list.length;
        input.disabled = busy || !list.length;
    }
    function updateSelection() {
        const list = [...selected.values()];
        get("selected").textContent = list.length ? t("selection", list.length, size(total(list))) : t("noneSelected");
        get("delete-selected").disabled = busy || loading || !list.length;
        checkboxState(get("select-page"), files.filter(deletable));
        document.querySelectorAll("[data-file-select]").forEach(input => {
            input.checked = selected.has(input.dataset.fileSelect); input.disabled = busy;
        });
        document.querySelectorAll("[data-order-select]").forEach(input => checkboxState(input, files.filter(file => String(file.order_id) === input.dataset.orderSelect && deletable(file))));
        get("previous").disabled = busy || loading || page <= 1;
        get("next").disabled = busy || loading || page >= pages;
    }
    function select(list, checked) {
        if (busy) return;
        const additions = list.filter(file => !selected.has(file.id));
        if (checked && selected.size + additions.length > 200) { show(t("maxSelection")); updateSelection(); return; }
        for (const file of list) { if (checked) selected.set(file.id, file); else selected.delete(file.id); }
        updateSelection();
    }
    function node(tag, text, className) {
        const element = document.createElement(tag);
        if (text != null) element.textContent = text;
        if (className) element.className = className;
        return element;
    }
    function groups(list) {
        const result = new Map();
        for (const file of list) {
            const key = String(file.order_id);
            if (!result.has(key)) result.set(key, []);
            result.get(key).push(file);
        }
        return result;
    }
    function errorText(body, fallback) {
        return ({"upload.selection.invalid": t("invalidSelection"), "upload.not.found": t("missingFile"),
            "upload.not.ordered": t("missingFile"), "upload.rate.limit": t("downloadLimit")})[body?.code] || body?.error || t(fallback);
    }
    get("download-frame").addEventListener("load", () => {
        try {
            const text = get("download-frame").contentDocument?.body?.textContent;
            if (text?.trim().startsWith("{")) show(errorText(JSON.parse(text), "downloadFailed"));
            else if (text?.trim()) show(t("downloadFailed"));
        } catch { show(t("downloadFailed")); }
    });
    function render() {
        const container = get("orders");container.replaceChildren();
        for (const [orderId, list] of groups(files)) {
            const section = node("section", null, "panel buyer-order");
            const heading = node("div", null, "buyer-order-heading");
            const label = node("label", null, "checkbox");
            const input = node("input");input.type = "checkbox";input.dataset.orderSelect = orderId;input.setAttribute("aria-label", t("selectOrder", orderId));
            input.addEventListener("change", () => select(list.filter(deletable), input.checked));label.append(input, node("strong", t("order", list[0].order_number || orderId)));
            const copy = node("div");copy.append(label, node("p", t("orderSummary", list.length, size(total(list)), date(list[0].order_created_at)), "muted"));
            const statuses = node("div", null, "buyer-order-statuses");
            for (const [key, field] of [["orderStatus", "order_status"], ["paymentStatus", "payment_status"], ["shippingStatus", "shipping_status"]]) {
                const value = list[0][field], text = ui[`status.${value}`] || t("status.unknown");
                statuses.append(node("span", `${t(key)}: ${text}`, "buyer-order-status"));
            }
            copy.append(statuses);heading.append(copy);section.append(heading);
            const scroll = node("div", null, "buyer-order-scroll");
            const table = node("table", null, "table");const head = node("thead"), header = node("tr");
            for (const key of ["file", "size", "expires", "state", "actions"]) header.append(node("th", t(key)));
            head.append(header);table.append(head);const body = node("tbody");
            for (const file of list) {
                const row = node("tr"), name = node("td"), label = node("label", null, "checkbox");
                if (deletable(file)) { const check = node("input");check.type = "checkbox";check.dataset.fileSelect = file.id;check.setAttribute("aria-label", t("selectFile", file.original_name));check.addEventListener("change", () => select([file], check.checked));label.append(check); }
                const title = node("span", file.original_name || t("unavailable"));
                const product = node("span", null, "buyer-file-product");
                if (/^https?:\/\//i.test(file.product_image_url || "")) {
                    const image = node("img");image.src = file.product_image_url;image.alt = "";image.loading = "lazy";
                    image.addEventListener("error", () => image.remove());product.append(image);
                }
                const productCopy = node("span");
                if (file.product_name) productCopy.append(node("strong", file.product_name));
                productCopy.append(node("small", t("item", file.order_item_id), "muted"));product.append(productCopy);title.append(product);
                label.append(title);name.append(label);row.append(name, node("td", size(file.size_bytes)), node("td", date(file.expires_at)), node("td", t(file.state === "DELETING" ? "deleting" : available(file) ? "ordered" : "expired")));
                const actions = node("td");
                if (available(file)) { const link = node("a", t("download"));link.href = `/admin/buyer-uploads/${encodeURIComponent(file.id)}/download`;link.target = "buyer-download-frame";actions.append(link); }
                row.append(actions);body.append(row);
            }
            table.append(body);scroll.append(table);section.append(scroll);container.append(section);
        }
        get("empty").hidden = files.length > 0;updateSelection();
    }
    async function load(quiet = false) {
        clearTimeout(refreshTimer);const current = ++generation;loading = true;updateSelection();
        if (!quiet) show(t("loading"));
        const query = new URLSearchParams({page: String(page), sort: get("sort").value, orderStatus: get("order-status").value});
        if (get("before").value) query.set("before", new Date(`${get("before").value}T23:59:59.999`).toISOString());
        try {
            const response = await fetch(`/admin/buyer-uploads/data?${query}`, {headers: {Accept: "application/json"}, credentials: "same-origin"});
            if (!response.ok) throw new Error();const data = await response.json();if (current !== generation) return;
            files = data.files;page = data.page;pages = data.pages;
            for (const file of files) if (selected.has(file.id)) { if (deletable(file)) selected.set(file.id, file);else selected.delete(file.id); }
            get("occupied").textContent = size(data.usage.occupiedBytes);get("available").textContent = size(Math.max(0, data.usage.quotaBytes - data.usage.occupiedBytes));get("quota").textContent = size(data.usage.quotaBytes);
            get("disabled").hidden = data.enabled && data.usage.acceptsUploads;
            const percent = data.usage.warningPercent;get("warning").hidden = percent < 50;get("warning").textContent = percent >= 100 ? t("full") : t("warning", percent);
            get("pending").hidden = !data.pendingBytes;get("pending").textContent = t("pending", size(data.pendingBytes));
            get("page").textContent = t("page", page, pages, data.totalOrders);
            render();if (!quiet) show("");
            if (data.pendingBytes && !busy) refreshTimer = setTimeout(() => { void load(true); }, 5000);
        } catch { if (current === generation) show(t("loadFailed")); }
        finally { if (current === generation) { loading = false;updateSelection(); } }
    }
    get("select-page").addEventListener("change", () => select(files.filter(deletable), get("select-page").checked));
    get("delete-selected").addEventListener("click", () => {
        review = [...selected.values()];if (!review.length || busy) return;
        get("delete-summary").textContent = t("confirmSummary", review.length, groups(review).size, size(total(review)));
        get("delete-orders").replaceChildren(...[...groups(review)].map(([id, list]) => node("li", `${t("order", id)} · ${list.length} · ${size(total(list))}`)));
        get("delete-error").textContent = "";get("delete-dialog").showModal();
    });
    get("cancel-delete").addEventListener("click", () => get("delete-dialog").close());
    get("confirm-delete").addEventListener("click", async () => {
        if (busy || !review.length) return;busy = true;clearTimeout(refreshTimer);updateSelection();
        get("confirm-delete").disabled = true;get("cancel-delete").disabled = true;show(t("deletingNow"));
        try {
            const response = await fetch("/admin/buyer-uploads/delete-selected", {method: "POST", credentials: "same-origin", headers: {"Content-Type": "application/json", "X-NCF-Action": "delete", Accept: "application/json"}, body: JSON.stringify({ids: review.map(file => file.id), confirmed: true})});
            const result = await response.json().catch(() => { throw new Error(t("deleteFailed")); });if (!response.ok) throw Object.assign(new Error(errorText(result, "deleteFailed")), {localized: true});
            for (const file of review) selected.delete(file.id);review = [];get("delete-dialog").close();
            busy = false;await load(true);show(t("deleteQueued", result.files, size(result.bytes)));
        } catch (error) { get("delete-error").textContent = error.localized ? error.message : t("deleteFailed");show(get("delete-error").textContent); }
        finally { busy = false;get("confirm-delete").disabled = false;get("cancel-delete").disabled = false;updateSelection(); }
    });
    get("filters").addEventListener("submit", event => { event.preventDefault();page = 1;void load(); });
    get("clear-filters").addEventListener("click", () => { get("before").value = "";get("sort").value = "oldest";get("order-status").value = "all";page = 1;void load(); });
    get("refresh").addEventListener("click", () => void load());
    get("previous").addEventListener("click", () => { page--;void load(); });get("next").addEventListener("click", () => { page++;void load(); });
    window.addEventListener("pagehide", () => clearTimeout(refreshTimer));
    updateSelection();void load();
})();
