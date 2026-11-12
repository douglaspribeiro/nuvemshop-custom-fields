# LAB Cafe — verificação do fluxo manual em 05/11/2026

## Mitigação urgente: retirar a trava do checkout

Após novo relato de cliente impedido de continuar mesmo após preencher campos, a prioridade passou a ser liberar as vendas de todas as lojas. O app de checkout não registra mais validação obrigatória; envia `has_cart_validation=false`, limpa seu estado de validação para `success` e remove a mensagem antiga. O resumo dos dados já existentes continua visível. A obrigatoriedade continua nos formulários da vitrine.

Isso permite prosseguir também com pedidos manuais sem respostas. Não acrescenta preenchimento no checkout nem dispensa seletiva por origem. O trabalho experimental de coleta/autenticação/persistência no checkout foi retirado desta alteração; não há nova API, tabela ou migração para esta mitigação.

156 testes frontend passaram, além de TypeScript, build ESM/IIFE e verificação do bundle. A execução local do novo bundle com a linha extraída do checkout real `2112337901` retornou `success` e nenhum slot de bloqueio. São testes de código; não comprovam publicação ou conclusão de pedido real.

**Publicação necessária:** enviar `src/main/frontend/dist/nuvemshop-checkout-sdk.js` para o script de checkout `checkout-v1` (ID observado 7145), que neste exemplo ainda serve versão 9. Não basta implantar o backend. Conferir o novo arquivo servido e recarregar uma sessão de checkout antes de afirmar desbloqueio em produção. A sessão não tem navegador conectado para publicar no Partner Portal.

O restante do documento mantém o diagnóstico e as alternativas investigadas antes dessa mitigação.

Loja 959276. Investigação somente leitura; nenhum pedido, pagamento, permissão ou script publicado foi alterado.

## Evidência de produção

Consultas autenticadas realizadas pelo servidor usando a credencial existente, sem registrar tokens ou dados pessoais:

- `GET /v1/959276/draft_orders?per_page=10`: HTTP 403, `Missing required scope: read_draft_orders`.
- `GET /v1/959276/orders?per_page=15`: HTTP 200, 15 pedidos recentes. `order_origin` é nulo ou string vazia em todos. Esse resultado não permite classificar pedidos como manuais.
- Nessas respostas, `checkout_app_blocked=false`, `checkout_enabled=true` e mensagens de validação vazias. Isso não reproduz nem refuta o bloqueio do Worker no navegador e não identifica o pedido do chamado.

## Superfície documentada

