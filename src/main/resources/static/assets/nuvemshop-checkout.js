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

function customFieldsBlock(groups) {
    const color = normalizedColor(styleConfig.checkoutTextColor);
    return {
        type: "col",
        gap: 8,
        padding: "16px 14px",
        margin: "10px 0 0",
        borderRadius: "6px",
        style: {
            border: "1px solid rgba(0,0,0,.12)",
            backgroundColor: "rgba(0,0,0,.025)"
        },
        children: [
            textNode("Itens Personalizados", color, ["bold"], {
                fontSize: "17px",
                textAlign: "center",
                marginBottom: "6px"
            }),
            ...groups.map((group) => customFieldGroup(group, color))
        ]
    };
}

function customFieldGroup(group, color) {
    return {
        type: "col",
        gap: 4,
        padding: "10px 0 0",
        style: {
            borderTop: "1px solid rgba(0,0,0,.08)"
        },
        children: [
            textNode(group.productName, color, ["bold"], {
                fontSize: "16px",
                marginBottom: "4px"
            }),
            {
                type: "col",
                gap: 1,
                children: group.fields.map((field) => {
                    return textNode(field.name + ": " + field.value, color, ["bold"], {
                        fontSize: "14px",
                        lineHeight: "18px"
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
