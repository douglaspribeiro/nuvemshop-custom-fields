# Scripts NubeSDK — Campos Personalizados

Bundles NubeSDK do app, compilados com o toolchain oficial (`tsup`).

## Entradas

| Bundle | Origem | Slot | `location` no Partner Portal |
| --- | --- | --- | --- |
| `nuvemshop-storefront-sdk.js` | `src/storefront/main.tsx` | `before_product_detail_add_to_cart` | `store` |
| `nuvemshop-checkout-sdk.js` | `src/checkout/main.tsx` | `after_line_items` | `checkout` |

O script legado `../resources/static/assets/nuvemshop-personalizer.js` (DOM, sem SDK)
**continua registrado** e não é gerado aqui. A Nuvemshop exige que os dois coexistam até
a adoção do NubeSDK ser integral em todos os temas.

## Como a personalização chega ao pedido

`cart:add` aceita `properties`, mas o add-to-cart **nativo** não. Então:

1. `config:set { handle_cart_before_update: true }` — sem isso o gate nunca dispara.
2. Campos renderizados no slot da página de produto; valores capturados via `onChange`.
3. `cart:before_update` (action `ADD`) → valida → responde `proceed: false`.
4. `cart:add` com `properties` como **objeto** (o bridge da loja faz `Object.entries`;
   um array geraria `properties[0]`).

Referência: [TiendaNube/nube-sdk#407](https://github.com/TiendaNube/nube-sdk/issues/407)
(mesmo caso de uso, validado e fechado pela Nuvemshop) e
[#394](https://github.com/TiendaNube/nube-sdk/issues/394) (pedido de esconder o botão
nativo, fechado com "use `cart:before_update`").

### Guardas e limitações

- **Anti-loop**: nosso `cart:add` redispara `cart:before_update`. `pendingSelfAdds`
  libera esse segundo evento; `cart:add:success`/`:fail` zeram o contador.
- **Fail-open**: o gate tem 5s para responder e a plataforma prossegue no timeout.
  Qualquer dúvida (config não carregada, produto diferente, sem valores) → `proceed: true`.
  Bloquear a compra é pior que perder a personalização.
- **Produto já no carrinho**: o bridge resolve o `cart:add` como bump de quantidade via
  `LS.changeQuantity` e **descarta `properties`**. Não há contorno pelo SDK; o caso é
  reportado em `/public/script-events` com `reason=reissue_properties_dropped_item_in_cart`.
- **Tema patagonia**: não recebe `cart:before_update` ([#394]). O script não renderiza
  nada nesse tema — um formulário que não envia nada é pior que nenhum formulário.

## Comandos

Rode pelo Maven: o `frontend-maven-plugin` baixa Node 22.14.0 / npm 10.9.2 em
`./node/` e usa essa versão, não a do sistema.

```bash
mvn generate-resources        # install + typecheck + test + build
```

Direto com o npm pinado (a partir da raiz do repo):

```bash
src/main/frontend/node/node src/main/frontend/node/npm/bin/npm-cli.js --prefix src/main/frontend test
```

Scripts disponíveis: `test`, `test:watch`, `typecheck`, `build`, `dev`.
`npm run dev` faz watch + serve `dist` em :8090 — use essa URL como *development url*
no Partner Portal.

`APP_BASE_URL` é embutido no bundle em build time (`__APP_ORIGIN__`). Via Maven:
`mvn package -Dapp.base.url=https://staging.exemplo.com`.

## Integração com o Maven

`frontend-maven-plugin` baixa Node localmente e roda `install` / `test` / `build` na fase
`generate-resources`; os bundles são copiados para `target/classes/static/assets`.
Para build só-Java: `mvn package -Dskip.frontend=true` (não regenera os bundles).
