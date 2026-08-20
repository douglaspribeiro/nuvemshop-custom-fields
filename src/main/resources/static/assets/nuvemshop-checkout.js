// ATENCAO: arquivo em producao, mas NAO e mais a fonte de verdade.
// A versao mantida e src/main/frontend/src/checkout/main.tsx, que gera
// /assets/nuvemshop-checkout-sdk.js. Este arquivo existe apenas porque o script
// cadastrado no Partner Portal ainda aponta para ele.
//
// Nao edite aqui: altere o .tsx e, quando for promover, reenvie o bundle novo no
// Portal e apague este arquivo. Editar so este arquivo faz as duas versoes divergirem.

const APP_ORIGIN = "https://campos-personalizados.wzhub.pro";

let styleConfig = {};

export function App(nube) {
    const state = safeState(nube);
    renderCustomFields(nube, state);
    loadStyle(state).then((style) => {
        styleConfig = style || {};
        renderCustomFields(nube, safeState(nube) || state);
    });

    nube.on("checkout:ready", (nextState) => {
        renderCustomFields(nube, nextState);
        loadStyle(nextState).then((style) => {
            styleConfig = style || {};
            renderCustomFields(nube, nextState);
        });
    });

    nube.on("cart:update", (nextState) => {
        renderCustomFields(nube, nextState);
    });
}

function safeState(nube) {
    try {
        return nube.getState();
    } catch (error) {
        return null;
    }
}

function loadStyle(state) {
    const storeId = storeIdFromState(state) || storeIdFromLocation();
    if (!storeId || typeof fetch !== "function") {
        return Promise.resolve({});
    }
    return fetch(APP_ORIGIN + "/public/stores/" + encodeURIComponent(storeId) + "/style", {
        credentials: "omit",
        headers: {
            "Accept": "application/json"
        }
    })
        .then((response) => response.ok ? response.json() : {})
        .catch(() => ({}));
}

function storeIdFromState(state) {
    const store = state && state.store ? state.store : {};
    const candidates = [
        store.id,
        store.storeId,
        store.store_id,
        state && state.storeId,
        state && state.store_id
    ];
    const value = candidates.find(Boolean);
    return value ? String(value) : null;
}

function storeIdFromLocation() {
    try {
        return new URL(globalThis.location.href).searchParams.get("store");
    } catch (error) {
        return null;
    }
}

function renderCustomFields(nube, state) {
    const groups = collectCustomFieldGroups(state && state.cart ? state.cart.items : []);
    if (groups.length === 0) {
        nube.clearSlot("after_line_items");
        return;
    }

    nube.send("ui:slot:set", () => ({
        ui: {
            slots: {
                after_line_items: customFieldsBlock(groups)
            }
        }
    }));
}

function collectCustomFieldGroups(items) {
    if (!Array.isArray(items)) {
        return [];
    }

    return items
        .map((item) => ({
            productName: item.name || item.productName || "Produto",
            fields: normalizeProperties(item.properties)
        }))
        .filter((group) => group.fields.length > 0);
}

function normalizeProperties(properties) {
    if (!properties) {
        return [];
    }

    if (Array.isArray(properties)) {
        return properties
            .map((property) => normalizeProperty(property))
            .filter(Boolean);
    }

    if (typeof properties === "object") {
        return Object.entries(properties)
            .map(([name, value]) => normalizeProperty({ name, value }))
            .filter(Boolean);
    }

    return [];
}

function normalizeProperty(property) {
    if (!property || typeof property !== "object") {
        return null;
    }

    const name = String(property.name || property.label || property.key || "").trim();
    const value = String(property.value || property.text || "").trim();
    if (!name || !value || name.charAt(0) === "_") {
        return null;
    }

    return { name, value };
}

/**
 * Idioma vem do endpoint /style (derivado do pais da loja em GET /store): o script roda
 * no checkout de qualquer pais em que o app for instalado.
 */
function checkoutTitle() {
    const locale = styleConfig.locale;
    const portuguese = typeof locale === "string" && locale.toLowerCase().indexOf("pt") === 0;
    return portuguese ? "Itens Personalizados" : "\u00cdtems Personalizados";
}

function customFieldsBlock(groups) {
    const color = normalizedColor(styleConfig.checkoutTextColor);
    return {
        type: "col",
        gap: 0,
        padding: "16px 14px",
        margin: "10px 0 0",
        borderRadius: "6px",
        style: {
            border: "1px solid rgba(0,0,0,.12)",
            backgroundColor: "rgba(0,0,0,.025)"
        },
        children: [
            textNode(checkoutTitle(), color, ["bold"], {
                fontSize: "17px",
                textAlign: "center",
                marginBottom: "14px"
            }),
            ...groups.map((group) => customFieldGroup(group, color))
        ]
    };
}

function customFieldGroup(group, color) {
    return {
        type: "col",
        gap: 0,
        padding: "14px 0 12px",
        style: {
            borderTop: "1px solid rgba(0,0,0,.08)"
        },
        children: [
            textNode(group.productName, color, ["bold"], {
                fontSize: "16px",
                marginBottom: "8px"
            }),
            {
                type: "col",
                gap: 0,
                children: group.fields.map((field) => {
                    return textNode(field.name + ": " + field.value, color, ["bold"], {
                        fontSize: "14px",
                        lineHeight: "18px",
                        margin: "0 0 2px"
                    });
                })
            }
        ]
    };
}

function textNode(children, color, modifiers, style) {
    const node = {
        type: "txt",
        children: children
    };
    if (color) {
        node.color = color;
    }
    if (Array.isArray(modifiers) && modifiers.length > 0) {
        node.modifiers = modifiers;
    }
    if (style) {
        node.style = style;
    }
    return node;
}

function normalizedColor(value) {
    return /^#[0-9A-Fa-f]{6}$/.test(String(value || "")) ? value : null;
}
