import type { NubeSDK, NubeSDKState } from "@tiendanube/nube-sdk-types";

export type FieldType = "TEXT" | "NUMBER" | "SELECT" | "TEXTAREA";

/** Espelha br.com.nuvemcustomfields.dto.FieldResponse. */
export type PersonalizationField = {
	label: string;
	fieldType: FieldType;
	required: boolean;
	maxLength: number | null;
	placeholder: string | null;
	validationPattern: string | null;
	propertyName: string;
	options: string[];
};

/** Espelha br.com.nuvemcustomfields.dto.PersonalizationStyleResponse. */
export type PersonalizationStyle = {
	productTextColor: string | null;
	checkoutTextColor: string | null;
	cartTextColor: string | null;
	locale: string;
};

/** Espelha br.com.nuvemcustomfields.dto.PersonalizationResponse. */
export type PersonalizationConfig = {
	enabled: boolean;
	fields: PersonalizationField[];
	style: PersonalizationStyle;
	locale: string;
};

export const DISABLED: PersonalizationConfig = {
	enabled: false,
	fields: [],
	style: { productTextColor: null, checkoutTextColor: null, cartTextColor: null, locale: "pt-BR" },
	locale: "pt-BR",
};

export function appOrigin(): string {
	return __APP_ORIGIN__;
}

export function safeState(nube: NubeSDK): NubeSDKState | null {
	try {
		return nube.getState();
	} catch {
		return null;
	}
}

export function storeId(state: NubeSDKState | null): number | null {
	const id = state?.store?.id;
	return typeof id === "number" && id > 0 ? id : null;
}

/**
 * O productId nao vem no state em nivel "minimal" de forma garantida, entao o
 * chamador resolve e passa; aqui so buscamos a config.
 */
export async function fetchConfig(
	store: number,
	productId: number,
): Promise<PersonalizationConfig> {
	const url = `${appOrigin()}/public/stores/${store}/personalization?productId=${productId}`;
	try {
		const response = await fetch(url, { headers: { Accept: "application/json" } });
		if (!response.ok) {
			return DISABLED;
		}
		const body = (await response.json()) as PersonalizationConfig;
		return body?.enabled ? normalize(body) : DISABLED;
	} catch {
		return DISABLED;
	}
}

function normalize(config: PersonalizationConfig): PersonalizationConfig {
	return {
		enabled: true,
		fields: (config.fields ?? []).filter((field) => !!field?.label),
		style: config.style ?? DISABLED.style,
		locale: config.locale ?? DISABLED.locale,
	};
}

/** Nome do input tal como o carrinho/pedido da Nuvemshop espera. */
export function propertyInputName(field: PersonalizationField): string {
	return `properties[${field.propertyName || field.label}]`;
}