- [Estado Order](https://tiendanube.dev/apps/nube-sdk/state/order): `order` só fica disponível no sucesso, após conclusão. Não há flag documentado de pedido manual no estado Cart/Checkout consultado, nem nos tipos instalados `@tiendanube/nube-sdk-types`.
- [Eventos do carrinho](https://tiendanube.dev/apps/nube-sdk/events/cart): `cart:add` aceita properties e `cart:remove` remove itens. Não foi encontrado comando documentado de edição de properties de uma linha existente. Remover/recriar itens não está validado para pedidos manuais, seus preços e descontos.
- [Eventos Order](https://tiendanube.dev/es-AR/apps/nube-sdk/events/order): `order:add:extra` permite persistir metadados chave/valor no pedido. É uma alternativa documentada para investigar o preenchimento no checkout, mas não equivale a editar `products[].properties`. Substitui o objeto extra inteiro a cada envio.
- [Draft Order](https://tiendanube.dev/es-AR/api/resources/2025-03/draft-order): rascunhos contêm checkout_url e products[].properties. A presença desses dados não comprova edição pelo cliente ou acesso com o token atual.
- `OrderPersonalization` atualmente lê as properties dos produtos, sem consumir order.extra. Adotar extra exige integração explícita no relatório e validação da persistência/visibilidade ao lojista.

## Conclusão e pendência concreta

Não há base para implementar dispensa automática apenas para pedidos manuais com os sinais atualmente confirmados. Também não está comprovada a gravação de respostas em um pedido manual já existente.

Para concluir a reprodução, obter o link enviado ao cliente de um pedido manual afetado, preferencialmente um pedido de teste. Conferir etapa, estado SDK, slots disponíveis e comportamento de persistência sem pagar/concluir um pedido real. Se for necessário consultar rascunhos via Admin API, será necessária autorização adicional `read_draft_orders`; não ampliar permissões automaticamente.

Candidato para uma solução que preserve obrigatoriedade: campos no checkout para itens com respostas ausentes, com persistência suportada e confirmação antes de liberar o pagamento. `order:add:extra` é candidato técnico, não uma solução já validada. Não prometer recuperação de valores nunca informados.

## Exemplo fornecido pelo usuário e verificado em 05/11

Pedido/carrinho `2112337901`, loja 959276. O token de acesso do link não é registrado neste documento.

- O GET do checkout respondeu com a etapa `start` e o estado inicial do NubeSDK. Há uma linha: produto `44960115`, variante `125804540`, quantidade 3, `properties=[]`.
- A configuração real consultada internamente no backend em produção está habilitada e não pausada. Exige o SELECT **Molido o en Grano?**.
- `GET /orders/2112337901` respondeu: `number=0`, `status=open`, `payment_status=pending`, `order_origin` vazio, sem chaves em `extra`. O campo de bloqueio da API está falso; ele não reflete necessariamente a validação executada pelo Worker no navegador.
- O checkout entrega `checkout-v1/9.js`. A inspeção do bundle publicado confirma a regra de bloqueio e a renderização apenas de texto no slot `before_line_items`.
- Uma execução local do bundle publicado, com a linha do carrinho extraída do HTML e uma resposta de configuração simulada com o SELECT obrigatório confirmado no backend, produziu `cart:validate.status=fail` e a mensagem espanhola para voltar à loja. Não houve comunicação de escrita com a plataforma nessa reprodução. Isso valida a lógica do bundle; não substitui o clique num navegador real.

### `from_store=1` não identifica exclusivamente o pedido manual

O estado SDK recebe `location.queries.from_store=1`. Entretanto, o próprio código público do checkout (`_app-537d774ac557427f.js`, versão 2.3.1143) constrói URLs de navegação com `?from_store=1` no método genérico de checkout. Não é base para dispensar campos somente em pedidos manuais. Também é um parâmetro de URL que o visitante pode alterar.

### Persistência ainda não validada

O adaptador efetivamente servido (`register-0.60.0.min.js`) reconhece `order:add:extra` e substitui `order.extra` no estado SDK. Essa observação, isoladamente, não comprova gravação no servidor ou visibilidade ao lojista antes da conclusão deste pedido. Não foi enviado esse evento ao pedido real.

O problema está localizado: falta um caminho de preenchimento no checkout quando o pedido chega sem properties. Uma dispensa automática baseada em `from_store` foi descartada. A alternativa de campos no checkout continua dependendo de validar a gravação suportada e a integração com o relatório; não foi publicada uma correção nem alterado o pedido. Não há navegador conectado disponível nesta sessão para essa verificação de interface.


## Confirmação da publicação da mitigação

Após o usuário informar a publicação, uma nova leitura do checkout `2112337901` confirmou `checkout-v1/10.js`, substituindo a versão 9 nesse HTML. O SHA-256 do arquivo efetivamente servido é `6d2aaf7c05f8f0e02710a7a2d402aa8d8603ba6b88ca249bb5ed98dfe0d5c049`, idêntico ao bundle local sem bloqueio.

O bundle baixado foi executado localmente com a linha extraída do checkout atualizado: três unidades do produto `44960115`, sem properties. Nos cenários sem respostas, com respostas, falha de consulta e estado antigo de bloqueio, incluindo eventos `checkout:ready` e `cart:update`, emitiu apenas validação `success`, desativou `has_cart_validation` e não alterou os itens. Não renderizou o slot de bloqueio.

A publicação/entrega do arquivo na LAB Cafe está confirmada por HTTP. Não houve teste de clique no navegador, avanço de etapa, pedido ou pagamento: a sessão continua sem navegador conectado. Não generalizar essa consulta como confirmação de propagação para todas as lojas.


## Teste real com Playwright após publicação

A pedido explícito do usuário, foi usado Playwright com Chrome headless no Linux, independente da integração Computer Use indisponível. O teste abriu o checkout real, aguardou os scripts e clicou em **Continuar para el pago**.

- A rede carregou `checkout-v1/10.js` com HTTP 200; houve consulta ao endpoint de estilo do aplicativo.
- Nenhuma mensagem de bloqueio por personalização foi encontrada antes ou depois do clique.
- O botão de continuar estava habilitado.
- O checkout permaneceu na etapa inicial e exibiu **Elegí una forma de entrega.** Nenhuma opção de entrega estava selecionada; o teste não escolheu frete/retirada nem modificou condições de entrega do cliente.
- Não se alcançou a etapa de pagamento. Nenhum pedido ou pagamento foi concluído; o teste também bloqueava rotas de pagamento/conclusão.
- Foram observados erros JavaScript `$ is not defined`; a origem não foi atribuída nesta execução e eles não equivalem à antiga mensagem do nosso bloqueio.

Este teste confirma a ausência da antiga mensagem e a interação com o botão no navegador real. O aceite de avanço até pagamento continua pendente da escolha legítima da entrega pelo cliente/lojista; não afirmar que a compra foi concluída ou que todo o fluxo comercial foi validado.

## Pedido #15103 e possibilidade de integração — 12/11/2026

Consulta somente leitura à API da loja 959276: o pedido #15103 tem origem `form` (rascunho) e o item do produto 47290558 não possui propriedades de personalização. O texto “electrica” exibido na impressão está na observação da loja (`owner_note`), não na resposta do campo. A configuração atual do produto possui o campo obrigatório “¿Molido o en Grano?”.

Para comparação, o pedido #15105, do mesmo produto e com origem `store`, registra a propriedade “¿Molido o en Grano?” com valor “Espresso”. Essa comparação demonstra o registro na API, mas não comprova que a impressão nativa da Nuvemshop mostre essa propriedade.

A [documentação de pedidos rascunho](https://nuvemshop.dev/en-US/docs-api/resources/unstable/draft-order) descreve a criação por API com propriedades nos produtos. Uma possível evolução seria oferecer no aplicativo a criação de rascunhos com o preenchimento dos campos por item. A consulta rápida não confirmou como alterar propriedades em um rascunho criado no painel da Nuvemshop, nem se as propriedades aparecem na impressão; ambos exigem validação antes de prometer a solução.

A integração exigiria avaliar as permissões `read_draft_orders` e `write_draft_orders` conforme as operações escolhidas. [A documentação de autenticação](https://nuvemshop.dev/en-US/docs-api/authentication) informa que mudanças de escopo exigem reinstalação pelas lojas. Em 05/11, a leitura de rascunhos da LAB Cafe retornou 403 por falta de `read_draft_orders`. Nenhuma permissão foi alterada, integração implementada ou pedido modificado nesta investigação.
