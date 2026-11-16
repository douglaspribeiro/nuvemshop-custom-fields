/**
 * Mensagens que o COMPRADOR le. O idioma vem do servidor no payload de config
 * (campo `locale`, derivado do pais da loja em GET /store): o script roda em Web
 * Worker e nao tem DOM nem navigator para inspecionar.
 */
export type Lang = "pt" | "es";

type Dictionary = {
    personalizationPaused: string;
    minSelections: (min: number) => string;
    maxSelections: (max: number) => string;
    selectionCount: (count: number, max?: number | null) => string;
	required: string;
	maxLength: (max: number) => string;
	number: string;
	select: string;
	pattern: string;
	checkoutTitle: string;
};

const PT: Dictionary = {
    minSelections: min => `Selecione pelo menos ${min} opções.`,
    maxSelections: max => `Selecione no máximo ${max} opções.`,
    selectionCount: (count, max) => max != null ? `${count} de ${max} selecionadas` : `${count} selecionadas`,
    personalizationPaused: "A personalização deste produto está temporariamente indisponível. Após a compra, envie as informações de personalização diretamente à loja, informando o número do pedido.",
	required: "Campo obrigatorio.",
	maxLength: (max) => `Use no maximo ${max} caracteres.`,
	number: "Informe um numero valido.",
	select: "Selecione uma opcao valida.",
	pattern: "Formato invalido.",
	checkoutTitle: "Itens Personalizados",
};

const ES: Dictionary = {
    minSelections: min => `Seleccioná al menos ${min} opciones.`,
    maxSelections: max => `Seleccioná como máximo ${max} opciones.`,
    selectionCount: (count, max) => max != null ? `${count} de ${max} seleccionadas` : `${count} seleccionadas`,
    personalizationPaused: "La personalización de este producto está temporalmente no disponible. Después de la compra, envía la información de personalización directamente a la tienda, indicando el número de pedido.",
	required: "Campo obligatorio.",
	maxLength: (max) => `Usá como máximo ${max} caracteres.`,
	number: "Ingresá un número válido.",
	select: "Seleccioná una opción válida.",
	pattern: "Formato inválido.",
	checkoutTitle: "Ítems Personalizados",
};

export function langOf(locale: string | null | undefined): Lang {
	return typeof locale === "string" && locale.toLowerCase().startsWith("pt") ? "pt" : "es";
}

export function messages(locale: string | null | undefined): Dictionary {
	return langOf(locale) === "pt" ? PT : ES;
}
