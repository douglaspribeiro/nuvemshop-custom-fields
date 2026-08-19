/**
 * Verifica o script NubeSDK de vitrine numa loja real.
 *
 * O script e registrado com event=onfirstinteraction, entao ele NAO carrega numa
 * renderizacao headless passiva: e preciso sintetizar clique/scroll. Foi exatamente essa
 * pegadinha que gerou um diagnostico falso ("nenhum script carregado") antes.
 *
 * Usa o WebSocket nativo do Node 22 — sem dependencia de `ws`.
 *
 * Uso:
 *   src/main/frontend/node/node scripts/check-nubesdk-storefront.mjs <url-do-produto>
 */
import { spawn } from "node:child_process";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

const targetUrl = process.argv[2];
if (!targetUrl) {
	console.error("Uso: node scripts/check-nubesdk-storefront.mjs <url-do-produto>");
	process.exit(2);
}

const SLOT = "before_product_detail_add_to_cart";
const INTEREST = ["apps-scripts", "storefront-sdk", "script-events", "personalization", "personalizer"];

const port = 9300 + Math.floor(Math.random() * 600);
const userDataDir = await mkdtemp(join(tmpdir(), "nubesdk-check-"));
const chrome = spawn("chromium", [
	"--headless=new",
	"--disable-gpu",
	"--no-sandbox",
	"--window-size=1280,2000",
	`--remote-debugging-port=${port}`,
	`--user-data-dir=${userDataDir}`,
	"about:blank",
], { stdio: ["ignore", "ignore", "ignore"] });

const cleanup = async () => {
	chrome.kill("SIGTERM");
	await rm(userDataDir, { recursive: true, force: true }).catch(() => {});
};
process.on("exit", () => { chrome.kill("SIGTERM"); });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function targetWsUrl() {
	for (let i = 0; i < 60; i++) {
		try {
			const res = await fetch(`http://127.0.0.1:${port}/json/list`);
			const targets = await res.json();
			const page = targets.find((t) => t.type === "page");
			if (page?.webSocketDebuggerUrl) return page.webSocketDebuggerUrl;
		} catch {}
		await sleep(250);
	}
	throw new Error("CDP nao respondeu; o chromium subiu?");
}

const ws = new WebSocket(await targetWsUrl());
await new Promise((resolve, reject) => {
	ws.onopen = resolve;
	ws.onerror = () => reject(new Error("falha ao abrir o WebSocket do CDP"));
});

let nextId = 1;
const pending = new Map();
const requests = [];
const consoleMessages = [];
const exceptions = [];

ws.onmessage = (raw) => {
	const msg = JSON.parse(raw.data);
	if (msg.id && pending.has(msg.id)) {
		pending.get(msg.id)(msg);
		pending.delete(msg.id);
		return;
	}
	if (msg.method === "Network.requestWillBeSent") {
		requests.push(msg.params.request.url);
	}
	if (msg.method === "Runtime.consoleAPICalled") {
		const text = (msg.params.args ?? []).map((a) => a.value ?? a.description ?? a.type).join(" ");
		consoleMessages.push(`${msg.params.type}: ${text}`);
	}
	if (msg.method === "Runtime.exceptionThrown") {
		const d = msg.params.exceptionDetails;
		exceptions.push(d.exception?.description ?? d.text ?? "erro sem descricao");
	}
};

const send = (method, params = {}) =>
	new Promise((resolve) => {
		const id = nextId++;
		pending.set(id, resolve);
		ws.send(JSON.stringify({ id, method, params }));
	});

await send("Network.enable");
await send("Runtime.enable");
await send("Page.enable");

await send("Page.navigate", { url: targetUrl });
await sleep(6000);

