import { Column } from "@tiendanube/nube-sdk-jsx";
import type { CartItem, NubeSDK, NubeSDKState } from "@tiendanube/nube-sdk-types";
import { fetchConfig, storeId } from "../shared/config";
import { normalizeProperties, normalizedColor } from "../shared/properties";
import { createConfigurationLoader, imageProperties } from "../shared/selected-images";
import { PersonalizationValues } from "../shared/PersonalizationValues";

const SLOT = "before_line_item";

type Loader = typeof fetchConfig;

export { selectedCartImages } from "../shared/selected-images";

/** The repeated slot is keyed by cart line ID, so variants and distinct choices stay separate. */
export function createCartImageRenderer(nube: NubeSDK, load: Loader = fetchConfig) {
    const configFor = createConfigurationLoader(load);
    let generation = 0;

    return async (state: NubeSDKState | null) => {
        const current = ++generation;
        const store = storeId(state);
        const items = (state?.cart?.items ?? []).filter(item =>
            item?.id != null && item.product_id > 0 && normalizeProperties(item.properties).length > 0);
        if (!store || items.length === 0) {
            nube.clearSlot(SLOT);
            return;
        }
        try {
            const lines = await Promise.all(items.map(async item => {
                const config = await configFor(store, item.product_id);
                return { item, config, fields: imageProperties(item, config) };
            }));
            // An older request must not resurrect a removed item or another cart's selection.
            if (current !== generation) return;
            const visible = lines.filter(line => line.fields.length > 0);
            if (visible.length === 0) {
                nube.clearSlot(SLOT);
                return;
            }
            nube.render(SLOT, visible.map(({item, config, fields}) =>
                <Column key={item.id} id={`ncf-cart-personalizations-${item.id}`} gap={6}
                    style={{ paddingTop: "8px", paddingBottom: "8px" }}>
                    <PersonalizationValues fields={fields} color={normalizedColor(config.style?.cartTextColor)} />
                </Column>));
        } catch {
            // Image previews are optional; a configuration outage must never interrupt checkout.
            if (current === generation) nube.clearSlot(SLOT);
        }
    };
}
