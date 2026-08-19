import type { CartItem } from "@tiendanube/nube-sdk-types";

export type NamedProperty = { name: string; value: string };

export type ItemProperties = { productName: string; fields: NamedProperty[] };

/**
 * `CartItem.properties` chega como objeto ou array dependendo do tema/versao da
 * plataforma, entao normalizamos as duas formas.
 */
export function normalizeProperties(properties: unknown): NamedProperty[] {
	if (!properties) {
		return [];
	}
	if (Array.isArray(properties)) {
		return properties.map(normalizeProperty).filter(isNamedProperty);
	}
	if (typeof properties === "object") {
		return Object.entries(properties as Record<string, unknown>)
			.map(([name, value]) => normalizeProperty({ name, value }))
			.filter(isNamedProperty);
	}
	return [];
}

export function collectItemProperties(items: CartItem[] | undefined): ItemProperties[] {
	if (!Array.isArray(items)) {
		return [];
	}
	return items
		.map((item) => ({
			productName: item?.name || "Produto",
			fields: normalizeProperties(item?.properties),
		}))
		.filter((group) => group.fields.length > 0);
}

function normalizeProperty(property: unknown): NamedProperty | null {
	if (!property || typeof property !== "object") {
		return null;
	}
	const candidate = property as Record<string, unknown>;
	const name = String(candidate.name ?? candidate.label ?? candidate.key ?? "").trim();
	const value = String(candidate.value ?? candidate.text ?? "").trim();
	// Underscore e a convencao da Nuvemshop para propriedade interna/oculta.
	if (!name || !value || name.startsWith("_")) {
		return null;
	}
	return { name, value };
}

function isNamedProperty(property: NamedProperty | null): property is NamedProperty {
	return property !== null;
}

export function normalizedColor(value: string | null | undefined): string | undefined {
	return /^#[0-9A-Fa-f]{6}$/.test(String(value ?? "")) ? (value as string) : undefined;
}
