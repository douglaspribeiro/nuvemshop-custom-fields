import { spawnSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

// Um mesmo instante UTC identifica todos os artefatos do build, ate o segundo.
const stamp = process.env.NCF_SCRIPT_BUILD_TIME ?? new Date().toISOString().replace(/\D/g, "").slice(0, 14);
if (!/^\d{14}$/.test(stamp)) throw new Error("NCF_SCRIPT_BUILD_TIME deve conter 14 digitos UTC");
const result = spawnSync(process.execPath, [fileURLToPath(new URL("./node_modules/tsup/dist/cli-default.js", import.meta.url))], {
    cwd: fileURLToPath(new URL(".", import.meta.url)), stdio: "inherit",
    env: { ...process.env, NCF_SCRIPT_BUILD_TIME: stamp },
});
if (result.status !== 0) process.exit(result.status ?? 1);

function generate(source, output, name) {
    const content = readFileSync(new URL(source, import.meta.url), "utf8");
    if (!content.includes("__NCF_SCRIPT_VERSION__")) throw new Error(`Marcador ausente: ${source}`);
    writeFileSync(new URL(`./dist/${output}`, import.meta.url), content.replace("__NCF_SCRIPT_VERSION__", `${name}.js?v=${stamp}`));
}
generate("../resources/static/assets/nuvemshop-personalizer.js", "nuvemshop-personalizer.js", "legado-js");
generate("../resources/static/assets/nuvemshop-personalizer.js", "partner-legado-js.js", "legado-js");
generate("../resources/static/assets/nuvemshop-personalizer.js", "partner-legadov2.js", "legadov2");
generate("../resources/static/assets/nuvemshop-checkout.js", "nuvemshop-checkout.js", "checkout-legacy");
console.log(`Versoes de scripts geradas em UTC: ${stamp}`);
