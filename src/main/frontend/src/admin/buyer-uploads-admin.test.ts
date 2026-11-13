import { readFileSync } from "node:fs";
import { JSDOM } from "jsdom";
import { afterEach, expect, it, vi } from "vitest";

const source = readFileSync(new URL("../../../resources/static/assets/buyer-uploads-admin.js", import.meta.url), "utf8");
const html = readFileSync(new URL("../../../resources/templates/admin/buyer-uploads.html", import.meta.url), "utf8");
const messages = Object.fromEntries(readFileSync(new URL("../../../resources/messages.properties", import.meta.url), "utf8").split("\n")
    .filter(line => line.startsWith("buyer.files.")).map(line => { const equal = line.indexOf("=");return [line.slice(12, equal), line.slice(equal + 1)]; }));
let dom: JSDOM;
afterEach(() => dom?.window.close());
const file = (id: string, order = 300) => ({id, order_id: order, order_item_id: 301, original_name: `${id}.png`, state: "ORDERED", size_bytes: 20_000_000,
    order_created_at: "2026-01-01T10:00:00Z", expires_at: "2099-01-01T10:00:00Z"});
function boot(initial = [file("a"), file("b")],query="") {
    dom = new JSDOM(html, {url: "https://app.test/admin/buyer-uploads"+query, runScripts: "outside-only"});
    const doc = dom.window.document;
    doc.documentElement.lang = "pt";
    let files = initial;
    const quota = 2_000_000_000;
    const fetch = vi.fn(async (url: string, options?: {body: string}) => {
        if (url.includes("delete-selected")) {
            const ids: string[] = JSON.parse(options!.body).ids;
            files = files.map(f => ids.includes(f.id) ? {...f, state: "DELETING"} : f);
            return {ok: true, json: async () => ({files: ids.length, bytes: 40_000_000})};
        }
        return {ok: true, json: async () => ({files, usage: {occupiedBytes: 40_000_000, quotaBytes: quota, acceptsUploads: true, warningPercent: 0},
            enabled: true, pendingBytes: files.some(f => f.state === "DELETING") ? 40_000_000 : 0, page: 1, pages: 1, totalOrders: 1})};
    });
    Object.assign(dom.window, {fetch, ncfBuyerFilesUi: messages});
    const dialog = doc.getElementById("buyer-delete-dialog") as HTMLDialogElement;
    dialog.showModal = vi.fn(() => dialog.setAttribute("open", ""));
    dialog.close = vi.fn(() => dialog.removeAttribute("open"));
    const submit = vi.spyOn(dom.window.HTMLFormElement.prototype, "submit").mockImplementation(() => {});
    dom.window.eval(source);
    return {doc, fetch, dialog, submit};
}
async function loaded(doc: Document) { await vi.waitFor(() => expect(doc.querySelectorAll("[data-file-select]")).toHaveLength(2)); }
it("shows product names, safe thumbnails and localized order statuses", async () => {
    const {doc}=boot([...[file('a'),file('b')].map(f=>({...f,product_name:'Camisa personalizada',product_image_url:'https://images.test/camisa.png',order_number:'108',order_status:'open',payment_status:'paid',shipping_status:'unshipped'}))]);
    await loaded(doc);
    expect(doc.querySelector('.buyer-order-heading')!.textContent).toContain('Pedido 108');
    expect(doc.querySelector('.buyer-order-statuses')!.textContent).toContain('Pagamento: Pago');
    expect(doc.querySelector('.buyer-file-product')!.textContent).toContain('Camisa personalizada');
    expect(doc.querySelector('.buyer-file-product img')!.getAttribute('src')).toBe('https://images.test/camisa.png');
});
it("sends the order status filter and clears it with the other filters", async () => {
    const {doc,fetch}=boot();await loaded(doc);
    (doc.getElementById('buyer-order-status') as HTMLSelectElement).value='cancelled';
    doc.getElementById('buyer-filters')!.dispatchEvent(new dom.window.Event('submit',{cancelable:true}));
    await vi.waitFor(()=>expect(fetch.mock.calls[fetch.mock.calls.length - 1][0]).toContain('orderStatus=cancelled'));
    click(doc,'clear-filters');
    await vi.waitFor(()=>expect(fetch.mock.calls[fetch.mock.calls.length - 1][0]).toContain('orderStatus=all'));
});
function click(doc: Document, id: string) { (doc.getElementById(`buyer-${id}`) as HTMLButtonElement).click(); }

