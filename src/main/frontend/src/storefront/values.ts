import type { PersonalizationField } from "../shared/config";

export type ValueMap = Record<string, string>;

export type FieldError = { propertyName: string; label: string; message: string };

/** Chave usada tanto no `name` do componente quanto no `properties[...]` enviado. */
export function keyOf(field: PersonalizationField): string {
	return field.propertyName || field.label;
}

export function validate(fields: PersonalizationField[], values: ValueMap): FieldError[] {
	const errors: FieldError[] = [];
	for (const field of fields) {
		const key = keyOf(field);
		const value = (values[key] ?? "").trim();

		if (field.required && !value) {
			errors.push({ propertyName: key, label: field.label, message: "Campo obrigatorio." });
			continue;
		}
		if (!value) {
			continue;
		}
		if (field.maxLength != null && value.length > field.maxLength) {
			errors.push({
				propertyName: key,
				label: field.label,
				message: `Use no maximo ${field.maxLength} caracteres.`,
			});
			continue;
		}
		if (field.fieldType === "NUMBER" && !/^-?\d+([.,]\d+)?$/.test(value)) {
			errors.push({ propertyName: key, label: field.label, message: "Informe um numero valido." });
			continue;
		}
		if (field.fieldType === "SELECT" && field.options.length > 0 && !field.options.includes(value)) {
			errors.push({ propertyName: key, label: field.label, message: "Selecione uma opcao valida." });
			continue;
		}
		if (field.validationPattern) {
			const error = patternError(field, value);
			if (error) {
				errors.push(error);
			}
		}
	}
	return errors;
}

/**
 * Regex vem do banco e e editada pelo lojista, entao um padrao invalido nao pode
 * derrubar o script nem bloquear a compra: tratamos como "sem validacao".
 */
function patternError(field: PersonalizationField, value: string): FieldError | null {
	let pattern: RegExp;
	try {
		pattern = new RegExp(field.validationPattern as string);
	} catch {
		return null;
	}
	if (pattern.test(value)) {
		return null;
	}
	return {
		propertyName: keyOf(field),
		label: field.label,
		message: "Formato invalido.",
	};
}

/**
 * Monta o payload de `properties` do `cart:add`. Precisa ser objeto, nunca array:
 * o bridge da loja faz `Object.entries(...)` para gerar `properties[chave]`.
 */
export function toCartProperties(
	fields: PersonalizationField[],
	values: ValueMap,
): Record<string, string> {
	const properties: Record<string, string> = {};
	for (const field of fields) {
		const key = keyOf(field);
		const value = (values[key] ?? "").trim();
		if (value) {
			properties[key] = value;
		}
	}
	return properties;
}

export function hasAnyValue(properties: Record<string, string>): boolean {
	return Object.keys(properties).length > 0;
}
