import type { NubeSDK, NubeSDKState } from "@tiendanube/nube-sdk-types";
import { messages } from "../shared/i18n";
import { PersonalizationSummary } from "../shared/PersonalizationSummary";
import { createConfigurationLoader, imageProperties } from "../shared/selected-images";
import { appOrigin, safeState, storeId } from "../shared/config";
import { collectItemProperties, normalizedColor, normalizeProperties, type ItemProperties } from "../shared/properties";

const SLOT = "after_line_items";

let textColor: string | undefined;
let locale: string | null = null;

export function App(nube: NubeSDK) {
    const configFor = createConfigurationLoader();
    let latestState = safeState(nube);
    let generation = 0;
    const refresh = async (state: NubeSDKState | null) => {
        latestState = state;
        const current = ++generation;
        render(nube, state);
        const store = storeId(state);
        const items = (state?.cart?.items ?? []).filter(item => normalizeProperties(item?.properties).length > 0);
        if (!store || !items.length) return;
        const groups = await Promise.all(items.map(async item => ({
            productName: item.name || "Produto",
            fields: item.product_id > 0 ? imageProperties(item, await configFor(store, item.product_id)) : normalizeProperties(item.properties),
        })));
        if (current !== generation) return;
        renderGroups(nube, groups);
    };
    const state = latestState;
	void refresh(state);

	loadStyle(state).then((style) => {
		textColor = style.color;
		locale = style.locale;
		void refresh(latestState);
	});

	nube.on("checkout:ready", (nextState) => void refresh(nextState));
	nube.on("cart:update", (nextState) => void refresh(nextState));
}

function render(nube: NubeSDK, state: NubeSDKState | null) {
	renderGroups(nube, collectItemProperties(state?.cart?.items));
}

function renderGroups(nube: NubeSDK, groups: ItemProperties[]) {
	if (groups.length === 0) {
		nube.clearSlot(SLOT);
		return;
	}
	nube.render(
		SLOT,
		<PersonalizationSummary title={messages(locale).checkoutTitle} groups={groups} color={textColor} />,
	);
}

type StyleResult = { color: string | undefined; locale: string | null };

const NO_STYLE: StyleResult = { color: undefined, locale: null };

async function loadStyle(state: NubeSDKState | null): Promise<StyleResult> {
	const store = storeId(state);
	if (!store) {
		return NO_STYLE;
	}
	try {
		const response = await fetch(`${appOrigin()}/public/stores/${store}/style`, {
			credentials: "omit",
			headers: { Accept: "application/json" },
		});
		if (!response.ok) {
			return NO_STYLE;
		}
		const style = (await response.json()) as {
			checkoutTextColor?: string | null;
			locale?: string | null;
		};
		return { color: normalizedColor(style?.checkoutTextColor), locale: style?.locale ?? null };
	} catch {
		return NO_STYLE;
	}
}
