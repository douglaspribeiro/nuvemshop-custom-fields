(function () {
    "use strict";

    const script = document.currentScript || Array.from(document.scripts).find((candidate) => {
        return candidate.src && candidate.src.indexOf("nuvemshop-personalizer.js") !== -1;
    });

    if (!script) {
        return;
    }

    const scriptUrl = new URL(script.src);
    const storeId = scriptUrl.searchParams.get("store");

    if (!storeId) {
        return;
    }

    const form = findProductForm();
    const productId = findProductId(form);

    if (!form || !productId || form.dataset.ncfInjected === "true") {
        return;
    }

    fetch(scriptUrl.origin + "/public/stores/" + encodeURIComponent(storeId) + "/personalization?productId=" + encodeURIComponent(productId) + "&path=" + encodeURIComponent(window.location.pathname), {
        credentials: "omit",
        headers: {
            "Accept": "application/json"
        }
    })
        .then((response) => response.ok ? response.json() : null)
        .then((config) => {
            if (!config || !config.enabled || !Array.isArray(config.fields) || config.fields.length === 0) {
                return;
            }
            injectStyles();
            renderFields(form, config.fields);
            form.dataset.ncfInjected = "true";
        })
        .catch(() => {});

    function findProductForm() {
        return document.querySelector("#product_form")
            || document.querySelector("form[action*='/cart/add']")
            || document.querySelector("form.js-product-form")
            || document.querySelector("form[data-product-id]");
    }

    function findProductId(targetForm) {
        if (!targetForm) {
            return null;
        }
        const addToCartInput = targetForm.querySelector("input[name='add_to_cart']");
        if (addToCartInput && addToCartInput.value) {
            return addToCartInput.value;
        }
        const productNode = targetForm.querySelector("[data-product-id]") || targetForm;
        return productNode.dataset.productId || null;
    }

    function renderFields(targetForm, fields) {
        const container = document.createElement("div");
        container.className = "ncf-personalization";

        fields.forEach((field) => {
            container.appendChild(renderField(field));
        });

        const submit = targetForm.querySelector("button[type='submit'], input[type='submit'], .js-addtocart");
        if (submit && submit.parentNode) {
            submit.parentNode.insertBefore(container, submit);
        } else {
            targetForm.appendChild(container);
        }
    }

    function injectStyles() {
        if (document.getElementById("ncf-personalization-style")) {
            return;
        }
        const style = document.createElement("style");
        style.id = "ncf-personalization-style";
        style.textContent = ".ncf-personalization{display:grid;gap:10px;margin:14px 0}.ncf-field{display:grid;gap:6px}.ncf-label{font-weight:700}.ncf-field input,.ncf-field textarea{box-sizing:border-box;width:100%;min-height:40px;padding:8px 10px;border:1px solid #c8d3d8;border-radius:6px;font:inherit}.ncf-field textarea{min-height:88px;resize:vertical}";
        document.head.appendChild(style);
    }

    function renderField(field) {
        const wrapper = document.createElement("label");
        wrapper.className = "ncf-field";

        const text = document.createElement("span");
        text.className = "ncf-label";
        text.textContent = field.label;
        wrapper.appendChild(text);

        const input = createInput(field);
        input.name = "properties[" + field.label + "]";
        input.required = Boolean(field.required);

        if (field.maxLength) {
            input.maxLength = field.maxLength;
        }
        if (field.placeholder) {
            input.placeholder = field.placeholder;
        }
        if (field.validationPattern) {
            input.pattern = field.validationPattern;
        }

        wrapper.appendChild(input);
        return wrapper;
    }

    function createInput(field) {
        if (field.fieldType === "TEXTAREA") {
            return document.createElement("textarea");
        }

        const input = document.createElement("input");
        input.type = field.fieldType === "NUMBER" ? "number" : "text";
        return input;
    }
})();
