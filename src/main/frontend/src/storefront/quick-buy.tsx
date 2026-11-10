import { Button, Column, NumberField, Select, Text } from "@tiendanube/nube-sdk-jsx";
import type { NubeSDK, NubeSDKState } from "@tiendanube/nube-sdk-types";
import { fetchConfigRequired, safeState, storeId, type PersonalizationConfig } from "../shared/config";
import { normalizeProperties, normalizedColor } from "../shared/properties";
import { PersonalizationFields } from "./PersonalizationFields";
import { validate, toCartProperties, type ValueMap, type FieldError } from "./values";
import { createCartAdder, type CartMutation } from "./cart-add";

type Product = { id: number; name?: string | Record<string,string>; canonical_url?: string; variants?: {id: number; values?: unknown[]}[] };
type Draft = { slot: typeof SLOT | "modal_content"; product: Product; config: PersonalizationConfig; values: ValueMap; errors: FieldError[];
    variant: number | null; quantity: number; message: string; pending: boolean; generation: number };
const SLOT = "before_quick_buy_add_to_cart";

/** Native quick-buy form; themes without its slot keep the product-page fallback. */
export function installQuickBuy(nube: NubeSDK, adder = createCartAdder(nube)) {
    const policies = new Map<string, ReturnType<typeof fetchConfigRequired>>();
    const drafts = new Map<number, ValueMap>();
    let active: Product | null = null;
    let draft: Draft | null = null;
    let generation = 0;
    let navigated: string | null = null;
    let catalogGeneration = 0;
    let submission: {product: number; variant: number; values: string; generation: number; properties: Record<string,string>} | null = null;
    let submitTimer: ReturnType<typeof setTimeout> | undefined;

    function policy(store: number, product: number) {
        const key = `${store}:${product}`;
        if (!policies.has(key)) {
            const pending = fetchConfigRequired(store, product, 3500);
            policies.set(key, pending);
            void pending.catch(() => { policies.delete(key); });
        }
        return policies.get(key)!;
    }
    function route(state: NubeSDKState, id: number): `/${string}` | null {
        const data = state.location?.page?.data as {product?: Product; products?: Product[]; sections?: {products?: Product[]}[]} | undefined;
        const candidates = [active, data?.product, ...(data?.products ?? []), ...(data?.sections ?? []).flatMap(section => section.products ?? [])];
        const canonical = candidates.find(product => product?.id === id && product.canonical_url)?.canonical_url
            ?? state.cart?.items?.find(item => item.product_id === id)?.url;
        if (!canonical) return null;
        try {
            const base = new URL(state.location.url), url = new URL(canonical, base);
            return url.origin === base.origin && url.pathname !== base.pathname ? `${url.pathname}${url.search}${url.hash}` as `/${string}` : null;
        } catch { return null; }
    }
    function redirect(state: NubeSDKState, id: number) {
        const path = route(state, id);
        if (path) {
            if (navigated !== path) { navigated = path; nube.getBrowserAPIs().navigate(path); }
        } else nube.render("before_main_content", <Text>{state.store?.language?.startsWith("es")
            ? "Abrí la página del producto para completar la personalización antes de agregarlo al carrito."
            : "Abra a página do produto para preencher a personalização antes de adicionar ao carrinho."}</Text>);
    }
    async function supportsSlot(state: NubeSDKState) {
        // Patagonia has this documented slot, but does not implement slot discovery.
        if (state.store?.theme?.trim().toLowerCase() === "patagonia") return true;
        try {
            const slots = await nube.api.getAvailableSlots().getStatic();
            return slots.some(slot => slot.slotId === SLOT);
        } catch { return false; }
    }
    function render() {
        const current = draft;
        if (!current) return;
        const es = current.config.locale.startsWith("es");
        const variants = current.product.variants ?? [];
        const localized = (value: unknown): string => {
            if (typeof value === "string") return value;
            if (!value || typeof value !== "object") return "";
            const names = value as Record<string,string>;
            return names[current.config.locale] || names[es ? "es" : "pt"] || Object.values(names)[0] || "";
        };
        nube.render(current.slot, <Column gap={8} width={current.slot === "modal_content" ? "420px" : undefined}
            style={current.slot === "modal_content" ? {maxWidth:"90vw",backgroundColor:"#fff",color:"#111",paddingTop:"20px",paddingBottom:"20px",paddingLeft:"20px",paddingRight:"20px"} : undefined}>
            {current.slot === "modal_content" ? <Text modifiers={["bold"]}>{typeof current.product.name === "string" ? current.product.name : current.product.name?.[es ? "es" : "pt"] || ""}</Text> : <Column gap={0} />}
            <PersonalizationFields markerId="ncf-sdk-quickbuy-fields" namePrefix={`ncf-quickbuy-${current.product.id}-`} fields={current.config.fields} errors={current.errors}
                color={normalizedColor(current.config.style?.productTextColor)} values={current.values} locale={current.config.locale}
                onValueChange={(key,value,refresh = true) => {
                    if (draft !== current || current.pending) return;
                    current.values[key] = value; drafts.set(current.product.id, {...current.values});
                    current.errors = current.errors.filter(error => error.propertyName !== key);
                    if (refresh) render();
                }} />
            {variants.length > 1 ? <Select key="ncf-quickbuy-variant" name="ncf-quickbuy-variant" label={es ? "Variante" : "Variação"}
                value={current.variant == null ? "" : String(current.variant)} disabled={current.pending}
                options={[{label: es ? "Seleccioná una variante" : "Selecione uma variação", value:""},
                    ...variants.map(variant => ({label:(variant.values ?? []).map(localized).join(" / ") || String(variant.id),value:String(variant.id)}))]}
                onChange={event => {if(draft === current && !current.pending) {current.variant=Number(event.value)||null;render();}}} /> : <Column gap={0} />}
            <NumberField key="ncf-quickbuy-quantity" name="ncf-quickbuy-quantity" label={es ? "Cantidad" : "Quantidade"} min={1} step={1}
                value={current.quantity} disabled={current.pending} onChange={event => {if(draft === current && !current.pending){current.quantity=Number(event.value);}}} />
            <Button disabled={current.pending} onClick={() => {
                if(draft !== current || current.pending)return;
                submit(current, current.variant, current.quantity);
            }}>{current.pending ? (es ? "Agregando…" : "Adicionando…") : (es ? "Agregar con personalización" : "Adicionar com personalização")}</Button>
            <Text>{current.message}</Text>
            {current.slot === "modal_content" ? <Button variant="link" onClick={()=>{if(draft === current)close();}}>{es ? "Cerrar" : "Fechar"}</Button> : <Column gap={0} />}
        </Column>);
    }
    function submit(current: Draft, variant: number | null, quantity: number) {
        if(submission) {
            current.message=current.config.locale.startsWith("es") ? "Esperá la confirmación del carrito antes de agregar otro producto." : "Aguarde a confirmação do carrinho antes de adicionar outro produto.";
            render();return;
        }
        current.errors = validate(current.config.fields, current.values, current.config.locale);
        const es = current.config.locale.startsWith("es");
        if(current.errors.length) {render();return;}
        if(!variant || !current.product.variants?.some(v=>v.id===variant) || !Number.isSafeInteger(quantity) || quantity < 1) {
            current.message=es ? "Seleccioná una variante y una cantidad entera mayor que cero." : "Selecione uma variação e uma quantidade inteira maior que zero.";
            render();return;
        }
        const properties=toCartProperties(current.config.fields,current.values);
        current.pending=true;current.message="";
        submission={product:current.product.id,variant,values:JSON.stringify(current.values),generation:current.generation,properties};
        render();
        clearTimeout(submitTimer);
        submitTimer=setTimeout(()=>{
            submission=null;
            if(draft !== current || !current.pending)return;
            current.pending=false;
            current.message=es ? "No pudimos confirmar la inclusión. Revisá el carrito antes de intentar nuevamente." : "Não foi possível confirmar a inclusão. Confira o carrinho antes de tentar novamente.";
            render();
        },15000);
        adder.add({product_id:current.product.id,variant_id:variant,quantity,properties});
    }
    nube.on("quickbuy:open", state => {
        const product=state.eventPayload as Product | undefined;
        active=typeof product?.id === "number" && product.id > 0 ? {...product,variants:Array.isArray(product.variants) ? product.variants.filter(v=>Number.isSafeInteger(v?.id) && v.id > 0) : []} : null;
        draft=null;nube.clearSlot(SLOT);
        const ownGeneration=++generation, shop=storeId(state), selected=active;
        if(!shop || !selected)return;
        void Promise.all([policy(shop,selected.id),supportsSlot(state)]).then(([config,supported])=>{
            if(generation !== ownGeneration || safeState(nube)?.location.url !== state.location.url)return;
            if(!config.enabled || !config.fields.length)return;
            if(!supported || !selected.variants?.length){redirect(state,selected.id);return;}
            draft={slot:SLOT,product:selected,config,values:{...drafts.get(selected.id)},errors:[],
                variant:selected.variants?.length===1 ? selected.variants[0].id : null,quantity:1,message:"",pending:false,generation:ownGeneration};
            render();
        },()=>{if(generation===ownGeneration && safeState(nube)?.location.url===state.location.url)redirect(state,selected.id);});
    });
    function close() {const modal=draft?.slot === "modal_content";active=null;draft=null;generation++;nube.clearSlot(SLOT);if(modal)nube.clearSlot("modal_content");}
    nube.on("custom:modal:close",()=>{if(draft?.slot === "modal_content")close();});
    nube.on("quickbuy:close",close);
    function locationChanged() {close();catalogGeneration++;nube.clearSlot("before_product_grid_item_name");navigated=null;policies.clear();drafts.clear();nube.clearSlot("before_main_content");}
    function intercept(state: NubeSDKState | null): boolean {
        if(!state)return false;
        const payload=state.eventPayload as CartMutation | undefined, id=payload?.item?.product_id, shop=storeId(state);
        if(!payload?.request_id || payload.action!=="ADD" || !id || !shop)return false;
        const answer=(proceed:boolean,reason?:string)=>nube.send("cart:before_update:result",()=>({eventPayload:{request_id:payload.request_id,proceed,reason}}));
        if(adder.claim(payload)){answer(true);return true;}
        if(draft?.product.id===id){
            answer(false,"quick_buy_personalized_add");
            if(!draft.pending)submit(draft,payload.item?.variant_id ?? draft.variant,
                (payload.item?.new_quantity ?? (payload.item?.previous_quantity ?? 0)+1)-(payload.item?.previous_quantity ?? 0));
            return true;
        }
        const currentProduct=state.location?.page?.type==="product" ? state.location.page.data.product.id : null;
        if(currentProduct===id || String(state.location?.page?.type)==="cart")return false;
        const origin=state.location.url, ownGeneration=generation;
        void policy(shop,id).then(config=>{
            if(safeState(nube)?.location.url!==origin || ownGeneration!==generation){answer(false,"quick_buy_context_changed");return;}
            if(!config.enabled || !config.fields.length){answer(true);return;}
            if(active?.id===id){answer(false,"quick_buy_fields_loading");return;}
            // A quantity edit without an open quick-buy draft keeps its persisted answers.
            const existing=(state.cart?.items ?? []).filter(item=>item.product_id===id && (!payload.item?.variant_id || item.variant_id===payload.item.variant_id));
            const saved=existing.length===1 ? Object.fromEntries(normalizeProperties(existing[0].properties).map(p=>[p.name,p.value])) : {};
            if(!active && (payload.item?.previous_quantity ?? 0)>0 && Object.keys(saved).length && !validate(config.fields,saved,config.locale).length){answer(true);return;}
            answer(false,"personalization_requires_product_page");redirect(state,id);
        },()=>{answer(false,"personalization_policy_unavailable");if(safeState(nube)?.location.url===origin && ownGeneration===generation)redirect(state,id);});
        return true;
    }
    function finished(state:NubeSDKState,success:boolean){
        const added=state.eventPayload as {product_id?:number;variant_id?:number;properties?:unknown}|undefined;
        if(added?.product_id != null)adder.settle(added.product_id,added.variant_id);
        if(!submission)return;
        if(success && (added?.product_id!==submission.product || added.variant_id!==submission.variant))return;
        if(!success && added?.product_id != null && (added.product_id!==submission.product || added.variant_id!==submission.variant))return;
        if(!success)adder.settle(added?.product_id,added?.variant_id);
        const sent=submission;submission=null;clearTimeout(submitTimer);
        if(!draft || draft.generation!==sent.generation)return;
        draft.pending=false;
        const saved=Object.fromEntries(normalizeProperties(added?.properties).map(p=>[p.name,p.value]));
        const confirmed=success && Object.entries(sent.properties).every(([key,value])=>saved[key]===value);
        if(confirmed && sent.values===JSON.stringify(draft.values)){draft.values={};drafts.delete(sent.product);draft.errors=[];}
        const es=draft.config.locale.startsWith("es");
        draft.message=confirmed ? (es ? "Producto agregado con la personalización." : "Produto adicionado com a personalização.")
            : (es ? "Revisá la personalización en el carrito. Conservamos los datos ingresados." : "Confira a personalização no carrinho. Mantivemos os dados preenchidos.");
        render();
    }
    async function syncCatalog(state:NubeSDKState | null) {
        const own=++catalogGeneration;
        if(!state || state.store?.theme?.trim().toLowerCase() !== "patagonia")return;
        const shop=storeId(state);if(!shop)return;
        const products=new Map<number,Product>();
        const current=state.location.page?.type === "product" ? state.location.page.data.product.id : null;
        function visit(value:unknown,depth=0) {
            if(!value || typeof value !== "object" || depth > 5)return;
            if(Array.isArray(value)){value.forEach(item=>visit(item,depth+1));return;}
            const record=value as Record<string,unknown>;
            if(typeof record.id === "number" && Array.isArray(record.variants) && record.canonical_url && record.id !== current)products.set(record.id,record as Product);
            for(const [key,nested] of Object.entries(record))if(!["variants","images","description","name"].includes(key))visit(nested,depth+1);
        }
        visit(state.location.page?.data);
        const list=[...products.values()];
        if(!list.length){nube.clearSlot("before_product_grid_item_name");return;}
        const es=state.store.language?.startsWith("es");
        const cards=new Map<number,{product:Product;config:PersonalizationConfig|null;loading:boolean}>(list.map(product=>[product.id,{product,config:null,loading:true}]));
        function renderCards() {
            const visible=[...cards.values()].filter(card=>card.loading || card.config == null || (card.config.enabled && card.config.fields.length));
            if(!visible.length){nube.clearSlot("before_product_grid_item_name");return;}
            nube.render("before_product_grid_item_name",visible.map(card=><Column key={card.product.id} gap={4}>
                <Button key={card.product.id} ariaLabel={es ? "Personalizar este producto con Campos Personalizados" : "Personalizar este produto com Campos Personalizados"} disabled={card.loading} onClick={()=>{
                    if(own !== catalogGeneration)return;
                    if(!card.config || !card.product.variants?.length){redirect(state!,card.product.id);return;}
                    close();active=card.product;
                    draft={slot:"modal_content",product:card.product,config:card.config,values:{...drafts.get(card.product.id)},errors:[],
                        variant:card.product.variants.length===1 ? card.product.variants[0].id : null,quantity:1,message:"",pending:false,generation};
                    render();
                }}>{card.loading ? (es ? "Cargando opciones…" : "Carregando opções…") : card.config ? (es ? "Personalizar" : "Personalizar") : (es ? "Ver producto" : "Ver produto")}</Button>
            </Column>));
        }
        renderCards();
        await Promise.all(list.map(async product=>{
            let config:PersonalizationConfig|null=null;try{config=await policy(shop,product.id);}catch{}
            if(own !== catalogGeneration)return;
            cards.set(product.id,{product,config,loading:false});renderCards();
        }));
    }
    return {intercept,locationChanged,finished,syncCatalog};
}
