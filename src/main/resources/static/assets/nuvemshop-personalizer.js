(function () {
    "use strict";

    if (typeof window === "undefined" || typeof document === "undefined") {
        return;
    }

    const appOrigin = "https://campos-personalizados.wzhub.pro";
    const maxAttempts = 30;
    // Coexistencia com o script NubeSDK: a homologacao exige os dois registrados, mas o
    // mesmo comportamento nao pode rodar duas vezes (campos duplicados na vitrine).
    const sdkMarkerId = "ncf-sdk-fields";
    const sdkSlotSelector = '[data-nubesdk-slot="before_product_detail_add_to_cart"]';
    const sdkWaitMs = 2500;
    const sdkPollMs = 150;
    let suppressedBySdk = false;
    let cartItemsFromEvent = null;
    let cartEventsBound = false;
    let attempts = 0;
    let lastRetryReason = "not_started";
    let cartColorObserverStarted = false;
    let styleConfig = {};

    const script = document.currentScript || Array.from(document.scripts).find((candidate) => {
        return candidate.src
            && (
                candidate.src.indexOf("nuvemshop-personalizer.js") !== -1
                || candidate.src.indexOf("apps-scripts.tiendanube.com") !== -1
                || candidate.src.indexOf("apps-scripts.nuvemshop.com.br") !== -1
            );
    });

    if (!script) {
        track("missing_script_tag", { reason: "script_not_found" });
        return;
    }

    const scriptUrl = new URL(script.src);
    const storeId = scriptUrl.searchParams.get("store") || getStoreId();
    const apiOrigin = isAppOrigin(scriptUrl.origin) ? scriptUrl.origin : appOrigin;

    // Versões/IDs coexistentes compartilham o mesmo runtime por loja e página.
    // A reserva precisa acontecer antes de qualquer fetch ou callback assíncrono.
    const runtimeKey = "__ncfLegacyRuntime:" + storeId;
    if (storeId && window[runtimeKey]) return;
    if (storeId) window[runtimeKey] = true;

    injectStyles();
    watchCartPropertyColors();
    ready(forceForegroundMediumWhite);
    track("loaded", { storeId: storeId, scriptSrc: script.src });

    if (!storeId) {
        track("disabled", { reason: "missing_store_id", scriptSrc: script.src });
        return;
    }

    loadStoreStyle(function (style) {
        styleConfig = style || {};
        renderCartProperties();
        syncCartPropertyColors();
        forceForegroundMediumWhite();
    });
    installQuickBuyFields();
    ready(initialize);

    function installQuickBuyFields() {
        const policies = new Map();
        const replaying = new WeakSet();
        const prepared = new WeakSet();
        const cardSelector = ".js-item-product[data-product-id], [data-component='product-list-item'][data-product-id]";

        function policy(productId) {
            if (!policies.has(productId)) {
                const controller = new AbortController();
                const timer = window.setTimeout(() => controller.abort(), 5000);
                const pending = fetch(apiOrigin + "/public/stores/" + encodeURIComponent(storeId)
                    + "/personalization?productId=" + encodeURIComponent(productId), {
                    credentials: "omit", headers: { Accept: "application/json" }, signal: controller.signal
                }).then(response => {
                    if (!response.ok) throw new Error("quick_buy_policy_unavailable");
                    return response.json();
                }).then(config => {
                    if (typeof config?.enabled !== "boolean" || (config.enabled && !Array.isArray(config.fields))) {
                        throw new Error("quick_buy_policy_invalid");
                    }
                    return config;
                }).catch(() => {
                    // Sem política, a página do produto é o caminho para conferir os campos.
                    policies.delete(productId);
                    return null;
                }).finally(() => window.clearTimeout(timer));
                policies.set(productId, pending);
            }
            return policies.get(productId);
        }

        function productUrl(card) {
            const link = card.querySelector("a.item-link[href], a.js-product-item-image-link-private[href], a.js-item-image-link[href]")
                || Array.from(card.querySelectorAll("a[href]")).find(link => /\/(productos|produtos|products)\//.test(link.getAttribute("href")));
            if (!link) return null;
            try {
                const url = new URL(link.getAttribute("href"), window.location.href);
                return url.origin === window.location.origin && url.pathname !== window.location.pathname
                    ? url.href : null;
            } catch (ignored) { return null; }
        }

        function intercept(event) {
            const target = event.target instanceof Element ? event.target : null;
            const control = event.type === "submit" ? target
                : target && target.closest(".js-item-buy-open, .js-quickshop-modal-open, .js-quickshop-open, .js-addtocart, [type='submit'], [data-component='product-list-item.show-add-to-cart'], [data-component='product-list-item.add-to-cart']");
            const card = control && control.closest(cardSelector);
            if (!card || replaying.has(control)) return;
            const form = control.closest("form") || card.querySelector("form.js-product-form, form[action*='/comprar'], form");
            if (form && prepared.has(form) && form.querySelector(".ncf-personalization")) return;
            const url = productUrl(card);
            if (!url || !/^\d+$/.test(card.dataset.productId || "")) return;
            event.preventDefault();
            event.stopImmediatePropagation();
            if (control.dataset.ncfQuickBuyPending === "true") return;
            control.dataset.ncfQuickBuyPending = "true";
            const submitter = event.submitter;
            void policy(card.dataset.productId).then(config => {
                if (!card.isConnected) return;
                delete control.dataset.ncfQuickBuyPending;
                if (!config || (config.enabled && config.fields.length > 0 && !form)) {
                    // Themes without a local quick-buy form retain the safe product-page fallback.
                    track("quick_buy_redirect", { storeId: storeId, productId: card.dataset.productId });
                    window.location.assign(url);
                    return;
                }
                if (config.enabled && config.fields.length > 0) {
                    if (!form.querySelector(".ncf-personalization")) {
                        rememberFieldLabels(config.fields);
                        renderFields(form, config.fields, config.style || styleConfig);
                        bindProductValidation(form, config.fields);
                    }
                    prepared.add(form);
                    track("quick_buy_fields", { storeId: storeId, productId: card.dataset.productId });
                    // Opening the theme panel is safe; an add/submit must wait for buyer input.
                    const opener = card.querySelector(".js-item-buy-open, .js-quickshop-modal-open, .js-quickshop-open");
                    const opening = event.type === "click" && control === opener;
                    if (!opening) {
                        if (opener) { replaying.add(opener); try { opener.click(); } finally { replaying.delete(opener); } }
                        const input = form.querySelector(".ncf-personalization input:not([type=hidden]), .ncf-personalization select, .ncf-personalization textarea");
                        if (input) input.focus();
                        return;
                    }
                }
                replaying.add(control);
                try {
                    if (event.type === "submit") control.requestSubmit(submitter || undefined);
                    else control.click();
                } finally { replaying.delete(control); }
            });
        }

        document.addEventListener("click", intercept, true);
        document.addEventListener("submit", intercept, true);
        // Antecipar só os cards de interesse evita consultar o catálogo inteiro em cada visita.
        function warm(event) {
            const card = event.target instanceof Element && event.target.closest(cardSelector);
            if (card && /^\d+$/.test(card.dataset.productId || "")) void policy(card.dataset.productId);
        }
        document.addEventListener("pointerover", warm, true);
        document.addEventListener("focusin", warm, true);
    }

    function initialize() {
        // Patagonia usa React sem form de compra e não fornece cart:before_update.
        // Não confundir seu formulário de frete com o formulário do produto.
        const patagonia = isPatagoniaStorefront();
        if (patagonia === null) {
            lastRetryReason = "waiting_storefront_theme";
            retry();
            return;
        }
        if (patagonia) {
            loadPatagoniaFallback();
            return;
        }
        if (suppressedBySdk) {
            return;
        }
        const form = findProductForm();
        const productId = findProductId(form);

        if (!form || !productId) {
            lastRetryReason = !form ? "missing_product_form" : "missing_product_id";
            retry();
            return;
        }

        if (form.dataset.ncfInjected === "true") {
            return;
        }

        track("load_config", { storeId: storeId, productId: productId });
        loadConfig(productId, function (config) {
            if (config && config.paused === true) {
                whenSdkFieldsAbsent([], productId, function () {
                    if (form.dataset.ncfInjected === "true" || form.querySelector(".ncf-paused-notice")) return;
                    const notice = document.createElement("div");
                    notice.className = "ncf-personalization ncf-paused-notice";
                    notice.setAttribute("role", "status");
                    notice.textContent = String(config.locale || "pt-BR").toLowerCase().startsWith("pt")
                        ? "A personalização deste produto está temporariamente indisponível. Após a compra, envie as informações de personalização diretamente à loja, informando o número do pedido."
                        : "La personalización de este producto está temporalmente no disponible. Después de la compra, envía la información de personalización directamente a la tienda, indicando el número de pedido.";
                    const submit = form.querySelector("button[type='submit'], input[type='submit'], .js-addtocart");
                    const anchor = findActionContainer(form, submit) || submit;
                    if (anchor && anchor.parentNode) anchor.parentNode.insertBefore(notice, anchor);
                    else form.appendChild(notice);
                    form.dataset.ncfInjected = "true";
                });
                return;
            }
            if (!config || !config.enabled || !Array.isArray(config.fields) || config.fields.length === 0) {
                track("disabled", { storeId: storeId, productId: productId, reason: "config_disabled_or_empty" });
                return;
            }
            styleConfig = config.style || styleConfig || {};
            rememberFieldLabels(config.fields);
            whenSdkFieldsAbsent(config.fields, productId, function () {
                // Outra inicialização pode ter concluído durante a consulta/espera pelo SDK.
                if (form.dataset.ncfInjected === "true" || form.querySelector(".ncf-personalization")) return;
                form.dataset.ncfInjected = "true";
                renderFields(form, config.fields, styleConfig);
                bindProductValidation(form, config.fields);
                renderCartProperties();
                syncCartPropertyColors();
                track("injected", { storeId: storeId, productId: productId });
            });
        });
    }

    function isPatagoniaStorefront() {
        const runtime = window.nubeSDK;
        let theme;
        try {
            const store = runtime && runtime.getState && runtime.getState().store;
            theme = store && store.theme;
        } catch (ignored) {
            // O runtime pode ainda não estar pronto; consulte também o tema exposto por LS.
        }
        theme = theme || (window.LS && window.LS.theme && window.LS.theme.code);
        if (typeof theme === "string" && theme.trim()) {
            return theme.trim().toLowerCase() === "patagonia";
        }
        // A CDN identifica Lightspeed, não um tema específico. Aguarde sua identificação.
        return Array.from(document.scripts).some((item) => item.src.indexOf("https://lightspeed-cdn.mitiendanube.com/") === 0)
            ? null : false;
    }

    function loadPatagoniaFallback() {
        if (document.getElementById("ncf-patagonia-script")) return;
        const fallback = document.createElement("script");
        fallback.id = "ncf-patagonia-script";
        fallback.src = apiOrigin + "/assets/nuvemshop-patagonia.js";
        fallback.onerror = () => track("disabled", { storeId: storeId, reason: "patagonia_fallback_load_failed" });
        document.head.appendChild(fallback);
    }

    /**
     * Decide se o legado deve renderizar. Em loja sem NubeSDK ele e o unico caminho e
     * renderiza direto. Em loja com SDK, espera o app do SDK aparecer e sai de cena; se
     * o SDK nao renderizar dentro da janela, renderiza como fallback — um produto
     * personalizavel sem campo nenhum e pior que um risco de piscada de UI duplicada.
     */
    function whenSdkFieldsAbsent(fields, productId, render) {
        if (typeof window.nubeSDK === "undefined") {
            render();
            return;
        }

        const deadline = Date.now() + sdkWaitMs;

        (function poll() {
            if (sdkFieldsRendered(fields)) {
                suppressedBySdk = true;
                track("suppressed_by_sdk", { storeId: storeId, productId: productId });
                return;
            }
            if (Date.now() >= deadline) {
                track("sdk_present_but_idle", { storeId: storeId, productId: productId });
                render();
                return;
            }
            window.setTimeout(poll, sdkPollMs);
        })();
    }

    function sdkFieldsRendered(fields) {
        if (document.getElementById(sdkMarkerId)) {
            return true;
        }
        // Fallback caso o `id` nao chegue ao DOM: procura os nossos proprios campos no slot,
        // comparando por propertyName. Evita confundir com o que outro app renderize ali.
        const slot = document.querySelector(sdkSlotSelector);
        if (!slot) {
            return false;
        }
        const names = fields.map(function (field) {
            return field.propertyName || field.label;
        });
        const inputs = slot.querySelectorAll("input, select, textarea");
        return Array.prototype.some.call(inputs, function (element) {
            return names.indexOf(element.name) !== -1;
        });
    }

    function ready(callback) {
        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", callback, { once: true });
            return;
        }
        callback();
    }

    function retry() {
        attempts += 1;
        if (attempts <= maxAttempts) {
            window.setTimeout(initialize, 250);
            return;
        }
        track("disabled", { storeId: storeId, reason: lastRetryReason });
    }

    function loadConfig(productId, callback) {
        const url = apiOrigin
            + "/public/stores/" + encodeURIComponent(storeId) + "/personalization"
            + "?productId=" + encodeURIComponent(productId)
            + "&path=" + encodeURIComponent(window.location.pathname);

        fetch(url, {
            credentials: "omit",
            headers: {
                "Accept": "application/json"
            }
        })
            .then((response) => response.ok ? response.json() : null)
            .then(callback)
            .catch((error) => {
                track("error", {
                    storeId: storeId,
                    productId: productId,
                    reason: error && error.message ? error.message : "config_fetch_failed"
                });
            });
    }

    function loadStoreStyle(callback) {
        const url = apiOrigin
            + "/public/stores/" + encodeURIComponent(storeId) + "/style";

        fetch(url, {
            credentials: "omit",
            headers: {
                "Accept": "application/json"
            }
        })
            .then((response) => response.ok ? response.json() : {})
            .then(callback)
            .catch(() => callback({}));
    }

    function isAppOrigin(origin) {
        return origin === appOrigin || origin.indexOf("localhost") !== -1;
    }

    function getStoreId() {
        const candidates = [
            window.LS && window.LS.store && window.LS.store.id,
            window.LS && window.LS.store_id,
            window.LS && window.LS.storeId,
            window.TiendaNube && window.TiendaNube.store && window.TiendaNube.store.id,
            window.Nuvemshop && window.Nuvemshop.store && window.Nuvemshop.store.id
        ];
        for (const candidate of candidates) {
            if (candidate) {
                return String(candidate);
            }
        }
        const meta = document.querySelector("meta[name='nuvemshop:store_id'], meta[property='nuvemshop:store_id']");
        return meta && meta.content ? meta.content : null;
    }

    function findProductForm() {
        // Cart, search and newsletter forms can share /comprar/ or live on a PDP.
        // Only a form that actually adds a product may receive editable fields.
        const selectors = ["#product_form", "form[action*='/cart/add']", "form[action*='/comprar']",
            "form.js-product-form", "form[data-product-id]", "[data-product-id] form"];
        for (const selector of selectors) {
            const form = Array.from(document.querySelectorAll(selector)).find(candidate => {
                if (candidate.closest(".js-item-product, [data-component='product-list-item']")) return false;
                if (candidate.matches(".js-ajax-cart-panel, #cart-form")
                    || candidate.querySelector("[name='go_to_checkout'], [name='checkout']")) return false;
                return candidate.id === "product_form" || candidate.matches(".js-product-form, [data-product-id], form[action*='/cart/add']")
                    || !!candidate.querySelector("input[name='add_to_cart'], input[name='product_id'], input[name='product'], input[name='product[id]']");
            });
            if (form) return form;
        }
        return null;
    }

    function findProductId(targetForm) {
        const card = targetForm && targetForm.closest(".js-item-product[data-product-id], [data-component='product-list-item'][data-product-id]");
        if (card) return card.dataset.productId;
        const globalProductId = findGlobalProductId();
        if (globalProductId) {
            return globalProductId;
        }
        if (!targetForm) {
            return null;
        }
        if (targetForm.dataset.productId) {
            return targetForm.dataset.productId;
        }
        const ownerWithProductId = targetForm.closest("[data-product-id]");
        if (ownerWithProductId && ownerWithProductId.dataset.productId) {
            return ownerWithProductId.dataset.productId;
        }
        const productIdInput = targetForm.querySelector("input[name='product_id'], input[name='product'], input[name='product[id]'], input[name='add_to_cart']");
        if (productIdInput && productIdInput.value) {
            return productIdInput.value;
        }
        const productNode = targetForm.querySelector("[data-product-id]") || targetForm;
        return productNode.dataset.productId || null;
    }

    function findGlobalProductId() {
        const candidates = [
            window.LS && window.LS.product && window.LS.product.id,
            window.LS && window.LS.product_id,
            window.LS && window.LS.productId,
            window.TiendaNube && window.TiendaNube.product && window.TiendaNube.product.id,
            window.Nuvemshop && window.Nuvemshop.product && window.Nuvemshop.product.id,
            window.product && window.product.id,
            window.product && window.product.product_id
        ];
        for (const candidate of candidates) {
            if (candidate) {
                return String(candidate);
            }
        }
        const meta = document.querySelector("meta[name='nuvemshop:product_id'], meta[property='nuvemshop:product_id']");
        return meta && meta.content ? meta.content : null;
    }

    function renderFields(targetForm, fields, style) {
        const container = document.createElement("div");
        container.className = "ncf-personalization";
        applyProductTextColor(container, style);

        fields.forEach((field) => {
            container.appendChild(renderField(field));
        });

        const submit = targetForm.querySelector("button[type='submit'], input[type='submit'], .js-addtocart");
        const actionContainer = findActionContainer(targetForm, submit);
        if (actionContainer && actionContainer.parentNode) {
            actionContainer.parentNode.insertBefore(container, actionContainer);
        } else if (submit && submit.parentNode) {
            submit.parentNode.insertBefore(container, submit);
        } else {
            targetForm.appendChild(container);
        }
    }

    function findActionContainer(targetForm, submit) {
        if (!submit) {
            return null;
        }
        const quantitySelector = ".js-quantity, .js-product-quantity, [class*='quantity'], input[name='quantity'], input[name='cantidad']";
        let candidate = submit.parentElement;

        while (candidate && candidate !== targetForm) {
            const parent = candidate.parentElement;
            if (candidate.querySelector(quantitySelector)) {
                return candidate;
            }
            if (parent && parent !== targetForm && parent.querySelector(quantitySelector) && parent.contains(submit)) {
                return parent;
            }
            if (candidate.classList && candidate.classList.contains("row") && candidate.contains(submit)) {
                return candidate;
            }
            candidate = candidate.parentElement;
        }

        return submit.parentElement && submit.parentElement !== targetForm ? submit.parentElement : null;
    }

    function injectStyles() {
        if (document.getElementById("ncf-personalization-style")) {
            return;
        }
        const style = document.createElement("style");
        style.id = "ncf-personalization-style";
        style.textContent = ".ncf-personalization{display:block;box-sizing:border-box;width:100%;clear:both;margin:16px 0 14px;color:var(--ncf-product-text-color,inherit)}.ncf-field{display:block;margin:0 0 12px}.ncf-label{display:block;margin:0 0 6px;font-weight:700}.ncf-field input,.ncf-field textarea,.ncf-field select{box-sizing:border-box;display:block;width:100%;max-width:100%;min-height:40px;padding:8px 10px;border:1px solid #c8d3d8;border-radius:6px;background:#fff;font:inherit}.ncf-field textarea{min-height:88px;resize:vertical}.ncf-cart-property{color:var(--ncf-cart-property-color,inherit)!important}.ncf-cart-property--dark{color:var(--ncf-cart-property-color,rgba(255,255,255,.88))!important}.ncf-cart-property-list{display:block;margin-top:4px;font-size:.875em;line-height:1.35}.ncf-cart-property-line{display:block}.ncf-cart-property-line b{font-weight:700}[data-testid*='cart' i] [data-testid='product-name-qty'] .text-foreground-medium,.summary-details [data-testid='product-name-qty'] .text-foreground-medium{color:var(--ncf-cart-property-color,inherit)!important}[data-testid*='cart' i] [data-testid='product-name-qty'] .text-sm,.summary-details [data-testid='product-name-qty'] .text-sm{color:var(--ncf-cart-property-color,inherit)!important}";
        document.head.appendChild(style);
    }

    function applyProductTextColor(container, style) {
        const color = normalizedHexColor(style && style.productTextColor);
        if (color) {
            container.style.setProperty("--ncf-product-text-color", color);
            container.style.setProperty("color", color);
        }
    }

    function forceForegroundMediumWhite() {
        document.querySelectorAll(".text-foreground-medium").forEach((element) => {
            const color = configuredCartTextColor() || cartItemTitleColor(element);
            if (color) {
                element.style.setProperty("color", color, "important");
            } else {
                element.style.removeProperty("color");
            }
            element.style.setProperty("font-weight", "700", "important");
        });
    }

    function bindProductValidation(targetForm, fields) {
        if (!targetForm || targetForm.dataset.ncfValidationBound === "true") {
            return;
        }
        targetForm.dataset.ncfValidationBound = "true";
        const validateFields = (event) => {
            for (const field of fields) {
                const name = "properties[" + (field.propertyName || field.label) + "]";
                const input = Array.from(targetForm.elements).find(element => element.name === name);
                if (!input) continue;
                if (field.fieldType === "SELECT" || field.fieldType === "IMAGE_SELECT") {
                    const invalid = input.value && !(field.options || []).includes(input.value);
                    input.setCustomValidity(invalid
                        ? (document.documentElement.lang.toLowerCase().startsWith("es")
                            ? "Seleccioná una opción válida." : "Selecione uma opção válida.") : "");
                }
                const validationInput = field.fieldType === "CHECKBOX" && (field.options || []).length
                    ? input.closest(".ncf-field").querySelector("input[type=checkbox]") : input;
                if (!validationInput.checkValidity()) {
                    event.preventDefault();
                    event.stopImmediatePropagation();
                    validationInput.focus();
                    validationInput.reportValidity();
                    return;
                }
            }
        };
        targetForm.addEventListener("submit", validateFields, true);
        targetForm.addEventListener("click", event => {
            if (event.target.closest("button[type='submit'], input[type='submit'], .js-addtocart, .js-buy-now")) {
                validateFields(event);
            }
        }, true);
    }

    function renderCartProperties() {
        const ls = window.LS;
        if (!cartEventsBound && ls && typeof ls.on === "function" && ls.events?.productAddedToCart) {
            cartEventsBound = true;
            ls.on(ls.events.productAddedToCart, (event) => {
                const cart = event?.detail?.cart;
                cartItemsFromEvent = cart?.items || cart?.products || null;
                renderCartProperties();
            });
        }
        const items = cartItemsFromEvent || ls?.cart?.items || ls?.cart?.products || [];
        cartLineTargets().forEach((target) => {
            const row = target.closest("[data-item-id]") || target.parentElement?.closest(".js-cart-item,.cart-item,tr,[class*='cart-item' i]") || target;
            const identity = row.getAttribute("data-item-id") || row.querySelector("[data-item-id]")?.getAttribute("data-item-id")
                || row.querySelector("input[name^='quantity[']")?.name.match(/^quantity\[(.+)\]$/)?.[1];
            const item = identity && items.find(item => String(item.id) === identity);
            const properties = item?.properties;
            const fields = (Array.isArray(properties) ? properties.map(p => ({label: p.name || p.label || p.key, value: p.value || p.text}))
                : Object.entries(properties || {}).map(([label, value]) => ({label, value})))
                .filter(p => p.label && !String(p.label).startsWith("_") && p.value != null && String(p.value).trim())
                .map(p => ({label: String(p.label), value: String(p.value)}));
            const existing = target.querySelector(".ncf-cart-property-list");
            const nativeTarget = target.cloneNode(true);
            nativeTarget.querySelectorAll(".ncf-cart-property-list").forEach(n => n.remove());
            if (!fields.length || visibleCartProperties(nativeTarget, fields)) {
                if (existing) existing.remove();
                return;
            }
            const signature = JSON.stringify(fields);
            if (existing?.dataset.ncfProperties === signature) return;
            if (existing) existing.remove();
            const list = document.createElement("div");
            list.className = "ncf-cart-property ncf-cart-property-list";
            list.dataset.ncfProperties = signature;
            fields.forEach((field) => {
                const line = document.createElement("div");
                line.className = "ncf-cart-property-line";
                const label = document.createElement("b");
                label.textContent = field.label + ":";
                line.appendChild(label);
                line.appendChild(document.createTextNode(" " + field.value));
                list.appendChild(line);
            });
            target.appendChild(list);
        });
    }

    function cartLineTargets() {
        const selectors = [
            "[data-testid='product-name-qty']",
            ".js-cart-item-name",
            ".ajax-cart-item-link",
            ".cart-item-name",
            ".cart-item__name",
            ".line-item-name",
            ".line-item__name",
            "[class*='cart-item' i] [class*='name' i]",
            "[class*='line-item' i] [class*='name' i]"
        ];
        return Array.from(new Set(document.querySelectorAll(selectors.join(","))));
    }

    function visibleCartProperties(target, fields) {
        const text = target.textContent || "";
        return fields.some((field) => text.indexOf(field.label + ":") !== -1);
    }

    function rememberFieldLabels(fields) {
        if (!Array.isArray(fields)) {
            return;
        }
        const labels = fields
            .map((field) => field && (field.label || field.propertyName))
            .filter(Boolean);
        if (labels.length === 0) {
            return;
        }
        try {
            const storage = window.localStorage;
            if (storage) {
                storage.setItem("ncf:field-labels:" + storeId, JSON.stringify(labels));
            }
        } catch (ignored) {
        }
    }

    function watchCartPropertyColors() {
        if (cartColorObserverStarted) {
            return;
        }
        cartColorObserverStarted = true;
        ready(syncCartPropertyColors);
        if (!window.MutationObserver || !document.documentElement) {
            return;
        }
        let pending = false;
        const refreshCart = () => {
            if (document.getElementById(sdkMarkerId)) {
                document.querySelectorAll(".ncf-paused-notice").forEach(function (notice) { notice.remove(); });
            }
            renderCartProperties();
            syncCartPropertyColors();
            forceForegroundMediumWhite();
        };
        const observer = new MutationObserver(() => {
            if (pending) {
                return;
            }
            pending = true;
            window.setTimeout(() => {
                pending = false;
                refreshCart();
            }, 100);
        });
        observer.observe(document.documentElement, { childList: true, subtree: true });
        refreshCart();
    }

    function syncCartPropertyColors() {
        const candidates = cartPropertyElements();
        candidates.forEach((element) => {
            if (!element || element.closest(".ncf-personalization")) {
                return;
            }
            const configuredColor = configuredCartTextColor();
            const color = configuredColor || cartItemTitleColor(element);
            const dark = hasDarkCartBackground(element);
            element.classList.add("ncf-cart-property");
            element.classList.toggle("ncf-cart-property--dark", dark);
            if (configuredColor) {
                element.style.setProperty("--ncf-cart-property-color", configuredColor);
            } else if (color && (!dark || isLightColor(color))) {
                element.style.setProperty("--ncf-cart-property-color", color);
            } else if (dark) {
                element.style.setProperty("--ncf-cart-property-color", "rgba(255,255,255,.88)");
            } else {
                element.style.removeProperty("--ncf-cart-property-color");
            }
        });
    }

    function configuredCartTextColor() {
        return normalizedHexColor(styleConfig && styleConfig.cartTextColor);
    }

    function normalizedHexColor(value) {
        return /^#[0-9A-Fa-f]{6}$/.test(String(value || "")) ? value : null;
    }

    function cartPropertyElements() {
        const selectors = [
            ".js-cart-item-property",
            ".js-cart-item-variation",
            ".cart-item-property",
            ".cart-item-properties",
            ".cart-item-variation",
            ".cart-item__property",
            ".cart-item__properties",
            ".cart-item__variation",
            ".ncf-cart-property-list",
            "[data-testid*='cart' i] [data-testid='product-name-qty'] .text-foreground-medium",
            "[data-testid*='cart' i] [data-testid='product-name-qty'] .text-sm",
            ".summary-details [data-testid='product-name-qty'] .text-foreground-medium",
            ".summary-details [data-testid='product-name-qty'] .text-sm",
            "[class*='cart' i] [class*='propert' i]",
            "[class*='cart' i] [class*='custom' i]",
            "[class*='cart' i] [class*='attribute' i]",
            "[class*='cart' i] [class*='variation' i]",
            "[class*='cart' i] [class*='variant' i]",
            "[class*='drawer' i] [class*='propert' i]",
            "[class*='drawer' i] [class*='custom' i]",
            "[class*='drawer' i] [class*='attribute' i]",
            "[class*='drawer' i] [class*='variation' i]",
            "[class*='drawer' i] [class*='variant' i]"
        ];
        const elements = new Set();
        document.querySelectorAll(selectors.join(",")).forEach((element) => elements.add(element));
        findStoredFieldLabels().forEach((label) => {
            findCartTextElements(label).forEach((element) => elements.add(element));
        });
        return Array.from(elements);
    }

    function findStoredFieldLabels() {
        if (!storeId) {
            return [];
        }
        try {
            const storage = window.localStorage;
            if (!storage) {
                return [];
            }
            const raw = storage.getItem("ncf:field-labels:" + storeId);
            const labels = JSON.parse(raw || "[]");
            return Array.isArray(labels) ? labels.filter(Boolean) : [];
        } catch (ignored) {
            return [];
        }
    }

    function findCartTextElements(label) {
        const containers = document.querySelectorAll([
            ".js-cart-item",
            ".cart-item",
            "[class*='cart-item' i]",
            "[class*='line-item' i]",
            "[data-testid*='cart' i]",
            "[data-testid='product-name-qty']",
            ".summary-details",
            "[class*='mini-cart' i]",
            "[class*='cart' i]",
            "[id*='cart' i]",
            "[class*='carrinho' i]",
            "[id*='carrinho' i]",
            "[class*='drawer' i]",
            "[class*='modal' i]"
        ].join(","));
        const matches = [];
        containers.forEach((container) => {
            const walker = document.createTreeWalker(container, window.NodeFilter.SHOW_TEXT);
            let node = walker.nextNode();
            while (node) {
                const text = node.nodeValue || "";
                if (text.indexOf(label) !== -1 && node.parentElement) {
                    matches.push(node.parentElement);
                }
                node = walker.nextNode();
            }
        });
        return matches;
    }

    function cartItemTitleColor(element) {
        const item = element.closest(".js-cart-item,.cart-item,[class*='cart-item' i],[class*='line-item' i],[data-testid*='cart' i],.summary-details,[class*='cart' i],[class*='drawer' i]");
        if (!item) {
            return null;
        }
        const title = item.querySelector([
            "[data-testid='product-name-qty']",
            ".js-cart-item-name",
            ".ajax-cart-item-link",
            ".cart-item-name",
            ".cart-item__name",
            ".line-item-name",
            ".line-item__name",
            "[class*='product-name' i]",
            "[class*='item-name' i]",
            "[class*='name' i] a",
            "a[href*='/produtos/']",
            "a[href*='/productos/']"
        ].join(","));
        return title ? window.getComputedStyle(title).color : null;
    }

    function hasDarkCartBackground(element) {
        let node = element;
        let depth = 0;
        while (node && node !== document.body && depth < 20) {
            const style = window.getComputedStyle(node);
            if (isDarkColor(style.backgroundColor)) {
                return true;
            }
            node = node.parentElement;
            depth += 1;
        }
        return isDarkColor(window.getComputedStyle(document.body).backgroundColor);
    }

    function isDarkColor(value) {
        const rgba = parseColor(value);
        if (!rgba || rgba.alpha < 0.2) {
            return false;
        }
        return luminance(rgba) < 0.45;
    }

    function isLightColor(value) {
        const rgba = parseColor(value);
        return rgba ? luminance(rgba) > 0.55 : false;
    }

    function parseColor(value) {
        const match = String(value || "").match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([0-9.]+))?\)/);
        if (!match) {
            return null;
        }
        return {
            red: Number(match[1]),
            green: Number(match[2]),
            blue: Number(match[3]),
            alpha: match[4] === undefined ? 1 : Number(match[4])
        };
    }

    function luminance(color) {
        return ((0.2126 * color.red) + (0.7152 * color.green) + (0.0722 * color.blue)) / 255;
    }

    function renderField(field) {
        if (field.fieldType === "CHECKBOX" && (field.options || []).length) return renderCheckboxGroup(field);
        const wrapper = document.createElement(field.fieldType === "IMAGE_SELECT" ? "div" : "label");
        wrapper.className = "ncf-field";

        const text = document.createElement("span");
        text.className = "ncf-label";
        text.textContent = field.label;
        wrapper.appendChild(text);

        const input = createInput(field);
        input.name = "properties[" + (field.propertyName || field.label) + "]";
        input.required = Boolean(field.required) || field.fieldType === "SELECT";

        if (field.maxLength) {
            input.maxLength = field.maxLength;
        }
        if (field.placeholder) {
            input.placeholder = field.placeholder;
        }
        if (field.validationPattern) {
            input.pattern = field.validationPattern;
        }

        if (field.fieldType === "IMAGE_SELECT") {
            input.setAttribute("aria-label",field.label);
            wrapper.appendChild(renderImageChoices(field,input));
        }
        if (field.fieldType === "CHECKBOX") {
            wrapper.style.cssText = "display:flex;align-items:center;gap:8px;cursor:pointer";
            text.style.margin = "0";
            input.style.cssText = "width:20px;height:20px;min-height:20px;padding:0;flex-shrink:0;accent-color:currentColor";
            wrapper.prepend(input);
        } else wrapper.appendChild(input);
        return wrapper;
    }

    function renderCheckboxGroup(field) {
        const group = document.createElement("fieldset"); group.className = "ncf-field";
        group.style.cssText = "border:0;padding:0;margin:0 0 12px;min-width:0;color:inherit";
        const legend = document.createElement("legend"); legend.className = "ncf-label";
        legend.textContent = field.label + (field.required ? " *" : ""); group.append(legend);
        // Only the aggregate has a property name, so native form serialization preserves every choice.
        const aggregate = document.createElement("input"); aggregate.type = "hidden";
        aggregate.name = "properties[" + (field.propertyName || field.label) + "]";
        group.append(aggregate);
        const options = [...new Set(field.options)];
        const boxes = options.map(option => {
            const label = document.createElement("label");
            label.style.cssText = "display:flex;align-items:center;gap:8px;margin-bottom:8px;cursor:pointer";
            const box = document.createElement("input"); box.type = "checkbox"; box.value = option;
            box.style.cssText = "width:20px;height:20px;min-height:20px;padding:0;flex-shrink:0;accent-color:currentColor";
            const text = document.createElement("span"); text.textContent = option;
            box.addEventListener("change", sync);
            label.append(box, text); group.append(label); return box;
        });
        function sync() {
            const selected = boxes.filter(box => box.checked).map(box => box.value);
            aggregate.value = selected.join(", "); aggregate.disabled = !selected.length;
            const invalid = selected.some(option => !options.includes(option));
            const spanish = document.documentElement.lang.toLowerCase().startsWith("es");
            boxes[0].setCustomValidity(invalid ? (spanish ? "Seleccioná una opción válida." : "Selecione uma opção válida.")
                : field.required && !selected.length ? (spanish ? "Elegí al menos una opción." : "Escolha pelo menos uma opção.") : "");
        }
        sync(); return group;
    }

    function createInput(field) {
        if (field.fieldType === "TEXTAREA") {
            return document.createElement("textarea");
        }

        if (field.fieldType === "SELECT" || field.fieldType === "IMAGE_SELECT") {
            const select = document.createElement("select");
            const empty = document.createElement("option");
            empty.value = "";
            empty.textContent = (field.placeholder || "").trim()
                || (document.documentElement.lang.toLowerCase().startsWith("es")
                    ? "Seleccioná una opción" : "Selecione uma opção");
            select.appendChild(empty);
            (field.options || []).forEach((optionValue) => {
                const option = document.createElement("option");
                option.value = optionValue;
                option.textContent = optionValue;
                select.appendChild(option);
            });
            return select;
        }

        const input = document.createElement("input");
        input.type = field.fieldType === "CHECKBOX" ? "checkbox" : field.fieldType === "NUMBER" ? "number" : "text";
        if (field.fieldType === "CHECKBOX") input.value = document.documentElement.lang.toLowerCase().startsWith("es") ? "Sí" : "Sim";
        return input;
    }

    function renderImageChoices(field, select) {
        const grid = document.createElement("div");
        grid.style.cssText = "display:grid;grid-template-columns:repeat(auto-fit,minmax(120px,1fr));gap:8px;margin-bottom:8px";
        grid.className = "ncf-image-options";
        if (field.mobileImageColumns === 2 || field.mobileImageColumns === 3) {
            grid.dataset.mobileColumns = String(field.mobileImageColumns);
            if (!document.getElementById("ncf-mobile-image-styles")) {
                const style = document.createElement("style");
                style.id = "ncf-mobile-image-styles";
                // Keep in sync with image-options-layout.ts.
                style.textContent = `
@media (max-width:767px) {
    .ncf-image-options[data-mobile-columns="2"] {grid-template-columns:repeat(2,minmax(0,1fr))!important}
    .ncf-image-options[data-mobile-columns="3"] {grid-template-columns:repeat(3,minmax(0,1fr))!important}
    .ncf-image-options[data-mobile-columns] > div {min-width:0;overflow-wrap:anywhere}
    .ncf-image-options[data-mobile-columns] button {box-sizing:border-box;min-width:0;padding:6px!important;white-space:normal;overflow-wrap:anywhere}
    .ncf-image-options[data-mobile-columns] img {width:100%;max-width:120px;height:auto;aspect-ratio:1;object-fit:contain}
    .ncf-image-options[data-mobile-columns] span {max-width:100%;white-space:normal;overflow-wrap:anywhere}
}
`;
                document.head.append(style);
            }
        }
        const buttons = [];
        (field.imageOptions || []).forEach(option => {
            const card = document.createElement("div");
            const button = document.createElement("button");button.type = "button";button.dataset.value = option.label;
            button.style.cssText = "display:flex;flex-direction:column;align-items:center;gap:4px;width:100%;padding:8px;border:1px solid #c8d3d8;border-radius:8px;background:transparent;color:inherit;font:inherit";
            const image = document.createElement("img");image.src = new URL(option.thumbnailUrl, apiOrigin).href;image.alt = option.label;
            image.loading = "lazy";image.width = 120;image.height = 120;image.style.objectFit = "contain";
            const name = document.createElement("span");name.textContent = option.label;button.append(image,name);
            button.addEventListener("click", () => {select.value = option.label;select.dispatchEvent(new Event("change", {bubbles:true}));});
            card.append(button);
            if (field.showImageZoom !== false) {
                const link = document.createElement("a");link.href = new URL(option.imageUrl,apiOrigin).href;link.target = "_blank";link.rel = "noopener";
                link.textContent = document.documentElement.lang.startsWith("es") ? "Ampliar imagen" : "Ampliar imagem";
                link.setAttribute("aria-label",link.textContent + ": " + option.label);
                card.append(link);
            }
            grid.append(card);buttons.push(button);
        });
        function highlight() {buttons.forEach(button => {const selected = button.dataset.value === select.value;button.setAttribute("aria-pressed",String(selected));button.style.border = selected ? "2px solid #1976d2" : "1px solid #c8d3d8";});}
        select.addEventListener("change",highlight);highlight();
        return grid;
    }

    function track(eventName, details) {
        const params = new URLSearchParams();
        params.set("event", eventName);
        params.set("path", window.location.pathname);
        Object.keys(details || {}).forEach((key) => {
            const value = details[key];
            if (value !== null && value !== undefined && String(value).length > 0) {
                params.set(key, String(value).slice(0, 300));
            }
        });
        const url = apiOriginForTracking() + "/public/script-events?" + params.toString();
        fetch(url, {
            credentials: "omit",
            keepalive: true
        }).catch(() => {});
    }

    function apiOriginForTracking() {
        if (script && script.src) {
            try {
                const currentScriptUrl = new URL(script.src);
                return isAppOrigin(currentScriptUrl.origin) ? currentScriptUrl.origin : appOrigin;
            } catch (ignored) {
            }
        }
        return appOrigin;
    }
})();
