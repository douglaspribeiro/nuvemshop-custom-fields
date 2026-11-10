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
    it("shows the paused notice without adding fields or blocking native purchase", async () => {
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ok:true,json:async()=>({...DISABLED,paused:true,locale:"pt-BR"})}));
        await boot();
        const tree=render.mock.calls[render.mock.calls.length-1][1];
        expect(JSON.stringify(tree)).toContain("diretamente à loja");
        expect(JSON.stringify(tree)).not.toContain('"type":"field"');
        listeners.get("cart:before_update")?.({...state,cart:{...state.cart,items:[]}} as NubeSDKState);
        expect(send.mock.calls.some(([event])=>event==="cart:add")).toBe(false);
    });

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
    function variantSelect(node: Node | Node[]): Node | undefined {
        if (Array.isArray(node)) {
            for (const child of node) { const found = variantSelect(child); if (found) return found; }
            return undefined;
        }
        if (node.type === "select" && node.name?.startsWith("ncf-variant-")) return node;
        for (const child of node.children ?? []) { const found = variantSelect(child as Node | Node[]); if (found) return found; }
    }
    it.each(["TEXT", "NUMBER", "TEXTAREA"])("keeps %s typing and quantity edits in the mounted form, including after validation", async fieldType => {
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ok:true,json:async()=>({...DISABLED,enabled:true,
            fields:[{label:"Nome",propertyName:"Nome",fieldType,required:true,options:[]}],
        })}));
        await boot();
        action(render.mock.calls.slice(-1)[0]![1])!.onClick!();
        const tree = render.mock.calls.slice(-1)[0]![1] as Node;
        function input(node: Node): Node | undefined {
            if (node.name === "Nome") return node;
            for (const child of node.children ?? []) { const found=input(child); if(found) return found; }
        }
        render.mockClear();
        for (const value of ["1", "12", "123"]) input(tree)!.onChange!({value});
        quantity(tree)!.onChange!({value:"12"});
        expect(render).not.toHaveBeenCalled();
        action(tree)!.onClick!();
        expect(send.mock.calls.filter(([event])=>event==="cart:add").slice(-1)[0]![1]().cart.items[0])
            .toMatchObject({quantity:12,properties:{Nome:"123"}});
    });
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
    it("controls the Patagonia variant itself when the theme omits its selected-variant event", async () => {
        const page = state.location.page;
        if (page?.type === "product") page.data.product.variants = [
            {id: 101, position: 2, values: [{es: "Una"}, {es: "Flores"}]},
            {id: 102, position: 3, values: [{es: "Dos"}, {es: "Flores"}]},
        ] as typeof page.data.product.variants;
        if (page?.type === "product") page.data.product.attributes = [{es: "Mascotas"}, {es: "Diseño"}] as typeof page.data.product.attributes;
        await boot();
        let tree = currentTree();
        field(tree)!.onChange!({value: "Ana"});
        expect(JSON.stringify(tree)).toContain("ncf-variant-0");
        variantSelect(tree)!.onChange!({value: "Dos"});
        tree = currentTree();
        action(tree)!.onClick!();
        expect(send.mock.calls.find(c => c[0] === "cart:add")![1]().cart.items[0]).toMatchObject({variant_id: 102, properties: {Nome: "Ana"}});
    });

    it("sends different personalization without modifying an existing cart line", async () => {
        await boot();
        state.cart.items = [{product_id:341531127,variant_id:101,properties:{Nome:"Ana"}}] as unknown as typeof state.cart.items;
        field(currentTree())!.onChange!({value:"Bia"});
        action(currentTree())!.onClick!();
        expect(send.mock.calls.find(c => c[0] === "cart:add")![1]().cart.items[0]).toMatchObject({variant_id:101,properties:{Nome:"Bia"}});
        expect(state.cart.items[0].properties).toEqual({Nome:"Ana"});
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

    it.each([true,false])("revalidates uploaded artwork on the server before cart add, ready=%s",async ready=>{
        const reference="arte.png (10 bytes) — NCF-FILE:00000000-0000-4000-8000-000000000001";
        const storage={getItem:vi.fn(async(key:string)=>key.startsWith("ncf-upload-session")?"a".repeat(64):reference),setItem:vi.fn(async()=>{})};
        Object.assign(nube,{getBrowserAPIs:()=>({asyncSessionStorage:storage,resetForm:vi.fn()})});
        let valid=true;
        vi.mocked(fetch).mockImplementation(async url=>String(url).includes("/personalization?")
            ? {ok:true,json:async()=>({...DISABLED,enabled:true,fields:[{id:11,label:"Arte",propertyName:"_ncf_file_11",fieldType:"FILE_UPLOAD",required:true,maxLength:null,options:["PNG"]}]})} as Response
            : {ok:valid,json:async()=>valid?{ready:true}:{error:"Arquivo expirado.",code:"upload.expired"}} as Response);
        await boot();
        await vi.waitFor(()=>expect(render.mock.calls.length).toBeGreaterThan(1));
        valid=ready;send.mockClear();add();
        expect(send.mock.calls[0][1]().eventPayload.proceed).toBe(false);
        if(ready){
            await vi.waitFor(()=>expect(send.mock.calls.some(call=>call[0]==="cart:add")).toBe(true));
            const payload=send.mock.calls.find(call=>call[0]==="cart:add")![1]();
            expect(payload.cart.items[0].properties).toEqual({_ncf_file_11:reference,Arte:"arte.png (10 bytes)"});
        }else{
            await vi.waitFor(()=>expect(JSON.stringify(render.mock.calls[render.mock.calls.length-1])).toContain("Arquivo expirado."));
            expect(send.mock.calls.some(call=>call[0]==="cart:add")).toBe(false);
        }
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

    it("sends 1 En Grano and 1 Molido from quick buy, then 5 Molido from the PDP", async()=>{
        await boot();
        const product={id:341531127,canonical_url:'/produtos/cafe/',variants:[{id:101}]};
        state={...state,location:{url:'https://store.test/',page:{type:'home',data:{sections:[]}}}} as unknown as NubeSDKState;
        listeners.get('location:updated')!(state);
        const quickTree=()=>render.mock.calls.filter(([slot])=>slot==='before_quick_buy_add_to_cart').slice(-1)[0]?.[1];
        listeners.get('quickbuy:open')!({...state,eventPayload:product} as unknown as NubeSDKState);
        await vi.waitFor(()=>expect(quickTree()).toBeDefined());
        const first={id:11,product_id:341531127,variant_id:101,quantity:1,name:'Café',properties:{Nome:'En Grano'}};
        field(quickTree())!.onChange!({value:'En Grano'});action(quickTree())!.onClick!();
        // The SDK can omit the variant on the echo outside the product page.
        listeners.get('cart:before_update')!({...state,eventPayload:{request_id:'echo-grano',action:'ADD',item:{product_id:341531127,variant_id:null,previous_quantity:0,new_quantity:1}}} as unknown as NubeSDKState);
        state={...state,cart:{items:[first]}} as unknown as NubeSDKState;
        listeners.get('cart:add:success')!({...state,eventPayload:first} as unknown as NubeSDKState);
        const second={...first,id:22,properties:{Nome:'Molido'}};
        field(quickTree())!.onChange!({value:'Molido'});action(quickTree())!.onClick!();
        listeners.get('cart:before_update')!({...state,eventPayload:{request_id:'echo-molido',action:'ADD',item:{product_id:341531127,variant_id:null,previous_quantity:1,new_quantity:2}}} as unknown as NubeSDKState);
        state={...state,cart:{items:[first,second]}} as unknown as NubeSDKState;
        listeners.get('cart:add:success')!({...state,eventPayload:second} as unknown as NubeSDKState);
        const count=render.mock.calls.filter(([slot])=>slot==='before_product_detail_add_to_cart').length;
        state={...state,location:{url:'https://store.test/produtos/cafe/',page:{type:'product',data:{product}}}} as unknown as NubeSDKState;
        listeners.get('location:updated')!(state);
        await vi.waitFor(()=>expect(render.mock.calls.filter(([slot])=>slot==='before_product_detail_add_to_cart').length).toBeGreaterThan(count));
        const pdpTree=()=>render.mock.calls.filter(([slot])=>slot==='before_product_detail_add_to_cart').slice(-1)[0][1];
        field(pdpTree())!.onChange!({value:'Molido'});quantity(pdpTree())!.onChange!({value:'5'});action(pdpTree())!.onClick!();
        const items=send.mock.calls.filter(([event])=>event==='cart:add').map(([,fn])=>fn().cart.items[0]);
        expect(items).toEqual([
            {product_id:341531127,variant_id:101,quantity:1,properties:{Nome:'En Grano'}},
            {product_id:341531127,variant_id:101,quantity:1,properties:{Nome:'Molido'}},
            {product_id:341531127,variant_id:101,quantity:5,properties:{Nome:'Molido'}},
        ]);
        expect(state.cart.items[0]).toEqual(first);
    });

});
