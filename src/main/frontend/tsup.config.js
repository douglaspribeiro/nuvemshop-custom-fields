import { defineConfig } from "tsup";

// APP_ORIGIN e resolvido em build time: o script roda na loja do lojista e precisa
// saber onde buscar a config. Sem isso o bundle de staging aponta para producao.
const APP_ORIGIN = process.env.APP_BASE_URL ?? "https://campos-personalizados.wzhub.pro";

export default defineConfig({
  entry: {
    "nuvemshop-storefront-sdk": "src/storefront/main.tsx",
    // Nome novo de proposito: nao colide com /assets/nuvemshop-checkout.js, que esta
    // em producao. A troca no Partner Portal e um passo deliberado, nao efeito de build.
    "nuvemshop-checkout-sdk": "src/checkout/main.tsx",
  },
  format: ["esm"],
  target: "esnext",
  clean: true,
  minify: true,
  bundle: true,
  sourcemap: false,
  splitting: false,
  skipNodeModulesBundle: false,
  define: {
    __APP_ORIGIN__: JSON.stringify(APP_ORIGIN),
  },
  esbuildOptions(options) {
    options.alias = {
      "@tiendanube/nube-sdk-jsx/dist/jsx-runtime": "@tiendanube/nube-sdk-jsx/jsx-runtime",
    };
  },
  // Nome estavel: a URL do script fica cadastrada no Partner Portal.
  outExtension: () => ({ js: ".js" }),
});
