import { Button, Checkbox, Column, Image, NumberField, Row, Text } from "@tiendanube/nube-sdk-jsx";
import type { NubeComponent, NubeSDK, NubeSDKState } from "@tiendanube/nube-sdk-types";
import { styled } from "@tiendanube/nube-sdk-ui";
import { appOrigin } from "../shared/config";
import { normalizedColor, normalizeProperties } from "../shared/properties";
import type { createCartAdder, PersonalizedAdd } from "./cart-add";

type Item = { productId: number; variantId: number; label: string; subtitle?: string | null;
    price: string; imageUrl: string | null; stock: number | null;
    appearance?: {backgroundColor: string | null; textColor: string | null; accentColor?: string | null;
        showBorder: boolean; roundedCorners: boolean}; };
type Catalog = { locale: string; currency: string | null; items: Item[] };
type CartConfirmation = { product_id?: number; variant_id?: number; quantity?: number; properties?: unknown };

const ColoredOffer = styled(Column)`
    & .nube-check .form-group-icon svg { color: var(--ncf-complement-check) !important; }
`;

function luminance(hex: string): number {
    const [r, g, b] = [1, 3, 5].map(offset => {
        const channel = Number.parseInt(hex.slice(offset, offset + 2), 16) / 255;
        return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function checkboxColor(background: string | undefined, text: string | undefined): string | undefined {
    if (!background) return text;
    const surface = luminance(background);
    const ink = luminance(text ?? "#25323d");
    return (Math.max(surface, ink) + 0.05) / (Math.min(surface, ink) + 0.05) >= 4.5
        ? text ?? "#25323d"
        : surface >= 0.18 ? "#000000" : "#ffffff";
}

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
    let awaitingConfirmation = false;
    let message = "";
    let timer: ReturnType<typeof setTimeout> | undefined;
    let generation = 0;
    let basket = 0;
    let confirmed = 0;
    let parentConfirmed: (() => void) | undefined;
    let review = false;

    const es = () => catalog?.locale.startsWith("es") === true;
    const say = (pt: string, spanish: string) => es() ? spanish : pt;

    function reset(next: Catalog | null = null) {
        if (pending[0]) adder.settle(pending[0].product_id, pending[0].variant_id);
        clearTimeout(timer);
        generation++;
        catalog = next;
        selected = new Map();
        pending = [];
        awaitingConfirmation = false;
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
        awaitingConfirmation = false;
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
        awaitingConfirmation = true;
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
        let groupKey = "Grupo de complementos";
        while (Object.prototype.hasOwnProperty.call(parent.properties, groupKey)) groupKey += "+";
        const group = `${Date.now().toString(36)}-${(++basket).toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
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

    function confirms(item: CartConfirmation | null | undefined, expected: PersonalizedAdd) {
        if (item?.product_id !== expected.product_id || item.variant_id !== expected.variant_id
            || item.quantity !== expected.quantity) return false;
        const properties = Object.fromEntries(normalizeProperties(item.properties, { includeInternal: true })
            .map(property => [property.name, property.value]));
        return Object.entries(expected.properties).every(([key, value]) => properties[key] === value);
    }

    function confirmFromCart(state: NubeSDKState, added?: CartConfirmation | null) {
        const current = pending[0];
        if (!current || !awaitingConfirmation) return false;
        // cart:add can expose an optimistic item without an id even when the store rejects it.
        const isPersisted = state.cart?.items?.some(item => item.id > 0 && confirms(item, current));
        if (!confirms(added, current) && !isPersisted) return false;

        clearTimeout(timer);
        awaitingConfirmation = false;
        adder.settle(current.product_id, current.variant_id);
        pending.shift();
        if (confirmed++ === 0) {
            parentConfirmed?.();
            parentConfirmed = undefined;
        }
        // Let the SDK finish dispatching the mutation events before sending the next item.
        if (pending.length) timer = setTimeout(sendNext, 0);
        else sendNext();
        return true;
    }

    function finished(state: NubeSDKState, success: boolean) {
        const current = pending[0];
        if (!current || !awaitingConfirmation) return false;
        const added = state.eventPayload as CartConfirmation | null;
        if (success) return confirmFromCart(state, added);
        // The storefront failure event can lack item IDs. Never retry an ambiguous mutation.
        if (added?.product_id && (added.product_id !== current.product_id
            || (added.variant_id != null && added.variant_id !== current.variant_id))) return false;
        stop();
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
        return {type: "col", gap: 8, style: {paddingTop: "8px", paddingBottom: "8px"}, children: [
            <Text style={{fontWeight: "700", fontSize: "16px", lineHeight: "1.3", margin: "0"}}>
                {say("Complementos opcionais", "Complementos opcionales")}
            </Text>,
            ...catalog.items.map(item => {
                const quantity = selected.get(item.variantId);
                const name = `ncf-complement-${item.variantId}`;
                const appearance = item.appearance;
                const rounded = appearance?.roundedCorners !== false;
                const textColor = normalizedColor(appearance?.textColor) ?? undefined;
                const background = normalizedColor(appearance?.backgroundColor) ?? undefined;
                const accent = normalizedColor(appearance?.accentColor) ?? textColor ?? "currentColor";
                const tint = `color-mix(in srgb, ${accent} 12%, transparent)`;
                const checkColor = checkboxColor(background, accent === "currentColor" ? undefined : accent);
                const Offer = checkColor ? ColoredOffer : Column;
                const controlStyle = {
                    width: "36px", height: "36px", minWidth: "0", minHeight: "36px", flex: "0 1 36px",
                    margin: "0", padding: "0", border: "0", borderRadius: "0",
                    backgroundColor: tint, color: "inherit", boxSizing: "border-box", boxShadow: "none",
                };
                const summary = [
                    locked ? <Text color={textColor} style={{fontSize: "17px", fontWeight: "700", margin: "0"}}>{item.label}</Text> :
                        <Checkbox key={name} name={name} label={item.label} checked={quantity != null}
                            style={{
                                container: {display: "flex", flexDirection: "column", alignItems: "stretch", minHeight: "0", margin: "0"},
                                label: {
                                    margin: "0", padding: "0", minHeight: "0", lineHeight: "1.35",
                                    alignItems: "center", textAlign: "start", color: textColor, fontSize: "17px", fontWeight: "700",
                                    whiteSpace: "normal", overflowWrap: "anywhere",
                                },
                                checkbox: {width: "20px", height: "20px", alignSelf: "center", margin: "0", flexShrink: "0"},
                            }}
                            onChange={event => {
                                if (generation !== ownGeneration || pending.length || review) return;
                                if (event.value) selected.set(item.variantId, 1);
                                else selected.delete(item.variantId);
                                message = "";
                                render();
                            }} />,
                    ...(item.subtitle ? [<Text color={textColor} style={{fontSize: "14px", margin: "0"}}>{item.subtitle}</Text>] : []),
                    <Text color={accent} style={{fontSize: "16px", fontWeight: "700", margin: "0"}}>
                        {`${price(item)} ${say("por unidade", "por unidad")}`}
                    </Text>,
                ];
                const photo = item.imageUrl ? [
                    <Column gap={0} style={{flex: "1 1 38%", width: "0", minWidth: "0"}}>
                        {[<Image src={item.imageUrl} width="100%" alt="" style={{
                            display: "block", height: "auto", maxHeight: "260px", aspectRatio: "1.08",
                            borderRadius: rounded ? "12px" : "0", objectFit: "cover",
                        }} />]}
                    </Column>,
                ] : [];

                if (quantity != null) {
                    const quantityField = <NumberField key={`${name}-quantity`} name={`${name}-quantity`} label={say("Quantidade", "Cantidad")}
                        min={1} max={item.stock ?? undefined} step={1} value={quantity} disabled={locked}
                        style={{
                            container: {display: "flex", flexDirection: "column", alignItems: "flex-start", gap: "6px", margin: "0"},
                            label: {width: "auto", fontSize: "13px", fontWeight: "700", margin: "0", padding: "0", textAlign: "start", color: textColor},
                            wrapper: {
                                display: "flex", alignItems: "center", gap: "0", width: "fit-content", maxWidth: "100%",
                                margin: "0", padding: "0", border: `1px solid ${accent}`,
                                borderRadius: rounded ? "8px" : "0", overflow: "hidden",
                                backgroundColor: "transparent",
                            },
                            input: {
                                width: "54px", minWidth: "0", flex: "0 1 54px", height: "36px", minHeight: "36px",
                                margin: "0", padding: "0", textAlign: "center", border: "0",
                                borderInline: `1px solid ${accent}`, borderRadius: "0", fontWeight: "700",
                                color: "inherit", backgroundColor: "transparent", boxSizing: "border-box",
                            },
                            incrementButton: controlStyle, decrementButton: controlStyle,
                        }}
                        onChange={event => {
                            if (generation !== ownGeneration || pending.length || review || !selected.has(item.variantId)) return;
                            selected.set(item.variantId, Number(event.value));
                            render();
                        }} />;
                    const subtotal = Number.isSafeInteger(quantity) && quantity >= 1
                        ? [<Text color={accent} style={{
                            fontSize: "16px", fontWeight: "700", margin: "4px 0 0", padding: "6px 9px",
                            backgroundColor: tint, borderRadius: rounded ? "8px" : "0",
                        }}>
                            {`${say("Total do complemento", "Total del complemento")}: ${price(item, quantity)}`}
                        </Text>]
                        : [];
                    summary.push(<Column gap={6} style={{marginTop: "4px", alignItems: "stretch"}}>
                        {[quantityField, ...subtotal,
                            <Text color={textColor} style={{fontSize: "13px", margin: "0"}}>
                                {say("Quantidade total para este item do pedido.", "Cantidad total para este artículo del pedido.")}
                            </Text>,
                        ]}
                    </Column>);
                }

                return <Offer key={name} gap={0} style={{
                    ...(checkColor ? {"--ncf-complement-check": checkColor} : {}),
                    color: textColor, border: `1px solid ${appearance?.showBorder === false ? "transparent" : accent}`,
                    borderRadius: rounded ? "16px" : "0", padding: "14px", minWidth: "0", boxSizing: "border-box",
                    backgroundColor: background ?? "transparent",
                }}>
                    {[<Row gap={14} style={{flexWrap: "wrap", alignItems: "center", minWidth: "0", width: "100%"}}>
                        {[...photo, <Column gap={6} style={{
                            flex: "1.6 1 180px", minWidth: "0", alignItems: "stretch", overflowWrap: "anywhere",
                        }}>{summary}</Column>]}
                    </Row>]}
                </Offer>;
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

    return {reset, add, fields, finished, cartUpdated: confirmFromCart, hasSelection: () => selected.size > 0,
        pending: () => pending.length > 0, review: () => review, locale: () => catalog?.locale};
}
