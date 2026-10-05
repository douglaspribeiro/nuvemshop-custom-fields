import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { NubeSDK, NubeSDKState } from "@tiendanube/nube-sdk-types";
import { DISABLED } from "../shared/config";

type Node = { type?: string; name?: string; children?: Node[]; onClick?: () => void; ariaLabel?: string; onChange?: (event: { value: string }) => void };

describe("SDK storefront without a legacy script", () => {
	let state: NubeSDKState;
	let listeners: Map<string, (state: NubeSDKState) => void>;
	let render: ReturnType<typeof vi.fn>;
	let send: ReturnType<typeof vi.fn>;
	let nube: NubeSDK;
	beforeEach(() => {
		vi.resetModules();
		vi.useFakeTimers();
		// The actual SDK UI factories use this Worker global to generate IDs.
		vi.stubGlobal("self", { __APP_DATA__: { id: "test-storefront" } });
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, json: async () => ({
			...DISABLED, enabled: true,
			fields: [{ label: "Nome", propertyName: "Nome", fieldType: "TEXT", required: true,
				maxLength: 30, placeholder: null, validationPattern: null, options: [] }],
		}) }));
		state = { store: { id: 7278258, theme: "patagonia" }, cart: { items: [] },
			location: { page: { type: "product", data: { product: { id: 341531127, variants: [{ id: 101 }] } } } },
		} as unknown as NubeSDKState;
		listeners = new Map();
		render = vi.fn(); send = vi.fn();
		nube = { getState: () => state, render, send, clearSlot: vi.fn(),
			on: (event: string, callback: (state: NubeSDKState) => void) => listeners.set(event, callback),
		} as unknown as NubeSDK;
	});
	afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });

	async function boot() {
		const { App } = await import("./main");
		App(nube);
		await vi.waitFor(() => expect(render).toHaveBeenCalled());
		expect(send.mock.calls[0][0]).toBe("config:set");
		expect(send.mock.calls[0][1]()).toEqual({ config: { handle_cart_before_update: true } });
		send.mockClear();
	}
	function field(node: Node): Node | undefined {
		if (node.type === "field") return node;
		for (const child of node.children ?? []) {
			const found = field(child);
			if (found) return found;
		}
	}
	function add(requestId = "native", variant = 101) {
		listeners.get("cart:before_update")!({ ...state, eventPayload: { request_id: requestId,
			action: "ADD", item: { product_id: 341531127, variant_id: variant,
				previous_quantity: 0, new_quantity: 2 } },
		} as unknown as NubeSDKState);
	}

	it("renders Patagonia fields inside the Worker without DOM or a transition asset", async () => {
		expect(typeof document).toBe("undefined");
		expect(typeof window).toBe("undefined");
		await boot();
		expect(render.mock.calls[0][0]).toBe("before_product_detail_add_to_cart");
		expect(field(render.mock.calls[0][1])?.name).toBe("Nome");
		expect(vi.mocked(fetch)).toHaveBeenCalledWith(
			"https://app.test/public/stores/7278258/personalization?productId=341531127",
			{ headers: { Accept: "application/json" } });
	});

	it("blocks a native add with an empty required field", async () => {
		await boot();
		add();
		expect(send.mock.calls).toHaveLength(1);
		expect(send.mock.calls[0][0]).toBe("cart:before_update:result");
		expect(send.mock.calls[0][1]()).toEqual({ eventPayload: {
			request_id: "native", proceed: false, reason: "validation_failed" } });
	});

	it("reissues native quantity and variant with properties once and allows its own add", async () => {
		await boot();
		field(render.mock.calls[0][1])!.onChange!({ value: "  Pauli  " });
		add("native", 102);
		expect(send.mock.calls[1][0]).toBe("cart:add");
		expect(send.mock.calls[1][1]()).toEqual({ cart: { items: [{
			product_id: 341531127, variant_id: 102, quantity: 2, properties: { Nome: "Pauli" },
		}] } });
		add("self", 102);
		expect(send.mock.calls.filter(([event]) => event === "cart:add")).toHaveLength(1);
		expect(send.mock.calls[2][1]()).toEqual({ eventPayload: { request_id: "self", proceed: true } });
	});
    it("renders image options, blocks empty required choice and sends selected name", async () => {
        vi.mocked(fetch).mockResolvedValue({ok:true,json:async()=>({...DISABLED,enabled:true,
            fields:[{label:"Capa",propertyName:"Capa",fieldType:"IMAGE_SELECT",required:true,maxLength:100,
                placeholder:null,validationPattern:null,options:["Floral"],imageOptions:[{label:"Floral",thumbnailUrl:"/public/thumb",imageUrl:"/public/large"}]}],
        })} as Response);
        await boot();add();expect(send.mock.calls[0][1]().eventPayload.proceed).toBe(false);
        function button(node:Node):Node|undefined {
            if(node.type==="button" && node.ariaLabel?.includes("Floral"))return node;
            for(const child of node.children ?? []) {const result=button(child);if(result)return result;}
        }
        button(render.mock.calls[render.mock.calls.length-1][1])!.onClick!();send.mockClear();add();
        expect(send.mock.calls[1][1]().cart.items[0].properties).toEqual({Capa:"Floral"});
    });

});
