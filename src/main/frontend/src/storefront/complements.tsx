import { Button, Checkbox, Column, Image, NumberField, Row, Text } from "@tiendanube/nube-sdk-jsx";
import type { NubeComponent, NubeSDK, NubeSDKState } from "@tiendanube/nube-sdk-types";
import { appOrigin } from "../shared/config";
import { normalizeProperties } from "../shared/properties";
import type { createCartAdder, PersonalizedAdd } from "./cart-add";

type Item = { productId: number; variantId: number; label: string; price: string; imageUrl: string | null; stock: number | null;
    appearance?: {backgroundColor: string | null; textColor: string | null; showBorder: boolean; roundedCorners: boolean}; };
type Catalog = { locale: string; currency: string | null; items: Item[] };

export async function fetchComplements(store: number, product: number): Promise<Catalog | null> {
    try {
        const response = await fetch(`${appOrigin()}/public/stores/${store}/products/${product}/complements`,
            { signal: AbortSignal.timeout(5000) });
        if (!response.ok) return null;
        const catalog = await response.json() as Catalog;
        return catalog?.items?.length ? catalog : null;
    } catch {
        return null;
    }
}

export function createComplements(nube: NubeSDK, adder: ReturnType<typeof createCartAdder>, render: () => void) {
    let catalog: Catalog | null = null;
    let selected = new Map<number, number>();
    let pending: PersonalizedAdd[] = [];
    let message = "";
    let timer: ReturnType<typeof setTimeout> | undefined;
    let generation = 0;
    let basket = 0;
    let confirmed = 0;
    let parentConfirmed: (() => void) | undefined;
    let review = false;
    let groupKey = "";
    let group = "";

    const es = () => catalog?.locale.startsWith("es") === true;
    const say = (pt: string, spanish: string) => es() ? spanish : pt;

    function reset(next: Catalog | null = null) {
        if (pending[0]) adder.settle(pending[0].product_id, pending[0].variant_id);
        clearTimeout(timer);
        generation++;
        catalog = next;
        selected = new Map();
        pending = [];
        parentConfirmed = undefined;
        message = "";
        review = false;
    }

    function stop() {
        const current = pending[0];
        if (!current) return;
        adder.settle(current.product_id, current.variant_id);
        clearTimeout(timer);
        pending = [];
        parentConfirmed = undefined;
        selected.clear();
        review = true;
        message = confirmed > 0
            ? say("Inclusão parcial. Confira quais itens entraram no carrinho antes de continuar.",
                "La compra se agregó parcialmente. Revisa qué artículos quedaron en el carrito antes de continuar.")
            : say("Não foi possível confirmar a inclusão. Confira o carrinho antes de tentar novamente.",
                "No pudimos confirmar la incorporación. Revisa el carrito antes de volver a intentar.");
        render();
    }

    function sendNext() {
        const next = pending[0];
        if (!next) {
            selected.clear();
            message = say("Produto e complementos adicionados ao carrinho.", "Producto y complementos agregados al carrito.");
            render();
            nube.send("cart:open");
            return;
        }
        timer = setTimeout(stop, 10000);
        adder.add(next);
    }

    function add(parent: PersonalizedAdd, onParentConfirmed: () => void) {
        if (!catalog || !selected.size) return false;
        if (pending.length || review) return true;
        const choices = catalog.items.filter(item => selected.has(item.variantId));
        if (choices.some(item => {
            const quantity = selected.get(item.variantId)!;
            return !Number.isSafeInteger(quantity) || quantity < 1 || (item.stock != null && quantity > item.stock);
        })) {
            message = say("Informe uma quantidade disponível para cada complemento.", "Indica una cantidad disponible para cada complemento.");
            render();
            return true;
        }
        groupKey = "Grupo de complementos";
        while (Object.prototype.hasOwnProperty.call(parent.properties, groupKey)) groupKey += "+";
        group = `${Date.now().toString(36)}-${(++basket).toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
        confirmed = 0;
        parentConfirmed = onParentConfirmed;
        pending = [
            {...parent, properties: {...parent.properties, [groupKey]: group}},
            ...choices.map(item => ({
                product_id: item.productId, variant_id: item.variantId,
                quantity: selected.get(item.variantId)!, properties: {[groupKey]: group},
            })),
        ];
        message = say("Adicionando ao carrinho…", "Agregando al carrito…");
        render();
        sendNext();
        return true;
    }

    function finished(state: NubeSDKState, success: boolean) {
        const current = pending[0];
        if (!current) return false;
        const added = state.eventPayload as {product_id?: number; variant_id?: number; quantity?: number; properties?: unknown} | null;
        if (!success) {
            // The storefront failure event can lack item IDs. Never retry an ambiguous mutation.
            if (!added?.product_id || (added.product_id === current.product_id && added.variant_id === current.variant_id)) {
                stop();
                return true;
            }
            return false;
        }
        if (added?.product_id !== current.product_id || added.variant_id !== current.variant_id) return false;
        const properties = Object.fromEntries(normalizeProperties(added.properties).map(property => [property.name, property.value]));
        if (properties[groupKey] !== group) return false;
        if (added.quantity !== current.quantity ||
            Object.entries(current.properties).some(([key, value]) => properties[key] !== value)) {
            stop();
            return true;
        }
        clearTimeout(timer);
        adder.settle(current.product_id, current.variant_id);
        pending.shift();
        if (confirmed++ === 0) {
            parentConfirmed?.();
            parentConfirmed = undefined;
        }
        sendNext();
        return true;
    }

    function fields(): NubeComponent | null {
        if (!catalog) return null;
        const ownGeneration = generation;
        const locked = pending.length > 0 || review;
        const price = (item: Item, quantity = 1) => {
            const amount = Number(item.price) * quantity;
            if (!catalog?.currency) return amount.toFixed(2);
            try {
                return new Intl.NumberFormat(catalog!.locale, {style: "currency", currency: catalog.currency}).format(amount);
            } catch {
                return `${amount.toFixed(2)} ${catalog!.currency ?? ""}`.trim();
            }
        };
        const border = "color-mix(in srgb, currentColor 25%, transparent)";
        return {type: "col", gap: 8, style: {paddingTop: "12px", paddingBottom: "12px"}, children: [
            <Text style={{fontWeight: "700", fontSize: "16px"}}>{say("Complementos opcionais", "Complementos opcionales")}</Text>,
            ...catalog.items.map(item => {
                const quantity = selected.get(item.variantId);
                const name = `ncf-complement-${item.variantId}`;
                const appearance = item.appearance;
                const rounded = appearance?.roundedCorners !== false;
                const controlStyle = {
                    border: "1px solid currentColor", borderRadius: rounded ? "50%" : "0",
                    minWidth: "40px", minHeight: "40px", backgroundColor: "transparent", color: "inherit", boxShadow: "none",
                };
                const textColor = appearance?.textColor ?? undefined;
                const summary = <Column gap={3} style={{minWidth: "0", flex: "1", overflowWrap: "anywhere"}}>
                    {locked ? <Text color={textColor} style={{fontWeight: "600"}}>{item.label}</Text> :
                        <Checkbox key={name} name={name} label={item.label} checked={quantity != null}
                            style={{label: {minHeight: "36px", color: textColor, fontWeight: "600", whiteSpace: "normal", overflowWrap: "anywhere"}}}
                            onChange={event => {
                                if (generation !== ownGeneration || pending.length || review) return;
                                if (event.value) selected.set(item.variantId, 1);
                                else selected.delete(item.variantId);
                                message = "";
                                render();
                            }} />}
                    <Text color={textColor} style={{fontSize: "13px"}}>{price(item)}</Text>
                </Column>;
                const thumbnail = item.imageUrl ? [<Image src={item.imageUrl} width="52px" height="52px" alt=""
                    style={{borderRadius: rounded ? "8px" : "0", objectFit: "contain", flexShrink: "0"}} />] : [];
                const content = [<Row gap={12} style={{alignItems: "center", minWidth: "0"}}>{[...thumbnail, summary]}</Row>];

                if (quantity != null) {
                    const quantityField = <NumberField key={`${name}-quantity`} name={`${name}-quantity`} label={say("Quantidade", "Cantidad")}
                        min={1} max={item.stock ?? undefined} step={1} value={quantity} disabled={locked}
                        style={{
                            container: {width: "auto", minWidth: "0", display: "flex", alignItems: "center", gap: "8px"},
                            label: {fontSize: "13px", margin: "0", color: textColor},
                            wrapper: {maxWidth: "132px"},
                            input: {color: "inherit", backgroundColor: "transparent"},
                            incrementButton: controlStyle, decrementButton: controlStyle,
                        }}
                        onChange={event => {
                            if (generation !== ownGeneration || pending.length || review || !selected.has(item.variantId)) return;
                            selected.set(item.variantId, Number(event.value));
                            render();
                        }} />;
                    const subtotal = Number.isSafeInteger(quantity) && quantity >= 1
                        ? [<Text color={textColor} style={{fontSize: "13px", fontWeight: "600"}}>{`Subtotal: ${price(item, quantity)}`}</Text>]
                        : [];
                    content.push(<Row gap={12} style={{
                        flexWrap: "wrap", alignItems: "center", justifyContent: "space-between",
                        marginTop: "10px", paddingTop: "10px", borderTop: `1px solid ${border}`,
                    }}>{[quantityField, ...subtotal]}</Row>);
                }

                return <Column key={name} gap={0} style={{
                    color: textColor, border: `1px solid ${appearance?.showBorder === false ? "transparent"
                        : quantity == null ? border : "currentColor"}`,
                    borderRadius: rounded ? "12px" : "0", padding: "12px", minWidth: "0", boxSizing: "border-box",
                    backgroundColor: appearance?.backgroundColor ?? (quantity == null ? "transparent" : "color-mix(in srgb, currentColor 4%, transparent)"),
                }}>{content}</Column>;
            }),
            ...(message ? [<Text style={{fontSize: "14px", marginTop: "4px"}}>{message}</Text>] : []),
            ...(review ? [
                <Button onClick={() => nube.send("cart:open")}>{say("Abrir carrinho", "Abrir carrito")}</Button>,
                <Button variant="transparent" onClick={() => {
                    if (ownGeneration !== generation) return;
                    review = false;
                    message = "";
                    render();
                }}>{say("Já conferi o carrinho", "Ya revisé el carrito")}</Button>,
            ] : []),
        ]};
    }

    return {reset, add, fields, finished, hasSelection: () => selected.size > 0,
        pending: () => pending.length > 0, review: () => review, locale: () => catalog?.locale};
}
