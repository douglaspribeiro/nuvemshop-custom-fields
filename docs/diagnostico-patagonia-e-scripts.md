# Diagnóstico do tema Patagonia e scripts da Tiendanube

Atualizado em 30/09/2026 após o retorno do suporte no chamado #8216779.

A loja Store by Pauli (`7278258`) usa o tema Patagonia. Em 29/09/2026,
a Nuvemshop confirmou por e-mail que a plataforma escolhe um dos scripts:
SDK no Patagonia e legado nos outros layouts. A associação dos dois scripts
não significa execução simultânea. A orientação é manter ambos cadastrados
e incorporar o comportamento de personalização ao SDK.

## Diagnóstico corrigido

A ausência do `Legado-js` (`9344`) no Patagonia é esperada, conforme o suporte.
O diagnóstico anterior de falha de propagação estava incorreto.

Nosso Worker tinha uma regra específica que, ao encontrar Patagonia, limpava
o slot e registrava `patagonia_requires_transition_script` sem buscar os campos.
Como o legado não era executado, a loja ficava sem personalização.
Essa regra foi removida: o SDK renderiza no slot
`before_product_detail_add_to_cart`, valida os campos obrigatórios e reenvia
`cart:add` com as propriedades, a variante e a quantidade do evento nativo.

Não foi importado o adaptador DOM para o Worker. O NubeSDK não disponibiliza
`window`, `document` ou injeção de scripts externos dentro do Worker.
O asset `nuvemshop-patagonia.js` permanece como código histórico de transição;
a solução SDK não o carrega nem depende dele.

## Publicação e validação pendentes

### Alterações realizadas no código

- Removemos a dependência do `9344` quando o tema é Patagonia; a ausência do
  legado nesse tema é comportamento esperado da plataforma.
- O SDK renderiza os campos no slot `before_product_detail_add_to_cart`.
- O SDK valida campos obrigatórios e reenvia `cart:add` com propriedades, variante
  e quantidade do evento nativo.
- O fluxo usa eventos do NubeSDK e não acessa `window`, `document` nem injeta DOM
  no Worker. O asset histórico `nuvemshop-patagonia.js` não é carregado.

### O que fazer no Partner Portal

1. Gerar o bundle de produção do frontend e conferir o arquivo antes do upload.
2. Criar uma nova versão do script **Storefront SDK `9319`**, preservando o
   identificador e as permissões/configuração atuais.
3. Publicar a nova versão e associá-la a uma loja de teste Patagonia.
4. Reinstalar/atualizar o script na loja de teste para receber a versão publicada.

Não é necessário publicar uma nova versão do legado `9344` para o Patagonia: a
plataforma não executa os dois scripts simultaneamente. Mantenha o `9344` para os
demais temas. O checkout `7145` permanece inalterado.

Reinstalar associações sem publicar uma nova versão não atualiza o bundle entregue.
O checkout `7145` e o legado `9344` continuam cadastrados conforme o suporte.

Antes de liberar o bundle na loja:

1. Testar o SDK isolado na loja de teste com o tema Patagonia.
2. Confirmar a exibição dos campos pelo slot do SDK, sem script legado.
3. Clicar em adicionar ao carrinho com o campo obrigatório vazio e confirmar bloqueio.
4. Confirmar o evento `cart:before_update` depois da interação com o botão nativo.
   O app registra uma vez `storefront.sdk.cart_before_update_received`.
   Sua ausência em um beacon anterior à interação não comprova incompatibilidade do tema.
5. Testar variante, quantidade e envio de `properties` no carrinho.
6. Confirmar as propriedades no pedido; incluir produto já presente no carrinho,
   pois há uma limitação previamente observada no bridge ao aumentar a quantidade.

A remoção da regra resolve a dependência indevida do legado. A execução real do gate
no Patagonia ainda precisa ser comprovada; os testes locais simulam o evento.
Se o tema não entregar o evento, a plataforma precisa indicar o mecanismo SDK
suportado antes de considerarmos a personalização validada em produção.

Referências oficiais:

- [Arquitetura do NubeSDK](https://github.com/TiendaNube/nube-sdk).
- [Eventos do carrinho](https://nuvemshop.dev/apps/nube-sdk/events/cart).

## Resposta preparada ao suporte (não enviada)

Olá, Karol. Obrigado pelo esclarecimento sobre a seleção entre SDK e Legacy.
Ajustamos o aplicativo para que a renderização e o envio da personalização fiquem
no script SDK, sem depender da execução simultânea do legado.

Para validar a compra com campos obrigatórios, precisamos interceptar o botão
nativo usando `config:set` com `handle_cart_before_update: true` e receber
`cart:before_update`, conforme a documentação. Depois da validação, respondemos
com `proceed: false` e reenviamos `cart:add` com `properties`, variante e quantidade.

Podem confirmar se esse fluxo é suportado no Patagonia da loja `7278258`?
Se o evento não estiver disponível nesse tema, qual é o mecanismo oficial do SDK
para validar campos obrigatórios e anexar as propriedades ao botão nativo?
Precisamos também confirmar que esses dados chegam ao pedido quando o produto
já existe no carrinho. O SDK roda em Worker e não permite usar nosso adaptador DOM.

Obrigado!
