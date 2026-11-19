import { appOrigin } from "./config";

/** Um beacon best-effort por execução, sem depender de produto ou sessão. */
export function reportScriptExecution(name: "storefront-sdk" | "checkout-sdk" | "patagonia" | "buyer-upload-dom" | "buyer-upload-widget") {
    try {
        const version = typeof __SCRIPT_BUILD_TIME__ === "string" ? __SCRIPT_BUILD_TIME__ : "";
        if (!/^\d{14}$/.test(version) || typeof fetch !== "function") return;
        const url = new URL(`${appOrigin()}/public/script-events`);
        url.searchParams.set("event", "script_execution");
        url.searchParams.set("scriptVersion", `${name}.js?v=${version}`);
        void fetch(url.toString(), { credentials: "omit", keepalive: true }).catch(() => undefined);
    } catch { /* Telemetria nunca deve interromper a compra. */ }
}
