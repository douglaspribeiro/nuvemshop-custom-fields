# Scripts NubeSDK — Campos Personalizados

Bundles NubeSDK do app, compilados com o toolchain oficial (`tsup`).

## Entradas

| Bundle | Origem | Slot | `location` no Partner Portal |
| --- | --- | --- | --- |
| `nuvemshop-storefront-sdk.js` | `src/storefront/main.tsx` | `before_product_detail_add_to_cart` | `store` |
| `nuvemshop-checkout-sdk.js` | `src/checkout/main.tsx` | `after_line_items` | `checkout` |

O script legado `../resources/static/assets/nuvemshop-personalizer.js` (DOM, sem SDK)
**continua registrado** e não é gerado aqui. É exigência da homologação Nuvemshop:

> "Para aplicativos que possuem um JavaScript instalado no storefront da loja, é necessário
> manter ambos os scripts configurados simultaneamente — o script legado (sem o SDK) e o novo
> script adaptado ao NubeSDK. [...] Os dois scripts devem coexistir até que a adoção do
> NubeSDK seja integral em todos os temas."

Isso é o cenário *"transition script"* da documentação. A regra que vem com ele: **o mesmo
comportamento não pode rodar duas vezes** — em loja com SDK ativo os campos apareceriam
duplicados. A supressão condicional está implementada: o legado só renderiza quando
`window.nubeSDK` não existe ou quando o SDK não desenhou os campos em 2,5s.

O endpoint JSONP `/public/stores/{id}/personalization.js` foi removido: o script legado usa
`/personalization`, `/style` e `/public/script-events`, e nunca chamou o JSONP.

## Pré-requisito: a loja precisa estar liberada (whitelist)

**A loja precisa ser habilitada pela Nuvemshop para receber o runtime do NubeSDK.** Sem isso
nada abaixo funciona, e a falha é totalmente silenciosa: o script fica `active` e associado, o
arquivo responde 200 na CDN, e mesmo assim nunca é executado.

Como diagnosticar (numa página de produto):

| Sintoma | Loja liberada | Loja não liberada |
| --- | --- | --- |
| `window.nubeSDK` no console | objeto | `undefined` |
| `nsk-cdn-static.tiendanube.com/vanilla-adapter-*.min.js` | carrega | ausente |
| requisição para `apps-scripts.tiendanube.com/.../<script>.js` | acontece | nenhuma |

Atenção: `#nubesdk-root`, `#nubesdk-runtime`, os elementos `[data-nubesdk-slot]` e
`nube_sdk_product_state: "full"` aparecem **mesmo em loja não liberada** — não servem como
sinal de que o SDK está ativo.

**Como liberar a loja de teste**: abrir chamado com o suporte pelo **Painel do Partner**.
Confirmado pelo time de homologação em 20/08/2026 — o formulário de SDK tag que circula na
documentação não é o canal correto.

Isto vale apenas para **loja de teste**. Em produção o SDK é ativado pela Nuvemshop em
rollout canário depois da homologação — não se pede loja por loja.

Documentação: [Finished Migrating — What's Next?](https://dev.nuvemshop.com.br/docs/applications/nube-sdk/after-migration)
(o Migration Guide não menciona nada disso) e
[nube-sdk#370](https://github.com/TiendaNube/nube-sdk/issues/370).

### Checklist oficial antes de pedir ativação em produção

- App validado em loja de teste com a SDK flag, em **todos** os temas (vitrine e checkout)
- Flag **"Uses Nube SDK"** habilitada no script — é ela que diz à plataforma que o script é SDK
- Script legado/transição **não executa mais** nenhum comportamento já migrado para o SDK
- Bundle de produção enviado e *development mode* desligado
- Pedido de homologação aberto ou atualizado

O terceiro item é obrigatório, não opcional: renderizar os campos no legado **e** no SDK causa
UI duplicada e eventos conflitantes. O cenário "transition script" (parte no SDK, legado como
fallback para temas não migrados) é suportado — o que não pode é o mesmo comportamento rodar
duas vezes.

Para checar automaticamente: `scripts/check-nubesdk-storefront.mjs <url-do-produto>`
(rode com o node pinado: `src/main/frontend/node/node`). Ele reporta o estado do runtime,
todas as requisições, o conteúdo do slot, console e exceções. O script de vitrine é
registrado com `event=onfirstinteraction` (obrigatório para `location=store`), então a
ferramenta sintetiza interação — sem isso o bundle nunca é buscado e o resultado é um falso
negativo.

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
