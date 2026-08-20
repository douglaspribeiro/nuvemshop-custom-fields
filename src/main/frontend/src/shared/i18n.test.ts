import { describe, expect, it } from "vitest";
import { langOf, messages } from "./i18n";
import { validate } from "../storefront/values";
import type { PersonalizationField } from "./config";

const field = (overrides: Partial<PersonalizationField> = {}): PersonalizationField => ({
	label: "Nome",
	fieldType: "TEXT",
	required: true,
	maxLength: null,
	placeholder: null,
	validationPattern: null,
	propertyName: "nome",
	options: [],
	...overrides,
});

describe("langOf", () => {
	it("trata apenas pt como portugues", () => {
		expect(langOf("pt-BR")).toBe("pt");
		expect(langOf("pt")).toBe("pt");
		expect(langOf("PT-br")).toBe("pt");
	});

	// Loja fora do Brasil e o caso comum na Latam: espanhol e o fallback, nao portugues.
	it("cai em espanhol para qualquer outro locale", () => {
		expect(langOf("es")).toBe("es");
		expect(langOf("es-AR")).toBe("es");
		expect(langOf(null)).toBe("es");
		expect(langOf(undefined)).toBe("es");
	});
});

describe("validate", () => {
	it("usa espanhol quando a loja e da Latam", () => {
		const errors = validate([field()], {}, "es");
		expect(errors[0].message).toBe("Campo obligatorio.");
	});

	it("usa portugues quando a loja e brasileira", () => {
		const errors = validate([field()], {}, "pt-BR");
		expect(errors[0].message).toBe("Campo obrigatorio.");
	});

	it("localiza todas as mensagens de validacao", () => {
		const es = messages("es");
		expect(validate([field({ maxLength: 3 })], { nome: "abcdef" }, "es")[0].message)
			.toBe(es.maxLength(3));
		expect(validate([field({ fieldType: "NUMBER" })], { nome: "abc" }, "es")[0].message)
			.toBe(es.number);
		expect(validate([field({ fieldType: "SELECT", options: ["A"] })], { nome: "B" }, "es")[0].message)
			.toBe(es.select);
		expect(validate([field({ validationPattern: "^[0-9]+$" })], { nome: "abc" }, "es")[0].message)
			.toBe(es.pattern);
	});

	it("nao deixa nenhuma mensagem em portugues quando o locale e es", () => {
		const pt = messages("pt-BR");
		const portugueseMessages = [pt.required, pt.number, pt.select, pt.pattern, pt.maxLength(3)];
		const produced = [
			...validate([field()], {}, "es"),
			...validate([field({ maxLength: 3 })], { nome: "abcdef" }, "es"),
			...validate([field({ fieldType: "NUMBER" })], { nome: "abc" }, "es"),
			...validate([field({ fieldType: "SELECT", options: ["A"] })], { nome: "B" }, "es"),
			...validate([field({ validationPattern: "^[0-9]+$" })], { nome: "abc" }, "es"),
		].map((error) => error.message);

		expect(produced.filter((message) => portugueseMessages.includes(message))).toEqual([]);
	});
});
