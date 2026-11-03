# LAB Cafe: redirecionamento da compra rápida

Data: 03/11/2026. Loja 959276, `https://shop.labcafe.com.ar/`, tema Idea.

## Verificação após a publicação informada pelo usuário

Na primeira consulta após a publicação em 03/11/2026, a leitura confirmou:

- Container em execução: `NUVEMSHOP_SCRIPT_ID=12174`.
- A API de scripts da loja retorna `LegadoV2` 12174 ativo, evento `onload`, versão 1 publicada (`legadov2/1.js`). O legado antigo 9344 também continua ativo, evento `onfirstinteraction`, versão 8.
- O SHA-256 do arquivo novo publicado era idêntico ao arquivo local daquela etapa: `a042b63dab6247a63ee01bcb1dee3e3bb764566575a6cb1740287b6c1f05d39d`.
- Contudo, o HTML público da página inicial e de `/productos/cafe-colombia-la-marianela-250grs/` ainda referencia apenas `legado-js/8.js` para este app. A repetição da leitura da página inicial com query nova e `Cache-Control: no-cache` trouxe a mesma referência antiga.
- O arquivo antigo servido não contém `installQuickBuyRedirect` nem `quick_buy_redirect`.

Conclusão: publicação e configuração do novo ID confirmadas; entrega do script novo na vitrine ainda não confirmada. Não afirmar que a compra rápida foi corrigida nessa loja. A origem da diferença entre API e HTML ainda precisa ser investigada. Nenhuma associação ou configuração foi alterada durante esta checagem.

A sessão não disponibiliza navegador conectado; não houve teste de clique, desktop/celular, carrinho ou checkout. Essa inspeção HTTP não substitui o teste de interface solicitado.

Após a publicação posterior da proteção contra duplicidade nos dois legados, a API passou a indicar as versões 9 (`Legado-js`) e 2 (`LegadoV2`), com arquivos idênticos à correção. A última consulta pública da LAB Cafe nesta sessão ainda entregava apenas `legado-js/8.js`, tanto na página inicial quanto no produto. A Velo, por outro lado, já entregava as versões 9 e 2. Esses resultados são distintos por loja; a publicação no Partner não comprova entrega imediata na LAB Cafe.

## Evidência atual

- Cadastro ativo, plano interno `PREMIUM_PLUS` (Pro), 14 regras de produto habilitadas.
- O HTML público usa cards `.js-item-product[data-product-id]`, abertura `.js-item-buy-open`, formulário `.js-product-form` e botão `.js-addtocart`.
- Cada card contém um link `.item-link` para a página do próprio produto.
- O café La Marianela 250grs (44960054) retorna `enabled=true` e o campo obrigatório `Molido o en Grano?` na API pública de personalização.
- A loja carrega `campos-personalizados7/legado-js/8.js` na primeira interação (`onfirstinteraction`). O conteúdo publicado corresponde ao arquivo local anterior a esta alteração.

## Alteração preparada

Arquivo de publicação: `src/main/resources/static/assets/nuvemshop-personalizer.js`.

O comportamento foi ampliado para todas as lojas, sem lista de IDs, e também para NubeSDK. As instruções atuais estão em [Compra rápida com personalização](compra-rapida-personalizacao.md). Antes de abrir a compra rápida ou adicionar pelo card, consulta a configuração do produto. Se há campos, abre a página do próprio produto; sem campos, retoma a ação nativa. Se a consulta falhar, encaminha à página do produto. Nenhum item é adicionado pelo app durante o redirecionamento.

A delegação também cobre cards inseridos depois do carregamento e recomendações na página de outro produto, usando o ID do card. O formulário principal do produto continua com seu fluxo de preenchimento. Os campos não são mais injetados no primeiro formulário de um card do catálogo.

## Publicação e aceite pendentes

1. Publicar o conteúdo completo do arquivo no script legado `legado-js` e o bundle storefront SDK, conforme o procedimento global vinculado acima.
2. Garantir carregamento antes da primeira ação de compra. `onfirstinteraction` não garante que o listener esteja instalado no primeiro clique: antecipar o carregamento no portal, verificando a opção `onload`.
3. Em sessão nova, testar primeiro clique e cliques posteriores na sacola de La Marianela 250grs, no desktop e celular: abre o produto sem adicionar ao carrinho.
4. Preencher o campo e confirmar que a compra pela página do produto continua funcionando e preserva a resposta no carrinho/checkout.
5. Testar um item sem campos (por exemplo, cápsulas 128433426, após conferir sua configuração): mantém a compra rápida. Cobrir também listagem, cards carregados depois e recomendações.

Não houve publicação, commit, push, pedido ou pagamento nesta execução. A sessão não disponibilizou navegador conectado para acessar o Partner Portal ou validar a interface real.

## Validação local

- A primeira etapa passou em 132 testes de 15 arquivos; a etapa global amplia os cenários para outras lojas, temas e SDK. O resultado final está no procedimento global vinculado acima.
- Verificação de sintaxe do JavaScript e `git diff --check` aprovados.
- JSDOM simula os eventos com o markup observado no tema; a navegação é uma função de teste, pois o JSDOM não implementa mudança de página. Isso não substitui o aceite com o script publicado no navegador.
