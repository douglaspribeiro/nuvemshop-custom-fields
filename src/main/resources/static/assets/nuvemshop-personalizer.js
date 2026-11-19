(function () {
    "use strict";

    if (typeof window === "undefined" || typeof document === "undefined") {
        return;
    }

    const appOrigin = "https://campos-personalizados.wzhub.pro";
    // Substituido nos artefatos gerados para cada registro do Partner.
    const scriptVersion = "__NCF_SCRIPT_VERSION__";
    try {
        if (/^(legado-js|legadov2)\.js\?v=\d{14}$/.test(scriptVersion)) {
            const beacon = new URL(appOrigin + "/public/script-events");
            beacon.searchParams.set("event", "script_execution");
            beacon.searchParams.set("scriptVersion", scriptVersion);
            fetch(beacon.toString(), { credentials: "omit", keepalive: true }).catch(() => {});
        }
    } catch (ignored) { /* Nao interferir na vitrine. */ }
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
        loadConfig(productId, async function (config) {
            const catalog = config?.complements ? await loadComplements(productId) : null;
            if (!form.isConnected) return;
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
                    installComplements(form, [], catalog);
                });
                return;
            }
            if (!config || !config.enabled || !Array.isArray(config.fields) || config.fields.length === 0) {
                if (catalog) whenSdkFieldsAbsent([], productId, () => installComplements(form, [], catalog));
                else track("disabled", { storeId: storeId, productId: productId, reason: "config_disabled_or_empty" });
                return;
            }
            styleConfig = config.style || styleConfig || {};
            rememberFieldLabels(config.fields);
            whenSdkFieldsAbsent(config.fields, productId, function () {
                // Outra inicialização pode ter concluído durante a consulta/espera pelo SDK.
                if (form.dataset.ncfInjected === "true" || form.querySelector(".ncf-personalization")) return;
                form.dataset.ncfInjected = "true";
                renderFields(form, config.fields, styleConfig);
                if (!installComplements(form, config.fields, catalog)) bindProductValidation(form, config.fields);
                renderCartProperties();
                syncCartPropertyColors();
                track("injected", { storeId: storeId, productId: productId });
            });
        });
    }

    async function loadComplements(productId) {
        try {
            const url = apiOrigin + "/public/stores/" + encodeURIComponent(storeId)
                + "/products/" + encodeURIComponent(productId) + "/complements";
            const response = await fetch(url, {credentials: "omit", signal: AbortSignal.timeout(5000)});
            const catalog = response.ok ? await response.json() : null;
            return Array.isArray(catalog?.items) && catalog.items.length ? catalog : null;
        } catch {
            return null;
        }
    }

    function installComplements(form, fields, catalog) {
        if (!catalog || form.querySelector(".ncf-complements") || sdkFieldsRendered(fields)) return false;
        if (typeof window.LS?.addToCartEnhanced !== "function" || typeof window.jQueryNuvem !== "function") return false;
        const action = new URL(form.action);
        if (action.origin !== window.location.origin || !form.elements.namedItem("add_to_cart")) return false;

        const es = String(catalog.locale).startsWith("es");
        const say = (pt, spanish) => es ? spanish : pt;
        const currency = catalog.currency || window.LS.currency?.code;
        const price = amount => currency ? new Intl.NumberFormat(catalog.locale, {style: "currency", currency}).format(amount) : amount.toFixed(2);
        const panel = document.createElement("fieldset");
        panel.className = "ncf-personalization ncf-complements";
        applyProductTextColor(panel, styleConfig);
        const title = document.createElement("legend");
        title.className = "ncf-complements-title";
        title.textContent = say("Complementos opcionais", "Complementos opcionales");
        panel.appendChild(title);

        const choices = catalog.items.map(item => {
            const row = document.createElement("div");
            row.className = "ncf-complement";
            const appearance = item.appearance || {};
            if (/^#[0-9a-f]{6}$/i.test(appearance.backgroundColor)) row.style.backgroundColor = appearance.backgroundColor;
            if (/^#[0-9a-f]{6}$/i.test(appearance.textColor)) row.style.color = appearance.textColor;
            row.classList.toggle("ncf-complement-borderless", appearance.showBorder === false);
            row.classList.toggle("ncf-complement-square", appearance.roundedCorners === false);
            const label = document.createElement("label");
            label.className = "ncf-complement-choice";
            if (item.imageUrl?.startsWith("https://")) {
                const image = document.createElement("img");
                image.src = item.imageUrl;
                image.alt = "";
                image.width = image.height = 52;
                image.loading = "lazy";
                label.appendChild(image);
            }
            const checkbox = document.createElement("input");
            checkbox.type = "checkbox";
            const description = document.createElement("span");
            description.className = "ncf-complement-description";
            const name = document.createElement("strong");
            name.textContent = item.label;
            const unitPrice = document.createElement("span");
            unitPrice.textContent = price(Number(item.price));
            description.append(name, unitPrice);
            label.append(checkbox, description);
            const details = document.createElement("div");
            details.className = "ncf-complement-details";
            details.hidden = true;
            const quantityLabel = document.createElement("label");
            quantityLabel.textContent = say("Quantidade", "Cantidad");
            const quantity = document.createElement("input");
            quantity.type = "number";
            quantity.min = "1";
            quantity.step = "1";
            quantity.value = "1";
            quantity.required = true;
            if (item.stock != null) quantity.max = String(item.stock);
            quantity.setAttribute("aria-label", say("Quantidade", "Cantidad") + " · " + item.label);
            const controls = document.createElement("div");
            controls.className = "ncf-complement-controls";
            const quantityButton = (amount, text, accessibleLabel) => {
                const button = document.createElement("button");
                button.type = "button";
                button.textContent = text;
                button.setAttribute("aria-label", accessibleLabel + " · " + item.label);
                button.addEventListener("click", () => {
                    const next = Math.max(1, Math.min(item.stock ?? Infinity, (Number(quantity.value) || 1) + amount));
                    quantity.value = String(next);
                    update();
                });
                return button;
            };
            const decrease = quantityButton(-1, "−", say("Diminuir quantidade", "Reducir cantidad"));
            const increase = quantityButton(1, "+", say("Aumentar quantidade", "Aumentar cantidad"));
            const subtotal = document.createElement("span");
            const update = () => {
                details.hidden = !checkbox.checked;
                row.classList.toggle("ncf-complement-selected", checkbox.checked);
                quantity.disabled = !checkbox.checked;
                decrease.disabled = Number(quantity.value) <= 1;
                increase.disabled = item.stock != null && Number(quantity.value) >= item.stock;
                subtotal.textContent = quantity.checkValidity() && quantity.value
                    ? "Subtotal: " + price(Number(item.price) * Number(quantity.value)) : "";
            };
            checkbox.addEventListener("change", update);
            quantity.addEventListener("input", update);
            controls.append(decrease, quantity, increase);
            details.append(quantityLabel, controls, subtotal);
            row.append(label, details);
            panel.appendChild(row);
            update();
            return {item, checkbox, quantity, update};
        });
        const status = document.createElement("p");
        status.setAttribute("role", "status");
        const review = document.createElement("a");
        review.href = action.href;
        review.textContent = say("Conferir carrinho", "Revisar carrito");
        review.hidden = true;
        const reviewed = document.createElement("button");
        reviewed.type = "button";
        reviewed.textContent = say("Já conferi o carrinho", "Ya revisé el carrito");
        reviewed.hidden = true;
        status.className = "ncf-complements-status";
        const actions = document.createElement("div");
        actions.className = "ncf-complements-actions";
        actions.append(review, reviewed);
        panel.append(status, actions);
        insertBeforePurchase(form, panel);

        let pending = false;
        let needsReview = false;
        reviewed.addEventListener("click", () => {
            needsReview = false;
            reviewed.hidden = review.hidden = true;
            status.textContent = "";
            for (const choice of choices) {
                choice.checkbox.disabled = false;
                choice.checkbox.checked = false;
                choice.update();
            }
        });
        const observer = new MutationObserver(() => {
            if (!panel.isConnected) {
                observer.disconnect();
                return;
            }
            if (!pending && !needsReview && sdkFieldsRendered(fields)) {
                panel.remove();
                observer.disconnect();
            }
        });
        observer.observe(document.body, {childList: true, subtree: true});

        bindProductValidation(form, fields, event => {
            if (!pending && !needsReview && (!panel.isConnected || sdkFieldsRendered(fields))) return false;
            const selected = choices.filter(choice => choice.checkbox.checked);
            if (!selected.length && !pending && !needsReview) return false;
            event.preventDefault();
            event.stopImmediatePropagation();
            if (pending || needsReview) return true;
            if (!form.reportValidity()) return true;
            const invalid = selected.find(choice => !choice.quantity.value || !choice.quantity.checkValidity());
            if (invalid) {
                status.textContent = say("Confira a quantidade de cada complemento.", "Revisa la cantidad de cada complemento.");
                invalid.quantity.reportValidity();
                return true;
            }
            const data = new FormData(form);
            let groupKey = "properties[Grupo de complementos]";
            while (data.has(groupKey)) groupKey = groupKey.replace(/\]$/, "+]");
            const group = Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 12);
            data.set(groupKey, group);
            pending = true;
            panel.disabled = true;
            status.textContent = say("Adicionando ao carrinho…", "Agregando al carrito…");

            void (async () => {
                let confirmed = 0;
                let attempted = false;
                try {
                    // Resolve every target variant before mutating the cart.
                    const extras = await Promise.all(selected.map(async ({item, quantity}) => {
                        const inputs = await classicVariantInputs(item);
                        return [["add_to_cart", String(item.productId)], ["quantity", quantity.value], [groupKey, group], ...inputs];
                    }));
                    if (sdkFieldsRendered(fields)) {
                        panel.remove();
                        observer.disconnect();
                        return;
                    }
                    attempted = true;
                    await addClassicCartItem(form, data, es);
                    confirmed++;
                    consumeUploads(form, fields);
                    for (const extra of extras) {
                        await addClassicCartItem(form, extra, es);
                        confirmed++;
                    }
                    for (const choice of choices) {
                        choice.checkbox.checked = false;
                        choice.update();
                    }
                    status.textContent = say("Produto e complementos adicionados ao carrinho.", "Producto y complementos agregados al carrito.");
                    review.hidden = false;
                } catch {
                    needsReview = attempted;
                    reviewed.hidden = review.hidden = !needsReview;
                    status.textContent = confirmed
                        ? say("Inclusão parcial. Confira quais itens entraram no carrinho.", "Compra agregada parcialmente. Revisa qué artículos quedaron en el carrito.")
                        : attempted
                            ? say("Não foi possível confirmar a inclusão. Confira o carrinho antes de tentar novamente.",
                                "No pudimos confirmar la incorporación. Revisa el carrito antes de volver a intentar.")
                            : say("Um complemento está indisponível. Revise suas escolhas.", "Un complemento no está disponible. Revisa tus elecciones.");
                } finally {
                    pending = false;
                    panel.disabled = false;
                    if (needsReview) {
                        for (const choice of choices) {
                            choice.checkbox.disabled = true;
                            choice.quantity.disabled = true;
                            choice.quantity.closest(".ncf-complement-controls").querySelectorAll("button")
                                .forEach(button => { button.disabled = true; });
                        }
                    }
                }
            })();
            return true;
        });
        return true;
    }

    async function classicVariantInputs(item) {
        if (item.singleVariant) return [];
        const url = new URL(item.productUrl, window.location.origin);
        if (url.origin !== window.location.origin || !item.productUrl) throw new Error("complement_product_url");
        const response = await fetch(url.href, {mode: "same-origin", signal: AbortSignal.timeout(5000)});
        if (!response.ok) throw new Error("complement_product_page");
        const page = new DOMParser().parseFromString(await response.text(), "text/html");
        const identity = Array.from(page.querySelectorAll("form input[name='add_to_cart']"))
            .find(input => input.value === String(item.productId) && input.closest("[data-variants]"));
        const variants = JSON.parse(identity?.closest("[data-variants]").getAttribute("data-variants") || "[]");
        const variant = variants.find(candidate => String(candidate.id) === String(item.variantId));
        const selectors = Array.from(identity?.form.querySelectorAll("select[name^='variation[']") || []);
        if (!variant || !selectors.length) throw new Error("complement_variant");
        return selectors.map(select => {
            const match = /^variation\[(\d+)\]$/.exec(select.name);
            const value = match && variant["option" + match[1]];
            if (value == null || !Array.from(select.options).some(option => option.value === String(value))) {
                throw new Error("complement_variant_option");
            }
            return [select.name, String(value)];
        });
    }

    function addClassicCartItem(sourceForm, values, es) {
        return new Promise((resolve, reject) => {
            const form = document.createElement("form");
            form.method = "post";
            form.action = sourceForm.action;
            for (const [name, value] of values) {
                if (typeof value !== "string") continue;
                const input = document.createElement("input");
                input.type = "hidden";
                input.name = name;
                input.value = value;
                form.appendChild(input);
            }
            const timer = window.setTimeout(() => reject(new Error("cart_confirmation_timeout")), 10000);
            function finish(done) {
                window.clearTimeout(timer);
                done();
            }
            try {
                window.LS.addToCartEnhanced(window.jQueryNuvem(form),
                    es ? "Agregar al carrito" : "Adicionar ao carrinho",
                    es ? "Agregando…" : "Adicionando…",
                    es ? "Producto sin stock suficiente." : "Produto sem estoque suficiente.",
                    true, () => finish(resolve), () => finish(reject));
            } catch (error) {
                finish(() => reject(error));
            }
        });
    }

    function consumeUploads(form, fields) {
        for (const field of fields.filter(field => field.fieldType === "FILE_UPLOAD")) {
            const key = "properties[" + (field.propertyName || field.label) + "]";
            const input = Array.from(form.elements).find(element => element.name === key);
            input?.closest(".ncf-field")?.dispatchEvent(new Event("ncf-upload-consumed"));
        }
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
        if (sdkFieldsRendered(fields)) return;
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
            return names.indexOf(element.name) !== -1 || element.name.startsWith("ncf-complement-");
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

        insertBeforePurchase(targetForm, container);
    }

    function insertBeforePurchase(targetForm, container) {
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
        style.textContent += `
            .ncf-complements{min-width:0;border:0;padding:0;margin-inline:0;--ncf-complement-border:color-mix(in srgb,currentColor 25%,transparent)}
            .ncf-complements-title{padding:0;margin-bottom:8px;font-size:16px;line-height:1.3;font-weight:700}
            .ncf-complement{padding:12px;border:1px solid var(--ncf-complement-border);border-radius:12px}
            .ncf-complement+.ncf-complement{margin-top:8px}
            .ncf-complement-selected{border-color:currentColor;background:color-mix(in srgb,currentColor 4%,transparent)}
            .ncf-complement-borderless{border-color:transparent}
            .ncf-complement-choice{display:flex;align-items:center;gap:10px;min-height:44px;margin:0;cursor:pointer}
            .ncf-complement-choice img{width:52px;height:52px;flex:0 0 52px;object-fit:contain;border-radius:8px}
            .ncf-complement-description{display:grid;flex:1;min-width:0;gap:3px;overflow-wrap:anywhere;font-size:13px}
            .ncf-complement-description strong{font-size:14px;line-height:1.35}
            .ncf-complement-choice input{appearance:auto;width:18px;height:18px;margin:0;flex:none;accent-color:currentColor}
            .ncf-complement-details:not([hidden]){display:flex;flex-wrap:wrap;align-items:center;gap:8px 12px;padding-top:10px;margin-top:10px;border-top:1px solid var(--ncf-complement-border);font-size:13px}
            .ncf-complement-details label{margin:0;color:inherit}
            .ncf-complement-controls{display:flex;align-items:center;gap:4px}
            .ncf-complement-controls button,.ncf-complement-controls input{box-sizing:border-box;width:40px;min-height:40px;padding:0;font:inherit;color:inherit;background:transparent;border:1px solid var(--ncf-complement-border);border-radius:8px;text-align:center}
            .ncf-complement-controls button{border-radius:50%;cursor:pointer}
            .ncf-complement-controls button:disabled{opacity:.5;cursor:default}
            .ncf-complement-controls input{appearance:textfield}
            .ncf-complement-controls input::-webkit-inner-spin-button{appearance:none}
            .ncf-complement-details span{font-weight:600;margin-inline-start:auto}
            .ncf-complement-square,.ncf-complement-square img,.ncf-complement-square .ncf-complement-controls>*{border-radius:0}
            .ncf-complements-status{margin:8px 0 0;font-size:14px}
            .ncf-complements-status:empty,.ncf-complements-actions:not(:has(:not([hidden]))){display:none}
            .ncf-complements-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px 16px;margin-top:4px}
            .ncf-complements-actions button{min-height:44px;padding:0 8px;border:0;background:transparent;color:inherit;text-decoration:underline;cursor:pointer}
        `;
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

    function bindProductValidation(targetForm, fields, purchaseComplements) {
        if (!targetForm || targetForm.dataset.ncfValidationBound === "true") {
            return;
        }
        targetForm.dataset.ncfValidationBound = "true";
        let checkingUploads=false,uploadsApproved=false;
        const validateFields = (event) => {
            for (const field of fields) {
                const name = "properties[" + (field.propertyName || field.label) + "]";
                const input = Array.from(targetForm.elements).find(element => element.name === name);
                if (!input) continue;
                if(field.fieldType === "FILE_UPLOAD" && ((field.required && !input.value) || (input.value && !/NCF-FILE:[a-f0-9-]{36}$/.test(input.value)))) {
                    event.preventDefault();event.stopImmediatePropagation();
                    const picker=input.closest(".ncf-field").querySelector("input[type=file]");
                    if(picker) { picker.setCustomValidity("Conclua o envio do arquivo antes de adicionar o produto.");picker.reportValidity();picker.setCustomValidity(""); }
                    return;
                }
                if (field.fieldType === "SELECT" || field.fieldType === "IMAGE_SELECT" || field.fieldType === "COLOR_SELECT") {
                    const invalid = input.value && !(field.options || []).includes(input.value);
                    input.setCustomValidity(invalid
                        ? (document.documentElement.lang.toLowerCase().startsWith("es")
                            ? "Seleccioná una opción válida." : "Selecione uma opção válida.") : "");
                }
                const validationInput = field.fieldType === "QUANTITY_SELECT"
                    ? input.closest(".ncf-field").querySelector("input[type=number]")
                    : field.fieldType === "CHECKBOX" && (field.options || []).length
                        ? input.closest(".ncf-field").querySelector("input[type=checkbox]") : input;
                if (!validationInput.checkValidity()) {
                    event.preventDefault();
                    event.stopImmediatePropagation();
                    validationInput.focus();
                    validationInput.reportValidity();
                    return;
                }
            }
            const uploads=fields.filter(field=>field.fieldType === "FILE_UPLOAD").map(field=>Array.from(targetForm.elements).find(element=>element.name==="properties["+(field.propertyName||field.label)+"]")).filter(input=>input&&input.value);
            if(uploads.length && !uploadsApproved) {
                event.preventDefault();event.stopImmediatePropagation();if(checkingUploads)return;checkingUploads=true;
                const button=event.type === "click" ? event.target.closest("button[type='submit'], input[type='submit'], .js-addtocart, .js-buy-now") : event.submitter;
                Promise.all(uploads.map(input=>{
                    const widget=input.closest(".ncf-field");
                    if(!widget?.ncfValidateUpload)throw new Error("Envio indisponível. Atualize a página e tente novamente.");
                    return widget.ncfValidateUpload();
                })).then(()=>{
                    uploadsApproved=true;
                    try{if(button)button.click();else targetForm.requestSubmit();}
                    finally{setTimeout(()=>{uploadsApproved=false;},0);}
                }).catch(error=>{
                    const notice=uploads[0]?.closest(".ncf-field")?.querySelector('[role="status"]');
                    if(notice)notice.textContent=error.message || "Reenvie o arquivo para continuar.";
                }).finally(()=>{checkingUploads=false;});return;
            }
            if (purchaseComplements?.(event)) return;
            if(event.type === "submit") queueMicrotask(()=>{
                consumeUploads(targetForm, fields);
            });
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
        if (field.fieldType === "FILE_UPLOAD") {
            const host=document.createElement("div");host.className="ncf-field";
            const blocked=document.createElement("input");blocked.name="properties["+(field.propertyName||field.label)+"]";
            blocked.readOnly=true;blocked.required=Boolean(field.required);blocked.setAttribute("aria-label",field.label);
            if(field.required) blocked.setCustomValidity("Aguarde o carregamento do envio de arquivos.");
            const text=document.createElement("p");text.textContent=field.label+" — Carregando envio de arquivo…";host.append(text,blocked);
            const mount=()=>{
                if(!window.ncfBuyerUploadControl || !host.isConnected)return;
                const control=window.ncfBuyerUploadControl(Number(storeId),Number(field.productId || findProductId(findProductForm())),field);
                host.replaceChildren(control.wrapper);
            };
            if(window.ncfBuyerUploadControl) setTimeout(mount,0);
            else {
                window.addEventListener("ncf-buyer-uploads-ready",mount,{once:true});
                if(!document.querySelector("script[data-ncf-buyer-uploads]")) {
                    const loader=document.createElement("script");loader.src=apiOrigin+"/assets/buyer-upload-dom.js";loader.dataset.ncfBuyerUploads="true";
                    loader.onerror=()=>{text.textContent="Envio indisponível. Entre em contato com a loja.";};document.head.append(loader);
                }
            }
            return host;
        }
        if (field.fieldType === "QUANTITY_SELECT") return renderQuantityGroup(field);
        if (field.fieldType === "CHECKBOX" && (field.options || []).length) return renderCheckboxGroup(field);
        const wrapper = document.createElement((field.fieldType === "IMAGE_SELECT" || field.fieldType === "COLOR_SELECT") ? "div" : "label");
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

        if(field.fieldType === "COLOR_SELECT") {
            input.setAttribute('aria-label',field.label);wrapper.appendChild(renderColorChoices(field,input));
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

    function renderColorChoices(field, select) {
        const group=document.createElement('div');group.className='ncf-color-options';
        group.style.cssText='display:flex;flex-wrap:wrap;gap:10px;margin:8px 0';
        const buttons=[];
        for(const option of field.colorOptions ?? []) {
            if(!/^#[0-9a-f]{6}$/i.test(option.color))continue;
            const button=document.createElement('button');button.type='button';button.dataset.value=option.label;
            button.style.cssText='display:flex;align-items:center;gap:8px;min-height:44px;max-width:100%;overflow-wrap:anywhere;border:1px solid currentColor;border-radius:8px;background:transparent;color:inherit;font:inherit;padding:8px 12px;cursor:pointer';
            const chip=document.createElement('span');chip.setAttribute('aria-hidden','true');
            chip.style.cssText='display:inline-block;width:28px;height:28px;flex-shrink:0;border:1px solid #777;border-radius:50%';chip.style.backgroundColor=option.color;
            const name=document.createElement('span');name.textContent=option.label;button.append(chip,name);
            button.addEventListener('click',()=>{select.value=option.label;select.dispatchEvent(new Event('change',{bubbles:true}));});
            buttons.push(button);group.append(button);
        }
        const sync=()=>buttons.forEach(button=>{
            const selected=button.dataset.value===select.value;button.setAttribute('aria-pressed',String(selected));
            button.style.outline=selected?'2px solid currentColor':'';button.style.outlineOffset='2px';
        });
        select.addEventListener('change',sync);sync();return group;
    }

    function renderQuantityGroup(field) {
        const spanish = document.documentElement.lang.toLowerCase().startsWith("es");
        const group=document.createElement("fieldset");group.className="ncf-field ncf-quantity-options";
        group.style.cssText="border:0;padding:0;margin:0 0 12px;min-width:0;color:inherit";
        const legend=document.createElement("legend");legend.textContent=field.label;legend.className="ncf-label";group.append(legend);
        const aggregate=document.createElement("input");aggregate.type="hidden";
        aggregate.name="properties["+(field.propertyName||field.label)+"]";group.append(aggregate);
        const style=document.createElement("style");style.textContent='.ncf-quantity-options{font:inherit;color:inherit}.ncf-quantity-list{display:grid;grid-template-columns:minmax(0,1fr);column-gap:24px;row-gap:0;margin:8px 0}.ncf-quantity-row{display:flex;align-items:center;justify-content:space-between;gap:12px;min-width:0;padding:10px 0;border:0;border-bottom:1px solid #ddd;border-bottom-color:color-mix(in srgb,currentColor 16%,transparent)}.ncf-quantity-name{min-width:0;overflow-wrap:anywhere;line-height:1.4;font-size:14px}.ncf-quantity-stepper{display:flex;align-items:center;justify-content:center;gap:6px;flex-shrink:0;width:110px;min-width:110px;border:0;background:transparent}.ncf-quantity-stepper button{appearance:none;display:flex;align-items:center;justify-content:center;box-sizing:border-box;width:34px;min-width:34px;height:34px;min-height:34px;padding:0;border:1px solid currentColor;border-radius:50%;background:var(--main-background,transparent);color:var(--main-foreground,inherit);font:inherit;font-size:18px;line-height:1;box-shadow:none;cursor:pointer}.ncf-quantity-stepper button:disabled{opacity:.35;cursor:default}.ncf-quantity-stepper input{appearance:textfield;-moz-appearance:textfield;box-sizing:border-box;width:30px;min-width:30px;min-height:34px;margin:0;padding:0;border:0;border-radius:0;background:transparent;color:var(--main-foreground,inherit);font:inherit;font-size:14px;text-align:center;box-shadow:none}.ncf-quantity-stepper input::-webkit-inner-spin-button,.ncf-quantity-stepper input::-webkit-outer-spin-button{-webkit-appearance:none;margin:0}.ncf-quantity-stepper :focus-visible{outline:2px solid currentColor;outline-offset:2px}.ncf-quantity-counter,.ncf-quantity-help{display:block;font-size:13px;line-height:1.5;opacity:.85}@media(min-width:700px){.ncf-quantity-list{grid-template-columns:repeat(var(--ncf-quantity-columns,1),minmax(0,1fr))}}';group.append(style);
        const minimum=Math.max(field.required?1:0,field.minSelections||0),maximum=field.maxSelections;
        const hint=document.createElement("small");hint.className="ncf-quantity-help";
        hint.textContent=maximum!=null?(minimum===maximum?(spanish?`Elegí ${maximum} unidades.`:`Escolha ${maximum} unidades.`):minimum>0?(spanish?`Elegí de ${minimum} a ${maximum} unidades.`:`Escolha de ${minimum} a ${maximum} unidades.`):(spanish?`Elegí hasta ${maximum} unidades.`:`Escolha até ${maximum} unidades.`)):minimum>0?(spanish?`Elegí al menos ${minimum} unidades.`:`Escolha pelo menos ${minimum} unidades.`):"";
        if(hint.textContent)group.append(hint);
        const list=document.createElement("div");list.className="ncf-quantity-list";
        list.style.setProperty("--ncf-quantity-columns",String([2,3].includes(field.quantityColumns)?field.quantityColumns:1));group.append(list);
        const options=[...new Set(field.options || [])];
        const inputs=options.map(option=>{
            const label=document.createElement("div");label.className="ncf-quantity-row";
            const text=document.createElement("span");text.className="ncf-quantity-name";text.textContent=option;
            const input=document.createElement("input");input.type="number";input.min="0";input.step="1";input.value="0";
            input.max=String(field.maxSelections==null?Number.MAX_SAFE_INTEGER:field.maxSelections);
            input.setAttribute("aria-label",option);input.dataset.quantityOption=option;
            const stepper=document.createElement("div");stepper.className="ncf-quantity-stepper";
            const minus=document.createElement("button"),plus=document.createElement("button");minus.type=plus.type="button";
            minus.textContent="−";plus.textContent="+";
            minus.setAttribute("aria-label",(spanish?"Disminuir ":"Diminuir ")+option);
            plus.setAttribute("aria-label",(spanish?"Aumentar ":"Aumentar ")+option);
            minus.addEventListener("click",()=>{input.value=String(Math.max(0,(Number(input.value)||0)-1));sync();});
            plus.addEventListener("click",()=>{input.value=String(Math.max(0,(Number(input.value)||0))+1);sync();});
            stepper.append(minus,input,plus);
            input.addEventListener("input",sync);input.addEventListener("change",sync);
            label.append(text,stepper);list.append(label);return input;
        });
        const counter=document.createElement("small");counter.className="ncf-quantity-counter";counter.setAttribute("aria-live","polite");group.append(counter);
        function sync() {
            const values=inputs.map(input=>input.value===""&&!input.validity.badInput?0:input.valueAsNumber);
            const valid=values.every(value=>Number.isSafeInteger(value)&&value>=0);
            const total=values.reduce((sum,value)=>sum+(Number.isSafeInteger(value)&&value>=0?value:0),0);
            const min=Math.max(field.required?1:0,field.minSelections||0);
            const error=!valid||!Number.isSafeInteger(total)?(spanish?"Ingresá cantidades enteras desde cero.":"Informe quantidades inteiras a partir de zero.")
                :total<min?(spanish?`Elegí al menos ${min} unidades en total.`:`Escolha pelo menos ${min} unidades no total.`)
                :field.maxSelections!=null&&total>field.maxSelections?(spanish?`Elegí como máximo ${field.maxSelections} unidades en total.`:`Escolha no máximo ${field.maxSelections} unidades no total.`):"";
            inputs.forEach(input=>{const buttons=input.parentElement.querySelectorAll("button");
                buttons[0].disabled=!(input.valueAsNumber>0);
                buttons[1].disabled=field.maxSelections!=null&&total>=field.maxSelections;});
            inputs[0]?.setCustomValidity(error);
            aggregate.value=options.flatMap((option,index)=>Number.isSafeInteger(values[index])&&values[index]>0?[`${option} × ${values[index]}`]:[]).join("; ");
            aggregate.disabled=!aggregate.value;
            counter.textContent=(field.maxSelections!=null?`${total} de ${field.maxSelections}`:String(total))+" unidades";
        }
        sync();return group;
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
        const counter = document.createElement("small"); counter.setAttribute("aria-live", "polite");
        if (field.minSelections != null || field.maxSelections != null) group.append(counter);
        function sync() {
            const selected = boxes.filter(box => box.checked).map(box => box.value);
            aggregate.value = selected.join(", "); aggregate.disabled = !selected.length;
            const invalid = selected.some(option => !options.includes(option));
            const spanish = document.documentElement.lang.toLowerCase().startsWith("es");
            const minimum = Math.max(field.required ? 1 : 0, field.minSelections || 0);
            boxes[0].setCustomValidity(invalid ? (spanish ? "Seleccioná una opción válida." : "Selecione uma opção válida.")
                : selected.length < minimum ? (spanish ? `Seleccioná al menos ${minimum} opciones.` : `Selecione pelo menos ${minimum} opções.`)
                : field.maxSelections != null && selected.length > field.maxSelections
                    ? (spanish ? `Seleccioná como máximo ${field.maxSelections} opciones.` : `Selecione no máximo ${field.maxSelections} opções.`) : "");
            counter.textContent = (field.maxSelections != null ? `${selected.length} de ${field.maxSelections}` : String(selected.length))
                + (spanish ? " seleccionadas" : " selecionadas")
                + (field.minSelections ? (spanish ? ` · Seleccioná al menos ${minimum} opciones.` : ` · Selecione pelo menos ${minimum} opções.`) : "");
            boxes.forEach(box => { box.disabled = !box.checked && field.maxSelections != null && selected.length >= field.maxSelections; });
        }
        sync(); return group;
    }

    function createInput(field) {
        if (field.fieldType === "TEXTAREA") {
            return document.createElement("textarea");
        }

        if (field.fieldType === "SELECT" || field.fieldType === "IMAGE_SELECT" || field.fieldType === "COLOR_SELECT") {
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
