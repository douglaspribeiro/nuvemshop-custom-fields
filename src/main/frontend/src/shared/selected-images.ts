import type { CartItem } from "@tiendanube/nube-sdk-types";
import { DISABLED, fetchConfig, type PersonalizationConfig } from "./config";
import { normalizeProperties, type NamedProperty } from "./properties";

/** Resolve only the choices persisted on this cart line, never the product form's draft. */
export function selectedCartImages(item: CartItem, config: PersonalizationConfig) {
    if (!config.enabled) return [];
    const properties = normalizeProperties(item.properties);
    return config.fields.flatMap(field => {
        if (field.fieldType !== "IMAGE_SELECT") return [];
        const selected = properties.find(property => property.name === (field.propertyName || field.label));
        const option = field.imageOptions?.find(option => option.label === selected?.value);
        return option ? [{ propertyName: field.propertyName || field.label, fieldLabel: field.label, ...option }] : [];
    });
}

export function imageProperties(item: CartItem, config: PersonalizationConfig): NamedProperty[] {
    const images = selectedCartImages(item, config);
    return normalizeProperties(item.properties).map(property => {
        const image = images.find(image => image.propertyName === property.name && image.label === property.value);
        return image ? { ...property, thumbnailUrl: image.thumbnailUrl } : property;
    });
}

/** Cache by store and product; failed requests are retried on the next update. */
export function createConfigurationLoader(load: typeof fetchConfig = fetchConfig) {
    const cache = new Map<string, { expires: number; config: Promise<PersonalizationConfig> }>();
    return (store: number, product: number) => {
        const key = `${store}:${product}`;
        const cached = cache.get(key);
        if (cached && cached.expires > Date.now()) return cached.config;
        const config = Promise.resolve().then(() => load(store, product)).catch(() => DISABLED);
        cache.set(key, { expires: Date.now() + 60_000, config });
        void config.then(value => {
            if (!value.enabled && cache.get(key)?.config === config) cache.delete(key);
        });
        return config;
    };
}
