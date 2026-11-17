import type { PersonalizationField } from "../shared/config";
import { messages } from "../shared/i18n";

export type QuantityMap = Record<string, number>;
export function quantityMap(raw: unknown): QuantityMap | null {
    if (raw === undefined || raw === "") return {};
    if (!raw || typeof raw !== "object" || Array.isArray(raw)) return null;
    return raw as QuantityMap;
}
export function quantityTotal(raw: unknown): number {
    const map = quantityMap(raw);
    return Object.values(map ?? {}).filter(value => Number.isSafeInteger(value) && value >= 0)
        .reduce((total, value) => total + value, 0);
}
export function quantityError(field: PersonalizationField, raw: unknown, locale?: string | null): string | null {
    const text = messages(locale), map = quantityMap(raw);
    if (!map || Object.entries(map).some(([option, value]) => !field.options.includes(option)
            || !Number.isSafeInteger(value) || value < 0)) return text.quantityInvalid;
    const total = quantityTotal(map);
    if (!Number.isSafeInteger(total)) return text.quantityInvalid;
    const min = Math.max(field.required ? 1 : 0, field.minSelections ?? 0);
    if (total < min) return text.quantityMin(min);
    if (field.maxSelections != null && total > field.maxSelections) return text.quantityMax(field.maxSelections);
    return null;
}
export function quantityText(field: PersonalizationField, raw: unknown): string {
    const map = quantityMap(raw);
    return [...new Set(field.options)].filter(option => map && Object.prototype.hasOwnProperty.call(map, option)
        && Number.isSafeInteger(map[option]) && map[option] > 0)
        .map(option => `${option} × ${map![option]}`).join("; ");
}

export function quantityColumns(field: PersonalizationField): 1 | 2 | 3 {
    return field.quantityColumns === 2 || field.quantityColumns === 3 ? field.quantityColumns : 1;
}
