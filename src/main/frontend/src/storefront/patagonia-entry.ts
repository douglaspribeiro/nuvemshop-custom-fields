import { startPatagonia, type StorefrontRuntime } from "./patagonia";
import { appOrigin, fetchConfig } from "../shared/config";

const page = window as typeof window & { nubeSDK?: StorefrontRuntime; ncfPatagoniaStarted?: boolean };
// O script legado pode executar antes do runtime. O timer não depende de interação extra.
let attempts = 0;
const boot = () => {
	if (page.ncfPatagoniaStarted) return;
	// O bootstrap usa send(...args); o runtime mantém queue, mas substitui send
	// pela assinatura (source, event, update, options). Esperamos essa substituição.
	if (page.nubeSDK && page.nubeSDK.send.length >= 3) {
		page.ncfPatagoniaStarted = true;
		startPatagonia(page.nubeSDK, fetchConfig, (reason, productId) => {
			const url = new URL(`${appOrigin()}/public/script-events`);
			url.searchParams.set("event", "storefront_sdk");
			url.searchParams.set("reason", reason);
			url.searchParams.set("storeId", String(page.nubeSDK!.getState().store.id));
			if (productId) url.searchParams.set("productId", String(productId));
			void fetch(url, { credentials: "omit" }).catch(() => undefined);
		});
	} else if (++attempts < 120) {
		setTimeout(boot, 500);
	}
};
boot();
