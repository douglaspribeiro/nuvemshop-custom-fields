import type { NubeSDK, NubeSDKState } from "@tiendanube/nube-sdk-types";
import { PersonalizationSummary } from "../shared/PersonalizationSummary";
import { appOrigin, safeState, storeId } from "../shared/config";
import { collectItemProperties, normalizedColor } from "../shared/properties";

const SLOT = "after_line_items";

let textColor: string | undefined;

export function App(nube: NubeSDK) {
	const state = safeState(nube);
	render(nube, state);

	loadTextColor(state).then((color) => {
		textColor = color;
		render(nube, safeState(nube) ?? state);
	});

	nube.on("checkout:ready", (nextState) => render(nube, nextState));
	nube.on("cart:update", (nextState) => render(nube, nextState));
}

function render(nube: NubeSDK, state: NubeSDKState | null) {
	const groups = collectItemProperties(state?.cart?.items);
	if (groups.length === 0) {
		nube.clearSlot(SLOT);
		return;
	}
	nube.render(
		SLOT,
		<PersonalizationSummary title="Itens Personalizados" groups={groups} color={textColor} />,
	);
}

async function loadTextColor(state: NubeSDKState | null): Promise<string | undefined> {
	const store = storeId(state);
	if (!store) {
		return undefined;
	}
	try {
		const response = await fetch(`${appOrigin()}/public/stores/${store}/style`, {
			credentials: "omit",
			headers: { Accept: "application/json" },
		});
		if (!response.ok) {
			return undefined;
		}
		const style = (await response.json()) as { checkoutTextColor?: string | null };
		return normalizedColor(style?.checkoutTextColor);
	} catch {
		return undefined;
	}
}
