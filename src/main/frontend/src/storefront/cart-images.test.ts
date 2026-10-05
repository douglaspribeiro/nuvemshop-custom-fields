import type { CartItem, NubeSDK, NubeSDKState } from "@tiendanube/nube-sdk-types";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { DISABLED, type PersonalizationConfig } from "../shared/config";
import { createCartImageRenderer, selectedCartImages } from "./cart-images";

const config: PersonalizationConfig = {
    ...DISABLED, enabled: true,
    fields: [{ label: "Capa", propertyName: "Capa", fieldType: "IMAGE_SELECT", required: true,
        maxLength: 100, placeholder: null, validationPattern: null, options: ["Floral", "Azul"],
        imageOptions: [
            { label: "Floral", thumbnailUrl: "https://app.test/floral", imageUrl: "https://app.test/floral-large" },
            { label: "Azul", thumbnailUrl: "https://app.test/azul", imageUrl: "https://app.test/azul-large" },
        ] }],
};
const item = (id: number, value: string, product = 100) => ({
    id, product_id: product, variant_id: id, name: "Caderno", properties: { Capa: value },
}) as unknown as CartItem;
const state = (...items: CartItem[]) => ({ store: { id: 42 }, cart: { items } }) as NubeSDKState;
const runtime = () => ({ render: vi.fn(), clearSlot: vi.fn(), send: vi.fn() });
function imageSources(node: any): string[] {
    if (Array.isArray(node)) return node.flatMap(imageSources);
    if (!node || typeof node !== "object") return [];
    return [...(node.type === "img" ? [node.src] : []), ...imageSources(node.children)];
}

beforeEach(() => vi.stubGlobal("self", { __APP_DATA__: { id: "test-storefront" } }));
afterEach(() => vi.unstubAllGlobals());

it("matches image choices by field and persisted value, in both property formats", () => {
    const cartItem = item(1, "Azul");
    expect(selectedCartImages(cartItem, config)[0].thumbnailUrl).toBe("https://app.test/azul");
    cartItem.properties = [{ name: "Capa", value: "Floral" }];
    expect(selectedCartImages(cartItem, config)[0].label).toBe("Floral");
    cartItem.properties = { OutroCampo: "Floral" };
    expect(selectedCartImages(cartItem, config)).toEqual([]);
});

it("keeps different selections on distinct cart lines and fetches each product once", async () => {
    const nube = runtime(); const load = vi.fn().mockResolvedValue(config);
    const render = createCartImageRenderer(nube as unknown as NubeSDK, load);
    await render(state(item(1, "Floral"), item(2, "Azul"), item(3, "Azul", 200)));
    const [slot, nodes] = nube.render.mock.calls[0];
    expect(slot).toBe("before_line_item");
    expect(nodes.map((node: any) => node.key)).toEqual([1, 2, 3]);
    expect(imageSources(nodes)).toEqual(["https://app.test/floral", "https://app.test/azul", "https://app.test/azul"]);
    expect(load).toHaveBeenCalledTimes(2);
    await render(state(item(2, "Floral")));
    expect(imageSources(nube.render.mock.calls[1][1])).toEqual(["https://app.test/floral"]);
    expect(load).toHaveBeenCalledTimes(2);
});

it("keeps names when an image is removed or the plan disallows it, and clears an empty cart", async () => {
    const nube = runtime(); const load = vi.fn().mockResolvedValue(config);
    const render = createCartImageRenderer(nube as unknown as NubeSDK, load);
    await render(state(item(1, "Removida")));
    expect(imageSources(nube.render.mock.calls.slice(-1)[0][1])).toEqual([]);
    expect(JSON.stringify(nube.render.mock.calls.slice(-1)[0][1])).toContain("Capa: Removida");
    load.mockResolvedValue(DISABLED);
    await render(state(item(1, "Floral", 200)));
    expect(imageSources(nube.render.mock.calls.slice(-1)[0][1])).toEqual([]);
    await render(state());
    expect(nube.clearSlot).toHaveBeenCalledWith("before_line_item");
    expect(load).toHaveBeenCalledTimes(2);
});

it("does not resurrect a removed item after a slow configuration response", async () => {
    let resolve!: (value: PersonalizationConfig) => void;
    const load = vi.fn(() => new Promise<PersonalizationConfig>(done => { resolve = done; }));
    const nube = runtime(); const render = createCartImageRenderer(nube as unknown as NubeSDK, load);
    const pending = render(state(item(1, "Floral")));
    await render(state()); resolve(config); await pending;
    expect(nube.render).not.toHaveBeenCalled();
    expect(nube.clearSlot).toHaveBeenCalledWith("before_line_item");
});

it("retries failed configuration requests without changing cart contents", async () => {
    const nube = runtime(); const load = vi.fn().mockRejectedValueOnce(new Error("offline")).mockResolvedValue(config);
    const render = createCartImageRenderer(nube as unknown as NubeSDK, load);
    await render(state(item(1, "Floral")));
    expect(JSON.stringify(nube.render.mock.calls.slice(-1)[0][1])).toContain("Capa: Floral");
    await render(state(item(1, "Floral")));
    expect(imageSources(nube.render.mock.calls.slice(-1)[0][1])).toEqual(["https://app.test/floral"]);
    expect(nube.send).not.toHaveBeenCalled();
});

it("displays all persisted custom fields even when there are no image choices", async () => {
    const nube = runtime(); const load = vi.fn().mockResolvedValue(DISABLED);
    const render = createCartImageRenderer(nube as unknown as NubeSDK, load);
    const cartItem=item(1,"Floral");cartItem.properties={Nome:"Ana",Numero:"10",Tamanho:"M",Mensagem:"Feliz aniversário",_interna:"oculta"};
    await render(state(cartItem));
    const rendered=JSON.stringify(nube.render.mock.calls.slice(-1)[0][1]);
    for(const value of ["Nome: Ana","Numero: 10","Tamanho: M","Mensagem: Feliz aniversário"]) expect(rendered).toContain(value);
    expect(rendered).not.toContain("oculta");
});