it("groups orders, supports partial selection and requires a reviewed confirmation before deletion", async () => {
    const {doc, fetch, dialog} = boot();await loaded(doc);
    const order = doc.querySelector<HTMLInputElement>("[data-order-select]")!;order.click();
    expect(doc.getElementById("buyer-selected")!.textContent).toContain("Arquivos selecionados: 2");
    doc.querySelector<HTMLInputElement>('[data-file-select="a"]')!.click();
    expect(order.indeterminate).toBe(true);
    doc.querySelector<HTMLInputElement>('[data-file-select="a"]')!.click();
    click(doc, "delete-selected");expect(dialog.open).toBe(true);
    expect(doc.getElementById("buyer-delete-summary")!.textContent).toContain("Arquivos: 2 · pedidos: 1");
    expect(fetch.mock.calls.filter(([url]) => url.includes("delete-selected"))).toHaveLength(0);
    click(doc, "cancel-delete");expect(dialog.open).toBe(false);
    click(doc, "delete-selected");click(doc, "confirm-delete");
    await vi.waitFor(() => expect(fetch.mock.calls.filter(([url]) => url.includes("delete-selected"))).toHaveLength(1));
    const request = fetch.mock.calls.find(([url]) => url.includes("delete-selected"))!;
    expect(JSON.parse(request[1]!.body)).toEqual({ids: expect.arrayContaining(["a", "b"]), confirmed: true});
    await vi.waitFor(() => expect(doc.getElementById("buyer-pending")!.hidden).toBe(false));
    expect(doc.getElementById("buyer-occupied")!.textContent).toBe("40 MB");
    expect(doc.getElementById("buyer-selected")!.textContent).toBe("Nenhum arquivo selecionado");
    expect(doc.querySelectorAll("[data-file-select]")).toHaveLength(0);
});

it("offers only individual downloads and keeps selection for deletion", async () => {
    const {doc, fetch, submit} = boot();await loaded(doc);
    expect(doc.getElementById("buyer-download-selected")).toBeNull();
    expect(doc.querySelector(".buyer-order-heading button")).toBeNull();
    expect([...doc.querySelectorAll<HTMLAnchorElement>(".buyer-order a")].map(link => link.getAttribute("href")))
        .toEqual(["/admin/buyer-uploads/a/download", "/admin/buyer-uploads/b/download"]);
    doc.querySelector<HTMLInputElement>("[data-order-select]")!.click();
    expect((doc.getElementById("buyer-delete-selected") as HTMLButtonElement).disabled).toBe(false);
    expect(submit).not.toHaveBeenCalled();
    expect(fetch.mock.calls.some(([url]) => url.includes("delete-selected"))).toBe(false);
});

it("keeps selection after a failed deletion and shows an actionable error", async () => {
    const {doc, fetch, dialog} = boot();await loaded(doc);
    doc.querySelector<HTMLInputElement>("[data-order-select]")!.click();click(doc, "delete-selected");
    fetch.mockResolvedValueOnce({ok: false, json: async () => ({code: "upload.not.found"})} as never);
    click(doc, "confirm-delete");
    await vi.waitFor(() => expect((doc.getElementById("buyer-confirm-delete") as HTMLButtonElement).disabled).toBe(false));
    expect(dialog.open).toBe(true);expect(doc.getElementById("buyer-selected")!.textContent).toContain("Arquivos selecionados: 2");
    expect(doc.getElementById("buyer-status")!.textContent).toContain("não está mais disponível");
});

it("preserves selection between pages and sends date and ordering filters to the server", async () => {
    const {doc, fetch} = boot();await loaded(doc);
    const payload = (files: ReturnType<typeof file>[], page: number) => ({files, usage: {occupiedBytes: 40_000_000, quotaBytes: 2e9, acceptsUploads: true, warningPercent: 0}, enabled: true, pendingBytes: 0, page, pages: 2, totalOrders: 2});
    fetch.mockResolvedValueOnce({ok: true, json: async () => payload([file("c", 400)], 2)} as never);
    doc.querySelector<HTMLInputElement>('[data-file-select="a"]')!.click();
    (doc.getElementById("buyer-next") as HTMLButtonElement).disabled = false;click(doc, "next");
    await vi.waitFor(() => expect(doc.querySelector('[data-file-select="c"]')).not.toBeNull());
    doc.querySelector<HTMLInputElement>('[data-file-select="c"]')!.click();
    expect(doc.getElementById("buyer-selected")!.textContent).toContain("Arquivos selecionados: 2");
    fetch.mockResolvedValueOnce({ok: true, json: async () => payload([file("a"), file("b")], 1)} as never);
    click(doc, "previous");await loaded(doc);
    expect(doc.querySelector<HTMLInputElement>('[data-file-select="a"]')!.checked).toBe(true);
    (doc.getElementById("buyer-before") as HTMLInputElement).value = "2026-01-31";
    (doc.getElementById("buyer-sort") as HTMLSelectElement).value = "largest";
    doc.getElementById("buyer-filters")!.dispatchEvent(new dom.window.Event("submit", {cancelable: true}));
    await vi.waitFor(() => expect(fetch.mock.calls[fetch.mock.calls.length - 1][0]).toContain("sort=largest"));
    expect(fetch.mock.calls[fetch.mock.calls.length - 1][0]).toContain("before=");
});

it("never treats a loading error as an empty list", async () => {
    const {doc, fetch} = boot();await loaded(doc);
    fetch.mockRejectedValueOnce(new Error("offline"));click(doc, "refresh");
    await vi.waitFor(() => expect(doc.getElementById("buyer-status")!.textContent).toContain("Não foi possível carregar"));
    expect(doc.querySelectorAll(".buyer-order")).toHaveLength(1);
});

it("keeps the selected order in requests from the Nuvemshop admin link",async()=>{
 const {fetch}=boot([file("a")],"?orderId=300");
 await vi.waitFor(()=>expect(fetch).toHaveBeenCalled());
 expect(fetch.mock.calls[0][0]).toContain("orderId=300");
});
