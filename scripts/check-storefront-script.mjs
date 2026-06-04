import { spawn } from "node:child_process";
import { mkdtemp, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const WebSocket = require("ws");

const targetUrl = process.argv[2];
const injectLocal = process.argv.includes("--inject-local");

if (!targetUrl) {
  console.error("Usage: node scripts/check-storefront-script.mjs <product-url>");
  process.exit(2);
}

const port = 9224 + Math.floor(Math.random() * 1000);
const userDataDir = await mkdtemp(join(tmpdir(), "nuvemshop-cdp-"));
const chrome = spawn("chromium", [
  "--headless",
  "--disable-gpu",
  "--no-sandbox",
  `--remote-debugging-port=${port}`,
  `--user-data-dir=${userDataDir}`,
  "about:blank"
], {
  stdio: ["ignore", "pipe", "pipe"]
});

chrome.stderr.on("data", (chunk) => {
  const line = chunk.toString();
  if (line.includes("DevTools listening")) {
    return;
  }
});

process.on("exit", () => {
  chrome.kill("SIGTERM");
});

let seq = 0;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const getJson = async (url, options) => (await fetch(url, options)).json();

async function waitForChrome() {
  const deadline = Date.now() + 8000;
  while (Date.now() < deadline) {
    try {
      await getJson(`http://127.0.0.1:${port}/json/version`);
      return;
    } catch {
      await sleep(250);
    }
  }
  throw new Error("Chromium did not expose DevTools in time");
}

function connect(url) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url);
    ws.once("open", () => resolve(ws));
    ws.once("error", reject);
  });
}

function command(ws, method, params = {}) {
  const id = ++seq;
  ws.send(JSON.stringify({ id, method, params }));
  return new Promise((resolve, reject) => {
    const onMessage = (data) => {
      const msg = JSON.parse(data.toString());
      if (msg.id !== id) {
        return;
      }
      ws.off("message", onMessage);
      if (msg.error) {
        reject(new Error(JSON.stringify(msg.error)));
      } else {
        resolve(msg.result || {});
      }
    };
    ws.on("message", onMessage);
  });
}

await waitForChrome();

const page = await getJson(`http://127.0.0.1:${port}/json/new?${encodeURIComponent(targetUrl)}`, {
  method: "PUT"
});
const ws = await connect(page.webSocketDebuggerUrl);
const networkLog = [];
const exceptions = [];
const consoleLog = [];
const requestUrls = new Map();

function isInterestingUrl(url) {
  return url.includes("apps-scripts")
    || url.includes("chlorine")
    || url.includes("script-events")
    || url.includes("personalization")
    || url.includes("personalizer");
}

ws.on("message", (data) => {
  const msg = JSON.parse(data.toString());
  if (msg.method === "Network.requestWillBeSent") {
    const url = msg.params.request.url;
    requestUrls.set(msg.params.requestId, url);
    if (isInterestingUrl(url)) {
      networkLog.push(`REQ ${msg.params.request.method} ${url}`);
    }
  }
  if (msg.method === "Network.responseReceived") {
    const url = msg.params.response.url;
    if (isInterestingUrl(url)) {
      networkLog.push(`RES ${msg.params.response.status} ${url}`);
    }
  }
  if (msg.method === "Network.loadingFailed") {
    const url = requestUrls.get(msg.params.requestId) || msg.params.requestId;
    if (isInterestingUrl(url)) {
      networkLog.push(`FAIL ${msg.params.errorText} ${url}`);
    }
  }
  if (msg.method === "Runtime.exceptionThrown") {
    exceptions.push(msg.params.exceptionDetails.text);
  }
  if (msg.method === "Runtime.consoleAPICalled") {
    const args = (msg.params.args || []).map((arg) => arg.value || arg.description || "").join(" ");
    if (args.includes("ncf") || args.includes("personalization") || args.includes("chlorine")) {
      consoleLog.push(`${msg.params.type} ${args}`);
    }
  }
});

await command(ws, "Page.enable");
await command(ws, "Runtime.enable");
await command(ws, "Network.enable");
await sleep(5000);
await command(ws, "Input.dispatchMouseEvent", { type: "mouseMoved", x: 280, y: 280 });
await command(ws, "Input.dispatchMouseEvent", {
  type: "mousePressed",
  x: 280,
  y: 280,
  button: "left",
  clickCount: 1
});
await command(ws, "Input.dispatchMouseEvent", {
  type: "mouseReleased",
  x: 280,
  y: 280,
  button: "left",
  clickCount: 1
});
await command(ws, "Input.dispatchKeyEvent", {
  type: "keyDown",
  key: "Tab",
  windowsVirtualKeyCode: 9,
  nativeVirtualKeyCode: 9
});
await command(ws, "Input.dispatchKeyEvent", {
  type: "keyUp",
  key: "Tab",
  windowsVirtualKeyCode: 9,
  nativeVirtualKeyCode: 9
});
await command(ws, "Runtime.evaluate", {
  expression: "window.scrollTo(0, 800); document.dispatchEvent(new Event('mousemove')); document.body.click();"
});
await sleep(9000);

if (injectLocal) {
  const scriptSource = await readFile(resolve("src/main/resources/static/assets/nuvemshop-personalizer.js"), "utf8");
  await command(ws, "Runtime.evaluate", {
    expression: `document.querySelectorAll(".ncf-personalization").forEach((node) => node.remove());
      document.querySelectorAll("form").forEach((form) => delete form.dataset.ncfInjected);`
  });
  await command(ws, "Runtime.evaluate", {
    expression: `${JSON.stringify(scriptSource + "\n//# sourceURL=ncf-local-injected.js")}`,
  }).then((source) => command(ws, "Runtime.evaluate", { expression: source.result.value }));
  await sleep(5000);
}

const result = await command(ws, "Runtime.evaluate", {
  returnByValue: true,
  expression: `({
    hasNcf: !!document.querySelector(".ncf-personalization"),
    ncfText: document.querySelector(".ncf-personalization")?.innerText || null,
    scriptCount: document.scripts.length,
    scripts: Array.from(document.scripts)
      .map((s) => s.src)
      .filter((src) => src.includes("apps-scripts") || src.includes("chlorine") || src.includes("personalizer") || src.includes("scriptv")),
    lsStore: window.LS && window.LS.store && window.LS.store.id,
    lsProduct: window.LS && window.LS.product && window.LS.product.id,
    form: !!document.querySelector("#product_form"),
    containsFieldText: document.body.innerText.includes("Nome Perso."),
    ncfParentClass: document.querySelector(".ncf-personalization")?.parentElement?.className || null,
    ncfNextClass: document.querySelector(".ncf-personalization")?.nextElementSibling?.className || null,
    ncfRect: document.querySelector(".ncf-personalization")?.getBoundingClientRect().toJSON() || null,
    quantityRect: document.querySelector(".js-quantity, [class*='quantity']")?.getBoundingClientRect().toJSON() || null
  })`
});

console.log("NETWORK_LOG");
console.log(networkLog.join("\n") || "none");
console.log("EXCEPTIONS");
console.log(exceptions.join("\n") || "none");
console.log("CONSOLE");
console.log(consoleLog.join("\n") || "none");
console.log("RESULT");
console.log(JSON.stringify(result.result.value, null, 2));

ws.close();
chrome.kill("SIGTERM");
