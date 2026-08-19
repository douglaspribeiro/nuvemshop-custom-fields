import { describe, expect, it } from "vitest";
import type { PersonalizationField } from "../shared/config";
import { hasAnyValue, keyOf, toCartProperties, validate } from "./values";

function field(overrides: Partial<PersonalizationField> = {}): PersonalizationField {
	return {
		label: "Nome",
		fieldType: "TEXT",
		required: false,
		maxLength: null,
		placeholder: null,
		validationPattern: null,
		propertyName: "Nome",
		options: [],
		...overrides,
	};
}

describe("keyOf", () => {
	it("cai no label quando propertyName vem vazio", () => {
		expect(keyOf(field({ propertyName: "" }))).toBe("Nome");
	});
});

describe("validate", () => {
	it("aceita campo opcional vazio", () => {
		expect(validate([field()], {})).toEqual([]);
	});

	it("reprova obrigatorio vazio ou so espacos", () => {
		expect(validate([field({ required: true })], {})).toHaveLength(1);
		expect(validate([field({ required: true })], { Nome: "   " })).toHaveLength(1);
	});

	it("reprova acima do maxLength", () => {
		expect(validate([field({ maxLength: 3 })], { Nome: "Ana" })).toEqual([]);
		expect(validate([field({ maxLength: 3 })], { Nome: "Anna" })).toHaveLength(1);
	});

	it("valida NUMBER", () => {
		const numeric = field({ fieldType: "NUMBER", propertyName: "Numero" });
		expect(validate([numeric], { Numero: "10" })).toEqual([]);
		expect(validate([numeric], { Numero: "10,5" })).toEqual([]);
		expect(validate([numeric], { Numero: "dez" })).toHaveLength(1);
	});

	it("valida SELECT contra as opcoes", () => {
		const select = field({ fieldType: "SELECT", options: ["P", "M", "G"] });
		expect(validate([select], { Nome: "M" })).toEqual([]);
		expect(validate([select], { Nome: "XG" })).toHaveLength(1);
	});

	it("aplica regex do lojista", () => {
		const masked = field({ validationPattern: "^[A-Z]{3}$" });
		expect(validate([masked], { Nome: "ABC" })).toEqual([]);
		expect(validate([masked], { Nome: "abc" })).toHaveLength(1);
	});

	it("ignora regex invalida em vez de bloquear a compra", () => {
		const broken = field({ required: true, validationPattern: "[" });
		expect(validate([broken], { Nome: "qualquer" })).toEqual([]);
	});
});

describe("toCartProperties", () => {
	it("monta objeto, nao array, e descarta vazios", () => {
		const fields = [field(), field({ label: "Numero", propertyName: "Numero" })];
		const properties = toCartProperties(fields, { Nome: " Ana ", Numero: "" });

		expect(Array.isArray(properties)).toBe(false);
		expect(properties).toEqual({ Nome: "Ana" });
	});

	it("hasAnyValue reflete o resultado", () => {
		expect(hasAnyValue({})).toBe(false);
		expect(hasAnyValue({ Nome: "Ana" })).toBe(true);
	});
});
