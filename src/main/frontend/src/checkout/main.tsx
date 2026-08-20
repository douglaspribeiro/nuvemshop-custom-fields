import type { NubeSDK, NubeSDKState } from "@tiendanube/nube-sdk-types";
import { messages } from "../shared/i18n";
import { PersonalizationSummary } from "../shared/PersonalizationSummary";
import { appOrigin, safeState, storeId } from "../shared/config";
import { collectItemProperties, normalizedColor } from "../shared/properties";

const SLOT = "after_line_items";

let textColor: string | undefined;
let locale: string | null = null;

export function App(nube: NubeSDK) {
	const state = safeState(nube);
	render(nube, state);

	loadStyle(state).then((style) => {
		textColor = style.color;
		locale = style.locale;
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
