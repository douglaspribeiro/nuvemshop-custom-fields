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
    const cartSnapshotTtlMs = 24 * 60 * 60 * 1000;
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
        renderStoredCartProperties();
        syncCartPropertyColors();
        forceForegroundMediumWhite();
    });
    ready(initialize);

    function initialize() {
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
            if (!config || !config.enabled || !Array.isArray(config.fields) || config.fields.length === 0) {
                track("disabled", { storeId: storeId, productId: productId, reason: "config_disabled_or_empty" });
                return;
            }
            styleConfig = config.style || styleConfig || {};
            rememberFieldLabels(config.fields);
            whenSdkFieldsAbsent(config.fields, productId, function () {
                renderFields(form, config.fields, styleConfig);
                bindCartSnapshot(form, config.fields);
                renderStoredCartProperties();
                syncCartPropertyColors();
                form.dataset.ncfInjected = "true";
                track("injected", { storeId: storeId, productId: productId });
            });
        });
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
        return document.querySelector("#product_form")
            || document.querySelector("form[action*='/cart/add']")
            || document.querySelector("form[action*='/comprar']")
            || document.querySelector("form[action*='/carrinho']")
            || document.querySelector("form.js-product-form")
            || document.querySelector("form[data-product-id]")
            || document.querySelector("[data-product-id] form")
            || document.querySelector("form");
    }

    function findProductId(targetForm) {
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
        const productNode = targetForm.querySelector("[data-product-id]")
            || document.querySelector("[data-product-id]")
            || targetForm;
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

    function bindCartSnapshot(targetForm, fields) {
        if (!targetForm || targetForm.dataset.ncfSnapshotBound === "true") {
            return;
        }
        targetForm.dataset.ncfSnapshotBound = "true";
        targetForm.addEventListener("submit", () => rememberCartSnapshot(targetForm, fields), true);
        const submit = targetForm.querySelector("button[type='submit'], input[type='submit'], .js-addtocart");
        if (submit) {
            submit.addEventListener("click", () => rememberCartSnapshot(targetForm, fields), true);
        }
    }

    function rememberCartSnapshot(targetForm, fields) {
        const values = cartFieldValues(targetForm, fields);
        if (values.length === 0) {
            return;
        }
        const snapshots = cartSnapshots();
        snapshots.push({
            productId: findProductId(targetForm),
            variantId: findVariantId(targetForm),
            productName: findProductName(),
            productPath: window.location.pathname,
            fields: values,
            createdAt: Date.now()
        });
        writeCartSnapshots(snapshots.slice(-30));
    }

    function cartFieldValues(targetForm, fields) {
        return (fields || [])
            .map((field) => {
                const key = field.propertyName || field.label;
                const input = Array.from(targetForm.elements || [])
                    .find((element) => element.name === "properties[" + key + "]");
                const value = input && input.value ? String(input.value).trim() : "";
                return value ? { label: field.label || key, value: value } : null;
            })
            .filter(Boolean);
    }

    function findVariantId(targetForm) {
        const input = Array.from(targetForm.elements || [])
            .find((element) => [
                "variant_id",
                "variation_id",
                "variant",
                "variation",
                "id"
            ].includes(element.name) && element.value);
        return input ? String(input.value) : null;
    }

    function findProductName() {
        const title = document.querySelector([
            "h1",
            ".js-product-name",
            ".product-name",
            ".product-title",
            "[class*='product-name' i]",
            "[class*='product-title' i]"
        ].join(","));
        return title && title.textContent ? title.textContent.trim() : null;
    }

    function cartSnapshots() {
        try {
            const storage = window.localStorage;
            if (!storage) {
                return [];
            }
            const raw = storage.getItem(cartSnapshotKey());
            const snapshots = JSON.parse(raw || "[]");
            const fresh = Array.isArray(snapshots)
                ? snapshots.filter((snapshot) => snapshot && Date.now() - Number(snapshot.createdAt || 0) < cartSnapshotTtlMs)
                : [];
            if (fresh.length !== snapshots.length) {
                writeCartSnapshots(fresh);
            }
            return fresh;
        } catch (ignored) {
            return [];
        }
    }

    function writeCartSnapshots(snapshots) {
        try {
            const storage = window.localStorage;
            if (storage) {
                storage.setItem(cartSnapshotKey(), JSON.stringify(snapshots));
            }
        } catch (ignored) {
        }
    }

    function cartSnapshotKey() {
        return "ncf:cart-properties:" + (storeId || "unknown");
    }

    function renderStoredCartProperties() {
        const snapshots = cartSnapshots();
        if (snapshots.length === 0) {
            return;
        }
        const used = new Set();
        cartLineTargets().forEach((target) => {
            if (!target || target.querySelector(".ncf-cart-property-list")) {
                return;
            }
            const snapshot = matchingCartSnapshot(target, snapshots, used);
            if (!snapshot || visibleCartProperties(target, snapshot.fields)) {
                return;
            }
            const list = document.createElement("div");
            list.className = "ncf-cart-property ncf-cart-property-list";
            snapshot.fields.forEach((field) => {
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
            ".cart-item-name",
            ".cart-item__name",
            ".line-item-name",
            ".line-item__name",
            "[class*='cart-item' i] [class*='name' i]",
            "[class*='line-item' i] [class*='name' i]"
        ];
        return Array.from(new Set(document.querySelectorAll(selectors.join(","))));
    }

    function matchingCartSnapshot(target, snapshots, used) {
        const targetText = normalizedText(cartLineMatchText(target));
        let fallback = null;
        for (let index = snapshots.length - 1; index >= 0; index -= 1) {
            if (used.has(index)) {
                continue;
            }
            const snapshot = snapshots[index];
            if (!fallback) {
                fallback = { snapshot: snapshot, index: index };
            }
            if (snapshotMatches(targetText, snapshot)) {
                used.add(index);
                return snapshot;
            }
        }
        if (snapshots.length === 1 && fallback) {
            used.add(fallback.index);
            return fallback.snapshot;
        }
        return null;
    }

    function snapshotMatches(targetText, snapshot) {
        const candidates = [
            snapshot.productName,
            snapshot.productPath,
            snapshot.productId,
            snapshot.variantId
        ].map(normalizedText).filter(Boolean);
        return candidates.some((candidate) => targetText.indexOf(candidate) !== -1 || candidate.indexOf(targetText) !== -1);
    }

    function cartLineMatchText(target) {
        const row = target.closest("tr,.js-cart-item,.cart-item,[class*='cart-item' i],[class*='line-item' i]") || target;
        const pieces = [row.textContent || ""];
        row.querySelectorAll("img[alt],img[title],a[href],[data-product-id],[data-variant-id]").forEach((node) => {
            pieces.push(node.getAttribute("alt") || "");
            pieces.push(node.getAttribute("title") || "");
            pieces.push(node.getAttribute("href") || "");
            pieces.push(node.getAttribute("data-product-id") || "");
            pieces.push(node.getAttribute("data-variant-id") || "");
        });
        return pieces.join(" ");
    }

    function visibleCartProperties(target, fields) {
        const text = target.textContent || "";
        return fields.some((field) => text.indexOf(field.label + ":") !== -1);
    }

    function normalizedText(value) {
        return String(value || "")
            .toLowerCase()
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .replace(/\s+/g, " ")
            .trim();
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
            renderStoredCartProperties();
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
        const wrapper = document.createElement("label");
        wrapper.className = "ncf-field";

        const text = document.createElement("span");
        text.className = "ncf-label";
        text.textContent = field.label;
        wrapper.appendChild(text);

        const input = createInput(field);
        input.name = "properties[" + (field.propertyName || field.label) + "]";
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

        if (field.fieldType === "SELECT") {
            const select = document.createElement("select");
            const empty = document.createElement("option");
            empty.value = "";
            empty.textContent = "";
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
        input.type = field.fieldType === "NUMBER" ? "number" : "text";
        return input;
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
