# Patagonia: teste real da compra rápida do Storefront SDK

Data: 05/11/2026. Loja de teste: `cajaspersonalizadas2.mitiendanube.com`, ID 8398782, tema Patagonia. Produto 379192178, variante 1633744953. Campo SELECT obrigatório `Moido ou Granel`, opções En Grano e Molido.

**Status: entrega com limitação conhecida, aceita em 05/11/2026.** Publicar os bundles atualizados sem exigir alterações no tema. O app valida e envia as propriedades em sua ação de personalização. O botão nativo Patagonia permanece disponível e pode adicionar sem propriedades; não há garantia de obrigatoriedade nesse caminho. O checkout não bloqueia vendas por personalização ausente, inclusive em pedidos manuais.

## Ambiente e limites da evidência

O teste usou Playwright/Chrome, uma sessão autenticada isolada e a configuração real do produto. A autenticação foi renovada depois da troca do tema. A vitrine carregava `store-front-v5/16.js`. O bundle local foi carregado somente na sessão de teste, substituindo o módulo importado pelo Worker; nenhum script foi publicado no Partner Portal. A substituição do módulo foi confirmada, em vez de presumida a partir da tentativa de interceptar a URL de rede.

Não foram alteradas configurações de personalização nem finalizado pedido ou pagamento. Senha, cookies e URL/token de checkout não fazem parte deste documento nem dos arquivos versionados.

## Caminho personalizado validado

A implementação local acrescenta a ação **Personalizar** no card configurado do Patagonia. Ela abre o formulário em `modal_content`, inclusive para produto de variante única, cujo botão nativo adiciona diretamente e não abre `quickbuy:open`. Produtos sem campos mantêm a ação nativa.

1. A tentativa vazia pelo botão do app foi impedida, sem adicionar produto.
2. Pela ação Personalizar, foi adicionada 1 unidade En Grano e depois 1 Molido.
3. Pela página do produto, usando Adicionar com personalização, foram adicionadas mais 5 unidades Molido.
4. O carrinho mostrou duas linhas com as respostas corretas: 6 Molido e 1 En Grano.
5. O estado persistido do checkout confirmou `quantity=6, properties={"Moido ou Granel":"Molido"}` e `quantity=1, properties={"Moido ou Granel":"En Grano"}`.
6. “Ítems Personalizados” mostrou as quantidades 6 e 1 com suas respectivas escolhas. Não houve bloqueio de personalização no checkout.
7. No viewport de 390 × 844, o modal, seletor, quantidade e botões ficaram visíveis dentro da largura da tela. Essa verificação de layout não equivale a uma compra concluída em dispositivo físico.

No estado SDK da vitrine, as duas linhas apareceram com `properties=[]`, apesar de a interface nativa do carrinho e o estado do checkout exibirem as respostas corretas. Portanto, esse estado transitório não é suficiente para concluir perda de dados. As respostas vieram na confirmação `cart:add:success`, e a verificação final usou o checkout.

Evidências locais desta máquina: `/tmp/cajas-sdk-patagonia-acceptance.json`, `/tmp/cajas-sdk-modal-grano.png`, `/tmp/cajas-sdk-modal-mobile.png`, `/tmp/cajas-sdk-checkout-properties.png`. Não são arquivos de publicação.

## Limitação do botão nativo

O botão Comprar de variante única adicionou diretamente, sem abrir a personalização. Foram observados `cart:update` e `cart:add:success`; não foi observado `cart:before_update` nem `quickbuy:open` nessa operação.

No runtime da loja, a API oficial de customização retornou `applied: 1` e `get` retornou `disabled: true`/`aria-disabled=true` para o botão do PDP. Mesmo assim, um clique real do browser adicionou uma linha sem propriedades. Portanto, esses retornos não comprovam que a compra está bloqueada neste runtime. Também foi observado que o botão nativo do card não corresponde ao seletor `.js-addtocart` da API.

A proposta de CSS por loja foi rejeitada e removida. A ponte DOM experimental também foi descartada. A tentativa de desabilitar o botão pela API foi removida da entrega, pois o retorno da API não comprovou o bloqueio do clique.

Uma nova rodada isolada confirmou: carrinho vazio no início, tentativa sem escolha bloqueada, 1 unidade En Grano e 1 Molido pela compra rápida, mais 5 Molido no PDP, persistência após recarga e ambas as escolhas visíveis no carrinho. O checkout dessa rodada redirecionou para `/complete` e mostrou a verificação anti-bot Cloudflare, então não foi possível repetir a inspeção do checkout nessa sessão. A confirmação de checkout registrada acima veio de uma rodada anterior.

Os testes de checkout do projeto também cobrem fluxo manual (`from_store=1`) e normal: personalização ausente não bloqueia o avanço; o resumo mostra propriedades já existentes. Isso implementa a alternativa de não exigir campos do app para ordens pré-carregadas sem propriedades, sem adicionar campos editáveis ao checkout manual.

A limitação nativa deve ser informada no aceite e encaminhada à plataforma. Não reintroduzir bloqueio no checkout para compensá-la.

Os itens dos dois testes (fluxo personalizado e reprodução nativa sem respostas) foram removidos ao final pelos controles do próprio carrinho. A recarga confirmou o carrinho da sessão de diagnóstico vazio.

## Publicação e aceite

1. Publicar `src/main/frontend/dist/nuvemshop-storefront-sdk.js` no registro Storefront SDK e `src/main/frontend/dist/nuvemshop-checkout-sdk.js` no registro Checkout SDK do Partner Portal.
2. Repetir as escolhas En Grano e Molido pela ação do app e conferir quantidades e propriedades no carrinho e checkout.
3. Confirmar que produto sem campos e pedido manual sem propriedades podem avançar.
4. Confirmar o carrinho de teste vazio depois da validação.

Não há mudança de backend nesta entrega; imagem Docker não é necessária para estas alterações. Build local e testes não equivalem a publicação no Partner Portal. O relato técnico está em [chamado para a plataforma](2026-11-05-patagonia-chamado-plataforma.md).

Referências oficiais consultadas: [eventos do carrinho e opt-in](https://tiendanube.dev/apps/nube-sdk/events/cart), [eventos de compra rápida](https://tiendanube.dev/apps/nube-sdk/events/ui-and-custom), [slots de Storefront](https://tiendanube.dev/es-AR/apps/nube-sdk/slots/storefront-slots). A documentação descreve separação de linhas por propriedades; esse comportamento foi confirmado no checkout do caminho explícito neste teste.
