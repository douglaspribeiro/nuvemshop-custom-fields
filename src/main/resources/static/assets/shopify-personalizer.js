(function () {
    "use strict";

    const appOrigin = "https://campos-personalizados.wzhub.pro";
    const script = document.currentScript || Array.from(document.scripts).find((candidate) => {
        return candidate.src && candidate.src.indexOf("shopify-personalizer.js") !== -1;
    });
    const scriptUrl = script && script.src ? new URL(script.src) : null;
    const apiOrigin = scriptUrl && isAppOrigin(scriptUrl.origin) ? scriptUrl.origin : appOrigin;
    const shop = (scriptUrl && scriptUrl.searchParams.get("shop")) || getShop();

    if (!shop) {
        track("disabled", { reason: "missing_shop" });
        return;
    }

    const productId = getProductId();
    if (!productId) {
        track("disabled", { shop: shop, reason: "missing_product_id", path: window.location.pathname });
        return;
    }

    fetch(
        apiOrigin
        + "/shopify/public/shops/"
        + encodeURIComponent(shop)
        + "/personalization?productId="
        + encodeURIComponent(productId)
        + "&path="
        + encodeURIComponent(window.location.pathname),
        { credentials: "omit" }
    )
        .then((response) => response.ok ? response.json() : null)
        .then((config) => {
            if (!config || !config.enabled || !Array.isArray(config.fields) || config.fields.length === 0) {
                track("disabled", { shop: shop, productId: productId, reason: "no_fields" });
                return;
            }
            renderFields(config.fields);
        })
        .catch((error) => {
            track("error", { shop: shop, productId: productId, reason: error && error.message });
        });

    function renderFields(fields) {
        const form = findProductForm();
        if (!form) {
            track("disabled", { shop: shop, productId: productId, reason: "form_not_found" });
            return;
        }
        if (form.querySelector("[data-ncf-shopify-root]")) {
            return;
        }
        const root = document.createElement("div");
        root.className = "ncf-personalization";
        root.setAttribute("data-ncf-shopify-root", "true");

        fields.forEach((field) => {
            root.appendChild(renderField(field));
        });

        const submit = form.querySelector("[type='submit'], button[name='add']");
        if (submit && submit.parentNode) {
            submit.parentNode.insertBefore(root, submit);
        } else {
            form.appendChild(root);
        }
        track("enabled", { shop: shop, productId: productId, fields: fields.length });
    }

    function renderField(field) {
        const wrapper = document.createElement("label");
        wrapper.className = "ncf-field";
        const label = document.createElement("span");
        label.className = "ncf-label";
        label.textContent = field.label + (field.required ? " *" : "");
        wrapper.appendChild(label);

        let input;
        if (field.fieldType === "TEXTAREA") {
            input = document.createElement("textarea");
        } else if (field.fieldType === "SELECT") {
            input = document.createElement("select");
            const empty = document.createElement("option");
            empty.value = "";
            empty.textContent = "Selecione";
            input.appendChild(empty);
            (field.options || []).forEach((optionValue) => {
                const option = document.createElement("option");
                option.value = optionValue;
                option.textContent = optionValue;
                input.appendChild(option);
            });
        } else {
            input = document.createElement("input");
            input.type = field.fieldType === "NUMBER" ? "number" : "text";
        }

        input.name = "properties[" + field.label + "]";
        input.placeholder = field.placeholder || "";
        input.required = Boolean(field.required);
        if (field.maxLength && input.tagName !== "SELECT") {
            input.maxLength = Number(field.maxLength);
        }
        if (field.validationPattern && input.tagName === "INPUT") {
            input.pattern = field.validationPattern;
        }
        wrapper.appendChild(input);
        return wrapper;
    }

    function findProductForm() {
        return document.querySelector("form[action*='/cart/add']")
            || document.querySelector("product-form form")
            || document.querySelector("form[action*='cart/add']");
    }

    function getShop() {
        return window.Shopify && window.Shopify.shop;
    }

    function getProductId() {
        const meta = window.ShopifyAnalytics && window.ShopifyAnalytics.meta;
        if (meta && meta.product && meta.product.id) {
            return String(meta.product.id);
        }
        const productJson = document.querySelector("[data-product-json], script[type='application/json'][data-product]");
        if (productJson) {
            try {
                const parsed = JSON.parse(productJson.textContent);
                if (parsed && parsed.id) {
                    return String(parsed.id);
                }
            } catch (ignored) {
                return null;
            }
        }
        return null;
    }

    function isAppOrigin(origin) {
        return origin && origin !== "null";
    }

    function track(event, details) {
        const params = new URLSearchParams(details || {});
        params.set("event", event);
        if (!params.has("shop") && shop) {
            params.set("shop", shop);
        }
        navigator.sendBeacon && navigator.sendBeacon(apiOrigin + "/shopify/public/script-events?" + params.toString());
    }
})();
