# Diagnóstico do tema Patagonia e scripts da Tiendanube

Atualizado em 29/09/2026.

## Contexto

A loja **Store by Pauli** (Store ID `7278258`) usa o tema **Patagonia**. O tema não entrega o evento `cart:before_update` usado pelo fluxo NubeSDK para interceptar o botão nativo e anexar `properties` ao carrinho.

O SDK registra `storefront.sdk.patagonia_requires_transition_script` quando encontra o Patagonia. Esse evento é esperado e significa que o script legado/transição precisa assumir a exibição dos campos e o `cart:add` personalizado. O evento antigo `storefront.sdk.gate_unsupported_theme` indica bundle SDK antigo.

## Correção implementada

- `nuvemshop-personalizer.js` continua sendo o script de transição, sem NubeSDK.
- Ao identificar explicitamente o tema Patagonia, ele carrega `/assets/nuvemshop-patagonia.js`.
- O adaptador desenha os campos junto ao botão do produto, valida valores obrigatórios, variante e quantidade e envia `cart:add` com `properties`.
- O botão de compra expressa é ocultado somente enquanto há personalização ativa, para não ignorar a validação.
- Outros temas continuam no fluxo anterior.
- O script `nuvemshop-patagonia.js` fica hospedado no servidor e não é cadastrado como um script separado no Partner Portal.

Arquivos principais:

- `src/main/resources/static/assets/nuvemshop-personalizer.js`
- `src/main/frontend/src/storefront/patagonia.ts`
- `src/main/frontend/src/storefront/patagonia-entry.ts`
- `src/main/frontend/dist/nuvemshop-patagonia.js`

## Scripts no Partner Portal

Os scripts associados à loja são:

| ID | Nome | Local | Evento | NubeSDK |
|---:|---|---|---|---|
| `9344` | Legado-js | `store` | `onfirstinteraction` | desativado |
| `9319` | store-front-v5 | `store` | `onfirstinteraction` | ativado |
| `7145` | checkout-v1 | `checkout` | `onload` | ativado |

O `Legado-js` recebeu uma nova versão ativa (versão 3). O Storefront SDK deve permanecer publicado separadamente. A ação **Reinstalar scripts** apenas associa os IDs à loja; ela não publica uma nova versão de arquivo.

## Diagnóstico atual

Após a reinstalação, o painel mostrou os três scripts associados e ativos. Na inspeção pública da vitrine, o `store-front-v5` foi observado carregando, mas não apareceu uma requisição para `legado-js` nem para `nuvemshop-patagonia.js`.

Isso significa que o problema atual está na entrega/propagação do script legado pela Tiendanube, e não no cache do adaptador Patagonia ou na configuração do produto. Sem o `legado-js`, o SDK consegue apenas registrar `patagonia_requires_transition_script`; os campos não podem ser renderizados.

## Como validar quando a Tiendanube propagar o script

1. Abrir um produto da loja em janela anônima.
2. Na aba Network, filtrar por `apps-scripts.tiendanube.com`.
3. Confirmar as requisições para `store-front-v5` e para `legado-js`.
4. Confirmar a requisição para `https://campos-personalizados.wzhub.pro/assets/nuvemshop-patagonia.js`.
5. Verificar os eventos:

```text
storefront.sdk.patagonia_transition_rendered
storefront.sdk.patagonia_transition_add
storefront.sdk.patagonia_transition_success
```

6. Testar campo obrigatório, variante, quantidade, carrinho e pedido.

Se aparecer somente `patagonia_requires_transition_script`, o SDK carregou, mas o legado ainda não chegou à página. Se aparecer `patagonia_transition_rendered`, o adaptador foi iniciado.

## Chamado enviado à Tiendanube

O chamado foi enviado ao suporte para parceiros pelo Portal de Parceiros, com o assunto:

```text
Falha na entrega do script legado 9344 na loja 7278258 – tema Patagonia
```

O chamado informa que os scripts `9344`, `9319` e `7145` estão associados, que o `store-front-v5` carrega e que o `legado-js` não aparece na aba Network. A solicitação é verificar a instalação automática e a propagação do script `9344` para a loja `7278258`.

## E-mail de suporte do aplicativo

Respostas do backoffice geram e-mail depois do commit da transação quando o AWS SES está configurado. As propriedades são:

```text
AWS_SES_SMTP_HOST
AWS_SES_SMTP_PORT
AWS_SES_SMTP_USERNAME
AWS_SES_SMTP_PASSWORD
AWS_SES_FROM_EMAIL
```

Sem configuração completa, o log é `support.email_provider_not_configured`. A resposta do chamado permanece salva mesmo se o envio falhar.

## Validação automatizada

O frontend possui testes para Patagonia, roteamento do script legado, campos obrigatórios, variante, quantidade, falha, timeout e navegação. A suíte frontend validada contém 40 testes passando. O bundle também foi incluído no JAR publicado.
