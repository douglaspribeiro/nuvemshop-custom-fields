// @vitest-environment jsdom
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { startPatagonia } from "./patagonia";

vi.mock("./patagonia", () => ({ startPatagonia: vi.fn() }));
const page = window as typeof window & { nubeSDK?: unknown; ncfPatagoniaStarted?: boolean };
// Assinatura observada no runtime da loja, que conserva a propriedade queue.
function send(_source: string, _event: string, _update: unknown, _options?: unknown) {}

beforeEach(() => { vi.resetModules(); vi.clearAllMocks(); vi.useFakeTimers(); });
afterEach(() => {
	delete page.nubeSDK; delete page.ncfPatagoniaStarted;
	vi.clearAllTimers(); vi.useRealTimers();
});

it("starts with the real runtime even when its bootstrap queue property remains", async () => {
	page.nubeSDK = { queue: [], send };
	await import("./patagonia-entry");
	expect(startPatagonia).toHaveBeenCalledOnce();
	expect(page.ncfPatagoniaStarted).toBe(true);
});

it("waits for the bootstrap methods to be replaced and starts only once", async () => {
	page.nubeSDK = { queue: [], send: (..._args: unknown[]) => {} };
	await import("./patagonia-entry");
	expect(startPatagonia).not.toHaveBeenCalled();
	page.nubeSDK = { queue: [], send };
	vi.advanceTimersByTime(500);
	expect(startPatagonia).toHaveBeenCalledOnce();
	vi.advanceTimersByTime(5000);
	expect(startPatagonia).toHaveBeenCalledOnce();
});
