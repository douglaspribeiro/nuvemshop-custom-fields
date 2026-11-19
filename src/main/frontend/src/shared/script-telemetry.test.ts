import { afterEach, expect, it, vi } from "vitest";
import { reportScriptExecution } from "./script-telemetry";

afterEach(() => vi.unstubAllGlobals());

it("sends the generated checkout version without blocking the checkout", () => {
    vi.stubGlobal("__APP_ORIGIN__", "https://app.test");
    vi.stubGlobal("__SCRIPT_BUILD_TIME__", "20261119092200");
    const fetchMock = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal("fetch", fetchMock);
    reportScriptExecution("checkout-sdk");
    const url = new URL(fetchMock.mock.calls[0][0]);
    expect(url.searchParams.get("event")).toBe("script_execution");
    expect(url.searchParams.get("scriptVersion")).toBe("checkout-sdk.js?v=20261119092200");
    expect(() => { vi.stubGlobal("fetch", () => { throw new Error("offline"); }); reportScriptExecution("checkout-sdk"); }).not.toThrow();
});
