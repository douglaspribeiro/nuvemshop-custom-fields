import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { NubeSDK, NubeSDKState } from "@tiendanube/nube-sdk-types";
import { DISABLED } from "../shared/config";

type Node = { disabled?: boolean; type?: string; name?: string; children?: Node[]; onClick?: () => void; ariaLabel?: string; onChange?: (event: { value: string }) => void; options?: {label: string; value: string}[]; value?: string };

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
		if (node.type === "field" || node.type === "select" || node.type === "check") return node;
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

    function action(node: Node): Node | undefined {
        if(node.type === "button" && String(node.children).includes("Adicionar com personalização")) return node;
        for(const child of node.children ?? []) {const found=action(child);if(found)return found;}
    }
    function quantity(node: Node): Node | undefined {
        if(node.type === "numberfield")return node;
        for(const child of node.children ?? []) {const found=quantity(child);if(found)return found;}
    }
    it("validates and sends through its own Patagonia button without a native gate event",async()=>{
        await boot();
        let tree=render.mock.calls[render.mock.calls.length-1][1] as Node;
        action(tree)!.onClick!();
        expect(send.mock.calls.some(c=>c[0]==="cart:add")).toBe(false);
        tree=render.mock.calls[render.mock.calls.length-1][1];
        field(tree)!.onChange!({value:"Ana"});
        quantity(tree)!.onChange!({value:"3"});
        action(tree)!.onClick!();
        const calls=send.mock.calls.filter(c=>c[0]==="cart:add");
        expect(calls).toHaveLength(1);
        expect(calls[0][1]().cart.items).toEqual([{product_id:341531127,variant_id:101,quantity:3,properties:{Nome:"Ana"}}]);
        expect(send.mock.calls.some(c=>c[0]==="cart:before_update:result")).toBe(false);
        action(tree)!.onClick!();
        expect(send.mock.calls.filter(c=>c[0]==="cart:add")).toHaveLength(1);
    });
    it("rejects an unresolved variant or invalid quantity on its direct button",async()=>{
        await boot();
        let tree=render.mock.calls[render.mock.calls.length-1][1] as Node;
        field(tree)!.onChange!({value:"Ana"});quantity(tree)!.onChange!({value:"1.5"});action(tree)!.onClick!();
        expect(send.mock.calls.some(c=>c[0]==="cart:add")).toBe(false);
        expect(JSON.stringify(render.mock.calls[render.mock.calls.length-1][1])).toContain("quantidade inteira");
    });
    it("does not expose the Patagonia direct action on other themes",async()=>{
        state.store.theme="other";await boot();
        expect(action(render.mock.calls[render.mock.calls.length-1][1])).toBeUndefined();
    });

    it("blocks a different personalization for an existing cart line", async () => {
        await boot();
        state.cart.items = [{product_id:341531127,variant_id:101,properties:{Nome:"Ana"}}] as unknown as typeof state.cart.items;
        field(currentTree())!.onChange!({value:"Bia"});
        action(currentTree())!.onClick!();
        expect(send.mock.calls.some(c => c[0] === "cart:add")).toBe(false);
        expect(JSON.stringify(currentTree())).toContain("outros dados");
    });
    function success(properties: unknown, variant = 101) {
        listeners.get("cart:add:success")!({...state, eventPayload: {
            product_id: 341531127, variant_id: variant, properties,
        }} as unknown as NubeSDKState);
    }
    function currentTree(): Node { return render.mock.calls[render.mock.calls.length - 1][1]; }
    it("preserves filled values after an unrelated native addition", async () => {
        await boot();
        field(currentTree())!.onChange!({value: "Ana"});
        success([]);
        action(currentTree())!.onClick!();
        expect(send.mock.calls.find(c => c[0] === "cart:add")![1]().cart.items[0].properties).toEqual({Nome: "Ana"});
    });
    it("preserves the draft when the SDK acknowledgement omits properties", async () => {
        await boot();
        field(currentTree())!.onChange!({value: "Ana"});
        action(currentTree())!.onClick!();
        success([]);
        send.mockClear();
        action(currentTree())!.onClick!();
        expect(send.mock.calls.find(c => c[0] === "cart:add")![1]().cart.items[0].properties).toEqual({Nome: "Ana"});
    });
    it("preserves edits made during submission and clears only the confirmed draft", async () => {
        await boot();
        field(currentTree())!.onChange!({value: "Ana"});
        action(currentTree())!.onClick!();
        field(currentTree())!.onChange!({value: "Bia"});
        success({Nome: "Ana"});
        send.mockClear();
        action(currentTree())!.onClick!();
        expect(send.mock.calls.find(c => c[0] === "cart:add")![1]().cart.items[0].properties).toEqual({Nome: "Bia"});
        success([{name: "Nome", value: "Bia"}]);
        send.mockClear();
        action(currentTree())!.onClick!();
        expect(send.mock.calls.some(c => c[0] === "cart:add")).toBe(false);
    });


	it("renders Patagonia fields inside the Worker without DOM or a transition asset", async () => {
		expect(typeof document).toBe("undefined");
		expect(typeof window).toBe("undefined");
		await boot();
		expect(render.mock.calls[0][0]).toBe("before_product_detail_add_to_cart");
		expect(field(render.mock.calls[0][1])?.name).toBe("Nome");
		expect(vi.mocked(fetch)).toHaveBeenCalledWith(
			"https://app.test/public/stores/7278258/personalization?productId=341531127",
			expect.objectContaining({ headers: { Accept: "application/json" } }));
	});

	it("shows the custom list prompt, blocks empty choice and sends a real option", async () => {
        vi.mocked(fetch).mockResolvedValue({ ok: true, json: async () => ({ ...DISABLED, enabled: true,
            fields: [{ label: "Moagem", propertyName: "Moagem", fieldType: "SELECT", required: false,
                maxLength: null, placeholder: "Escolha a moagem", validationPattern: null, options: ["Grano", "Molido"] }] }) } as Response);
        await boot();
        const select = field(render.mock.calls[0][1])!;
        expect(select.options?.[0]).toEqual({label: "Escolha a moagem", value: ""});
        expect(select.value).toBe("");
        add();
        expect(send.mock.calls[0][1]().eventPayload.proceed).toBe(false);
        select.onChange!({value: "Molido"});
        expect(field(render.mock.calls[render.mock.calls.length - 1][1])!.value).toBe("Molido");
        send.mockClear();
        add();
        expect(send.mock.calls[1][1]().cart.items[0].properties).toEqual({Moagem: "Molido"});
    });

    it.each(['pt-BR', 'es-AR'])('renders and submits controlled checkbox in %s', async (locale) => {
        vi.mocked(fetch).mockResolvedValue({ok:true, json:async()=>({...DISABLED, enabled:true, locale,
            fields:[{label:'Confirmo', propertyName:'Confirmo', fieldType:'CHECKBOX',required:true,options:[]}]})} as Response);
        await boot();
        const checkbox = () => field(render.mock.calls[render.mock.calls.length - 1][1])!;
        expect(checkbox().type).toBe('check');
        expect((checkbox() as unknown as {checked:boolean}).checked).toBe(false);
        add(); expect(send.mock.calls[0][1]().eventPayload.proceed).toBe(false);
        (checkbox().onChange as unknown as (e:{value:boolean})=>void)({value:true});
        expect((checkbox() as unknown as {checked:boolean}).checked).toBe(true);
        send.mockClear(); add();
        expect(send.mock.calls[1][1]().cart.items[0].properties).toEqual({Confirmo:locale==='pt-BR'?'Sim':'Sí'});
        listeners.get('cart:add:fail')!(state);
        (checkbox().onChange as unknown as (e:{value:boolean})=>void)({value:false});
        send.mockClear(); add();
        expect(send.mock.calls[0][1]().eventPayload.proceed).toBe(false);
        (checkbox().onChange as unknown as (e:{value:boolean})=>void)({value:true});
        send.mockClear(); add();
        success({Confirmo: locale === 'pt-BR' ? 'Sim' : 'Sí'});
        expect((checkbox() as unknown as {checked:boolean}).checked).toBe(false);
    });

    it.each([true, false])('supports multiple SDK choices with required=%s', async (required) => {
        vi.mocked(fetch).mockResolvedValue({ok:true,json:async()=>({...DISABLED,enabled:true,
            fields:[{label:'Adicionais',fieldType:'CHECKBOX',required,options:['Laço','Cartão','Embalagem']}]})} as Response);
        await boot();
        type Check = {type?:string;children?:Check[];checked?:boolean;onChange?:(e:{value:boolean})=>void};
        const checks = () => {
            const collect = (node:Check):Check[] => node.type === 'check' ? [node] : (Array.isArray(node.children) ? node.children : []).flatMap(collect);
            return collect(render.mock.calls[render.mock.calls.length-1][1]);
        };
        expect(checks().map(box=>box.checked)).toEqual([false,false,false]);
        add();expect(send.mock.calls[0][1]().eventPayload.proceed).toBe(!required);
        checks()[0].onChange!({value:true}); checks()[2].onChange!({value:true});
        expect(checks().map(box=>box.checked)).toEqual([true,false,true]);
        send.mockClear();add();
        expect(send.mock.calls[1][1]().cart.items[0].properties).toEqual({Adicionais:'Laço, Embalagem'});
        listeners.get('cart:add:fail')!(state);checks()[0].onChange!({value:false});
        send.mockClear();add();
        expect(send.mock.calls[1][1]().cart.items[0].properties).toEqual({Adicionais:'Embalagem'});
        success({Adicionais: 'Embalagem'});
        expect(checks().map(box=>box.checked)).toEqual([false,false,false]);
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
    it.each([
        {showImageZoom:undefined,mobileImageColumns:undefined},
        {showImageZoom:true,mobileImageColumns:2},
        {showImageZoom:false,mobileImageColumns:3},
    ])("renders SDK image options with %o and sends selected name", async ({showImageZoom,mobileImageColumns}) => {
        vi.mocked(fetch).mockResolvedValue({ok:true,json:async()=>({...DISABLED,enabled:true,
            fields:[{label:"Capa",propertyName:"Capa",fieldType:"IMAGE_SELECT",required:true,maxLength:100,showImageZoom,mobileImageColumns,
                placeholder:null,validationPattern:null,options:["Floral"],imageOptions:[{label:"Floral",thumbnailUrl:"/public/thumb",imageUrl:"/public/large"}]}],
        })} as Response);
        await boot();add();expect(send.mock.calls[0][1]().eventPayload.proceed).toBe(false);
        expect(JSON.stringify(render.mock.calls[render.mock.calls.length-1][1]).includes("Ampliar imagem"))
            .toBe(showImageZoom !== false);
        const tree=JSON.stringify(render.mock.calls[render.mock.calls.length-1][1]);
        expect(tree.includes("@media (max-width:767px)")).toBe(!!mobileImageColumns);
        if (mobileImageColumns) expect(tree).toContain(`repeat(${mobileImageColumns},minmax(0,1fr))`);
        function button(node:Node):Node|undefined {
            if(node.type==="button" && node.ariaLabel?.includes("Floral"))return node;
            for(const child of node.children ?? []) {const result=button(child);if(result)return result;}
        }
        button(render.mock.calls[render.mock.calls.length-1][1])!.onClick!();send.mockClear();add();
        expect(send.mock.calls[1][1]().cart.items[0].properties).toEqual({Capa:"Floral"});
    });

    it("updates cart thumbnails without changing native cart properties", async () => {
        await boot();
        vi.mocked(fetch).mockResolvedValue({ok:true,json:async()=>({...DISABLED,enabled:true,
            fields:[{label:"Capa",propertyName:"Capa",fieldType:"IMAGE_SELECT",required:true,maxLength:100,
                placeholder:null,validationPattern:null,options:["Floral"],imageOptions:[{label:"Floral",thumbnailUrl:"/public/thumb",imageUrl:"/public/large"}]}],
        })} as Response);
        const cartState={...state,cart:{items:[{id:101,product_id:341531127,properties:{Capa:"Floral"}}]}} as unknown as NubeSDKState;
        listeners.get("cart:update")!(cartState);
        await vi.waitFor(()=>expect(render.mock.calls.some(([slot])=>slot==="before_line_item")).toBe(true));
        const nodes=render.mock.calls.find(([slot])=>slot==="before_line_item")![1];
        expect(nodes[0].key).toBe(101);
        expect(JSON.stringify(nodes)).toContain("https://app.test/public/thumb");
        expect(send).not.toHaveBeenCalled();
        listeners.get("cart:update")!({...state,cart:{items:[]}} as unknown as NubeSDKState);
        expect(nube.clearSlot).toHaveBeenCalledWith("before_line_item");
    });

});
