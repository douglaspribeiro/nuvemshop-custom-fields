const CUSTOM_FIELD_LABELS = new Set([
    "Nome na Camisa",
    "Numero na Camisa",
    "Número na Camisa"
]);

export function App(nube) {
    renderCustomFields(nube, safeState(nube));

    nube.on("checkout:ready", (state) => {
        renderCustomFields(nube, state);
    });

    nube.on("cart:update", (state) => {
        renderCustomFields(nube, state);
    });
}

function safeState(nube) {
    try {
        return nube.getState();
    } catch (error) {
        return null;
    }
}

function renderCustomFields(nube, state) {
    const fields = collectCustomFields(state && state.cart ? state.cart.items : []);
    if (fields.length === 0) {
        nube.clearSlot("after_line_items");
        return;
    }

    nube.send("ui:slot:set", () => ({
        ui: {
            slots: {
                after_line_items: customFieldsBlock(fields)
            }
        }
    }));
}

function collectCustomFields(items) {
    if (!Array.isArray(items)) {
        return [];
    }

    return items.flatMap((item) => {
        return normalizeProperties(item && item.properties).map((property) => ({
            productName: item.name || "Produto",
            name: property.name,
            value: property.value
        }));
    });
}

function normalizeProperties(properties) {
    if (!properties) {
        return [];
    }

    if (Array.isArray(properties)) {
        return properties
            .map((property) => normalizeProperty(property))
            .filter(Boolean)
            .filter(isCustomField);
    }

    if (typeof properties === "object") {
        return Object.entries(properties)
            .map(([name, value]) => normalizeProperty({ name, value }))
            .filter(Boolean)
            .filter(isCustomField);
    }

    return [];
}

function normalizeProperty(property) {
    if (!property || typeof property !== "object") {
        return null;
    }

    const name = String(property.name || property.label || property.key || "").trim();
    const value = String(property.value || property.text || "").trim();
    if (!name || !value) {
        return null;
    }

    return { name, value };
}

function isCustomField(property) {
    return CUSTOM_FIELD_LABELS.has(property.name);
}

function customFieldsBlock(fields) {
    return {
        type: "col",
        gap: 8,
        padding: "12px",
        borderRadius: "6px",
        style: {
            border: "1px solid rgba(0,0,0,.12)"
        },
        children: [
            {
                type: "txt",
                modifiers: ["bold"],
                children: "Campos personalizados"
            },
            ...fields.map(customFieldLine)
        ]
    };
}

function customFieldLine(field) {
    return {
        type: "col",
        gap: 2,
        children: [
            {
                type: "txt",
                children: field.productName
            },
            {
                type: "txt",
                color: "#f00",
                modifiers: ["bold"],
                children: field.name + ": " + field.value
            }
        ]
    };
}
