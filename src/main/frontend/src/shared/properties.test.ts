import { describe, expect, it } from "vitest";
import type { CartItem } from "@tiendanube/nube-sdk-types";
import { collectItemProperties, normalizeProperties, normalizedColor } from "./properties";

describe("normalizeProperties", () => {
	it("aceita o formato objeto", () => {
		expect(normalizeProperties({ Nome: "Ana", Numero: "10" })).toEqual([
			{ name: "Nome", value: "Ana" },
			{ name: "Numero", value: "10" },
		]);
	});

	it("aceita o formato array com name/value", () => {
		expect(normalizeProperties([{ name: "Nome", value: "Ana" }])).toEqual([
			{ name: "Nome", value: "Ana" },
		]);
	});

	it("descarta vazios e propriedades internas com underscore", () => {
		expect(normalizeProperties({ Nome: "", _interna: "x", Numero: "10" })).toEqual([
			{ name: "Numero", value: "10" },
		]);
	});

	it("retorna vazio para entradas invalidas", () => {
		expect(normalizeProperties(null)).toEqual([]);
		expect(normalizeProperties(undefined)).toEqual([]);
		expect(normalizeProperties("texto")).toEqual([]);
	});
});

describe("collectItemProperties", () => {
	it("ignora itens sem personalizacao", () => {
		const items = [
			{ name: "Caneca", properties: { Nome: "Ana" } },
			{ name: "Camiseta", properties: {} },
		] as unknown as CartItem[];

		expect(collectItemProperties(items)).toEqual([
			{ productName: "Caneca", fields: [{ name: "Nome", value: "Ana" }] },
		]);
	});

	it("usa fallback de nome do produto", () => {
		const items = [{ properties: { Nome: "Ana" } }] as unknown as CartItem[];
		expect(collectItemProperties(items)[0].productName).toBe("Produto");
	});
});

describe("normalizedColor", () => {
	it("aceita apenas hex de 6 digitos", () => {
		expect(normalizedColor("#1a2B3c")).toBe("#1a2B3c");
		expect(normalizedColor("#123")).toBeUndefined();
		expect(normalizedColor("red")).toBeUndefined();
		expect(normalizedColor(null)).toBeUndefined();
	});
});
