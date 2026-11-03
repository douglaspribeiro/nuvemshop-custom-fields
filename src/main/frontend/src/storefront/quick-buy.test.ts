import { afterEach, beforeEach, expect, it, vi } from "vitest";
import type { NubeSDK, NubeSDKState } from "@tiendanube/nube-sdk-types";
import { DISABLED } from "../shared/config";
import { installQuickBuyRedirect } from "./quick-buy";

let state: NubeSDKState;
let listeners: Map<string, (state: NubeSDKState) => void>;
let navigate: ReturnType<typeof vi.fn>;
let send: ReturnType<typeof vi.fn>;
let render: ReturnType<typeof vi.fn>;
let fetchPolicy: ReturnType<typeof vi.fn>;
let nube: NubeSDK;
const configured = {...DISABLED, enabled: true, fields: [{label: "Nome", propertyName: "Nome", fieldType: "TEXT", required: true, options: []}]};

beforeEach(() => {
    vi.stubGlobal("self", {__APP_DATA__: {id: "quick-buy-test"}});
    state = {store: {id: 123, language: "pt", theme: "patagonia"}, cart: {items: []},
        location: {url: "https://store.test/", page: {type: "home", data: {sections: [{type: "featured_products", products: [
            {id: 456, canonical_url: "https://store.test/produtos/cafe/"},
        ]}]}}}} as unknown as NubeSDKState;
    listeners = new Map(); navigate = vi.fn(); send = vi.fn(); render = vi.fn();
    fetchPolicy = vi.fn().mockResolvedValue({ok: true, json: async () => configured});
    vi.stubGlobal("fetch", fetchPolicy);
    nube = {getState: () => state, getBrowserAPIs: () => ({navigate}), send, render, clearSlot: vi.fn(),
        on: (event: string, callback: (state: NubeSDKState) => void) => listeners.set(event, callback),
    } as unknown as NubeSDK;
});
afterEach(() => vi.unstubAllGlobals());

function add(id = 456, action = "ADD", previous = 0) {
    return {...state, eventPayload: {request_id: "request-1", action,
        item: {product_id: id, variant_id: 789, previous_quantity: previous}}} as unknown as NubeSDKState;
}
function answer() { return send.mock.calls.find(([event]) => event === "cart:before_update:result")?.[1]().eventPayload; }

it.each([123, 959276, 5656805])("redirects the SDK quick-buy modal for store %s", async id => {
    state.store.id = id;
    installQuickBuyRedirect(nube);
    listeners.get("quickbuy:open")!({...state, eventPayload: {id: 456, canonical_url: "https://store.test/produtos/cafe/"}});
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith("/produtos/cafe/"));
    expect(fetchPolicy.mock.calls[0][0]).toContain(`/stores/${id}/personalization?productId=456`);
    expect(send).not.toHaveBeenCalled();
});

it("cancels direct grid additions before redirecting, without needing a modal", async () => {
    const quickBuy = installQuickBuyRedirect(nube);
    expect(quickBuy.intercept(add())).toBe(true);
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith("/produtos/cafe/"));
    expect(answer()).toMatchObject({request_id: "request-1", proceed: false});
    expect(send.mock.calls.some(([event]) => event === "cart:add")).toBe(false);
});

it("keeps quick buy available for products without fields", async () => {
    fetchPolicy.mockResolvedValue({ok: true, json: async () => DISABLED});
    const quickBuy = installQuickBuyRedirect(nube);
    listeners.get("quickbuy:open")!({...state, eventPayload: {id: 456, canonical_url: "/produtos/cafe/"}});
    quickBuy.intercept(add());
    await vi.waitFor(() => expect(answer()?.proceed).toBe(true));
    expect(navigate).not.toHaveBeenCalled(); expect(fetchPolicy).toHaveBeenCalledTimes(1);
});

it("leaves the current PDP purchase and removals to their existing handlers", () => {
    state.location.page = {type: "product", data: {product: {id: 456}}} as NubeSDKState["location"]["page"];
    const quickBuy = installQuickBuyRedirect(nube);
    expect(quickBuy.intercept(add())).toBe(false);
    expect(quickBuy.intercept(add(999, "REMOVE"))).toBe(false);
    expect(fetchPolicy).not.toHaveBeenCalled();
});

it("uses a recommendation's own URL instead of the current PDP", async () => {
    state.location.url = "https://store.test/produtos/atual/";
    state.location.page = {type: "product", data: {product: {id: 111}, sections: [{type: "related_products", products: [
        {id: 456, canonical_url: "/produtos/cafe/"},
    ]}]}} as NubeSDKState["location"]["page"];
    installQuickBuyRedirect(nube).intercept(add());
    await vi.waitFor(() => expect(navigate).toHaveBeenCalledWith("/produtos/cafe/"));
});

it("allows increasing an existing correctly personalized cart line", async () => {
    state.cart.items = [{product_id: 456, variant_id: 789, properties: {Nome: "Ana"}}] as unknown as NubeSDKState["cart"]["items"];
    installQuickBuyRedirect(nube).intercept(add(456, "ADD", 1));
    await vi.waitFor(() => expect(answer()?.proceed).toBe(true));
    expect(navigate).not.toHaveBeenCalled();
});

it("redirects an existing line whose required personalization is missing", async () => {
    state.cart.items = [{product_id: 456, variant_id: 789, properties: {}}] as unknown as NubeSDKState["cart"]["items"];
    installQuickBuyRedirect(nube).intercept(add(456, "ADD", 1));
    await vi.waitFor(() => expect(navigate).toHaveBeenCalled());
    expect(answer()?.proceed).toBe(false);
});

it("cancels safely if the policy lookup fails", async () => {
    fetchPolicy.mockRejectedValue(new Error("offline"));
    installQuickBuyRedirect(nube).intercept(add());
    await vi.waitFor(() => expect(answer()).toMatchObject({proceed: false, reason: "personalization_policy_unavailable"}));
    expect(navigate).toHaveBeenCalledWith("/produtos/cafe/");
});

it("shows an instruction when the theme supplies no product URL", async () => {
    state.location.page = {type: "home", data: {}} as NubeSDKState["location"]["page"];
    installQuickBuyRedirect(nube).intercept(add());
    await vi.waitFor(() => expect(render).toHaveBeenCalledWith("before_main_content", expect.anything()));
    expect(answer()?.proceed).toBe(false); expect(navigate).not.toHaveBeenCalled();
});

it("does not navigate after the customer closes the quick-buy modal", async () => {
    let resolve!: (value: unknown) => void;
    fetchPolicy.mockReturnValue(new Promise(done => {resolve = done;}));
    installQuickBuyRedirect(nube);
    listeners.get("quickbuy:open")!({...state, eventPayload: {id: 456, canonical_url: "/produtos/cafe/"}});
    listeners.get("quickbuy:close")!(state);
    resolve({ok: true, json: async () => configured});
    await new Promise(done => setTimeout(done, 0));
    expect(navigate).not.toHaveBeenCalled();
});

it("discards late responses after a page change", async () => {
    let resolve!: (value: unknown) => void;
    fetchPolicy.mockReturnValue(new Promise(done => {resolve = done;}));
    const quickBuy = installQuickBuyRedirect(nube);
    quickBuy.intercept(add());
    state = {...state, location: {...state.location, url: "https://store.test/contato/"}};
    quickBuy.locationChanged();
    resolve({ok: true, json: async () => configured});
    await vi.waitFor(() => expect(answer()).toMatchObject({proceed: false, reason: "quick_buy_context_changed"}));
    expect(navigate).not.toHaveBeenCalled();
});
