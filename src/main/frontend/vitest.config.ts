import { defineConfig } from "vitest/config";

export default defineConfig({
  esbuild: {
    jsx: "automatic",
    jsxImportSource: "@tiendanube/nube-sdk-jsx",
  },
  define: {
    __APP_ORIGIN__: JSON.stringify("https://app.test"),
  },
  test: {
    globals: true,
    environment: "node",
  },
});