// onfirstinteraction e obrigatorio para location=store, entao o bundle so e buscado
// depois de uma interacao. Tentamos varias formas porque nao esta documentado qual
// evento a plataforma escuta, e evento sintetico pode nao ter isTrusted.
async function pokeUntilSdkBoots() {
	const pokes = [
		["mouse", async () => {
			await send("Input.dispatchMouseEvent", { type: "mouseMoved", x: 640, y: 500 });
			await send("Input.dispatchMouseEvent", { type: "mousePressed", x: 640, y: 500, button: "left", clickCount: 1 });
			await send("Input.dispatchMouseEvent", { type: "mouseReleased", x: 640, y: 500, button: "left", clickCount: 1 });
		}],
		["wheel", async () => {
			await send("Input.dispatchMouseEvent", { type: "mouseWheel", x: 640, y: 500, deltaX: 0, deltaY: 600 });
		}],
		["scroll-gesture", async () => {
			await send("Input.synthesizeScrollGesture", { x: 640, y: 500, yDistance: -600, gestureSourceType: "mouse" });
		}],
		["touch-gesture", async () => {
			await send("Input.synthesizeTapGesture", { x: 640, y: 500 });
		}],
		["keyboard", async () => {
			await send("Input.dispatchKeyEvent", { type: "rawKeyDown", key: "ArrowDown", windowsVirtualKeyCode: 40 });
			await send("Input.dispatchKeyEvent", { type: "keyUp", key: "ArrowDown", windowsVirtualKeyCode: 40 });
		}],
	];
	// Nao dispare Event() sintetico em window/document: handlers da loja chamam
	// e.target.closest(...) e estouram TypeError, poluindo o relatorio com erro nosso.

	for (const [label, poke] of pokes) {
		await poke();
		// da tempo do runtime subir antes de julgar este estimulo
		for (let i = 0; i < 12; i++) {
			await sleep(1000);
			const probe = await send("Runtime.evaluate", {
				expression: "typeof window.nubeSDK",
				returnByValue: true,
			});
			if (probe.result?.result?.value !== "undefined") {
				console.log(`\n>>> window.nubeSDK apareceu depois de: ${label}\n`);
				return label;
			}
		}
		console.log(`  (sem SDK depois de ${label})`);
	}
	console.log("\n>>> window.nubeSDK NUNCA apareceu, mesmo apos todos os estimulos\n");
	return null;
}

const bootTrigger = await pokeUntilSdkBoots();

// espera o worker buscar config, renderizar e mandar o beacon (5s no script)
await sleep(bootTrigger ? 12000 : 2000);

const slot = await send("Runtime.evaluate", {
	expression: `(() => {
    const el = document.querySelector('[data-nubesdk-slot="${SLOT}"]');
    if (!el) return "SLOT AUSENTE NO DOM";
    const html = el.innerHTML.trim();
    return html ? html.slice(0, 1200) : "SLOT VAZIO";
  })()`,
	returnByValue: true,
});

const inputs = await send("Runtime.evaluate", {
	expression: `JSON.stringify([...document.querySelectorAll('input,select,textarea')].map(e => e.name).filter(Boolean).slice(0, 60))`,
	returnByValue: true,
});

const runtime = await send("Runtime.evaluate", {
	expression: `JSON.stringify({
    readyState: document.readyState,
    hasNubeSdk: typeof window.nubeSDK,
    nubeSdkKeys: window.nubeSDK ? Object.keys(window.nubeSDK).slice(0, 20) : null,
    runtimeDiv: !!document.getElementById("nubesdk-runtime"),
    rootDiv: !!document.getElementById("nubesdk-root"),
    workers: typeof Worker,
    slots: document.querySelectorAll('[data-nubesdk-slot]').length
  })`,
	returnByValue: true,
});

console.log("=== runtime do SDK na pagina ===");
console.log("  " + String(runtime.result?.result?.value ?? "?"));

console.log("\n=== requisicoes de interesse ===");
const interesting = [...new Set(requests.filter((u) => INTEREST.some((k) => u.includes(k))))];
console.log(interesting.length ? interesting.map((u) => "  " + u).join("\n") : "  NENHUMA");

console.log("\n=== TODAS as requisicoes ===");
console.log([...new Set(requests)].map((u) => "  " + u.slice(0, 150)).join("\n"));

console.log(`\n=== slot ${SLOT} ===`);
console.log("  " + String(slot.result?.result?.value ?? "?").replace(/\n/g, "\n  "));

console.log("\n=== names de campos no DOM ===");
console.log("  " + String(inputs.result?.result?.value ?? "[]"));

console.log("\n=== console ===");
console.log(consoleMessages.length ? consoleMessages.slice(0, 25).map((m) => "  " + m).join("\n") : "  (vazio)");

console.log("\n=== excecoes ===");
console.log(exceptions.length ? exceptions.slice(0, 10).map((m) => "  " + m).join("\n") : "  (nenhuma)");

console.log(`\ntotal de requisicoes observadas: ${requests.length}`);

await cleanup();
process.exit(0);
