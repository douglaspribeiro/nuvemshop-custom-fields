# LAB Cafe: redirecionamento da compra rápida

## Correção de rótulos trocados e quantidade no checkout — 05/11/2026

O usuário validou a persistência pelo próprio navegador: adicionou 1 En Grano e 1 Molido pela compra rápida, depois 5 Molido pela página do produto. O carrinho parecia atribuir as cinco unidades a En Grano, mas o checkout confirmou os dados corretos: 1 En Grano e 6 Molido. Isso distingue erro de apresentação no carrinho de erro de gravação; não foi concluído um pedido.

O legado usava respostas locais associadas pelo nome/ID do produto, o que não distingue duas linhas do mesmo produto com personalizações diferentes. Essa associação foi removida. O complemento visual agora usa somente as propriedades persistidas da linha, identificada pelo ID do item no carrinho; sem identificação confiável, não inventa respostas. O evento nativo `productAddedToCart` atualiza a fonte a partir de `event.detail.cart`. Rótulos antigos são atualizados ou removidos quando deixam de corresponder à linha. Regressões cobrem reordenação, propriedades em objeto/array e atualização pelo evento nativo.

O resumo de personalizações no checkout passa a mostrar a quantidade por linha (`1 × produto`, `6 × produto`) e acompanha alterações do carrinho, inclusive após carregamento de imagens/configuração.

Publicação necessária desta etapa: o arquivo `src/main/resources/static/assets/nuvemshop-personalizer.js` em **ambos os legados**, para os rótulos; e `src/main/frontend/dist/nuvemshop-checkout-sdk.js` no **checkout-v1**, para a quantidade. As versões publicadas anteriormente ainda não contêm estas alterações. Repetir o cenário do usuário depois da publicação e conferir carrinho e checkout. Nenhum backend ou migração é necessário.


## Aceite automatizado após publicação dos dois legados — 05/11/2026

Investigação adicional do impedimento: a requisição AJAX nativa sem o app enviou somente `add_to_cart`, `quantity` e `add_to_cart_enhanced`, com Origin e Referer da própria loja; recebeu HTTP 403 e JSON sem erros explicativos. Também foi enviado o formulário diretamente pelo navegador, sem AJAX e com os scripts do app bloqueados na sessão: o POST respondeu HTTP 302, e o conteúdo subsequente exibiu “El carrito de compras está vacío.” Não foi identificada a causa da recusa. Não atribuir a Cloudflare, estoque, senha ou restrição de loja de desenvolvimento sem evidência adicional. Nenhum item ou pedido foi persistido nesses testes.

O Playwright executou os arquivos reais entregues pela plataforma, sem substituição de respostas. A loja de teste entregou `legadov2/5.js` e `legado-js/12.js`; ambos têm SHA-256 `e6e86219a351d02cc8bda153dcbb5101319316945706f2b3fe8fc5b6f83dca5e`, igual ao arquivo local corrigido.

Nos quatro cenários (compra rápida e página do produto, desktop e celular), foi observado um único campo do produto, nenhum campo editável no carrinho ou busca, bloqueio da tentativa vazia e envio de `properties[Moido ou Granel]=En Grano` ao preencher. Após recarregar, continuou sem seletor indevido no carrinho.

A adição retornou HTTP 403 (`success=false`, `errors=[]`) nos quatro cenários. Uma execução independente na página do produto, bloqueando todos os scripts do app apenas naquela sessão, enviou a compra nativa sem properties e recebeu o mesmo HTTP 403. A resposta informa `server=cloudflare`, o que não identifica por si só a origem ou a causa do bloqueio. Os carrinhos permaneceram vazios. Persistência das respostas e entrada no checkout continuam pendentes; não houve pedido ou pagamento. Evidências e screenshots estão em `/tmp/cajas-published-{quickbuy,product}-{desktop,mobile}.{json,png}` nesta máquina.


## Correção do seletor indevido no carrinho — loja de teste Idea

O usuário disponibilizou `cajaspersonalizadas2.mitiendanube.com`, produto `379192178`, tema Idea, com o campo obrigatório **Moido ou Granel**. O acesso à vitrine protegida foi autorizado para o teste; a senha não é registrada aqui.

O Playwright confirmou que o legado publicado `legadov2/4.js` inseria o seletor no formulário `.js-ajax-cart-panel[action='/comprar/']`, que contém `go_to_checkout`. A seleção genérica de formulários tratava esse formulário como produto e reutilizava o primeiro ID de produto encontrado na página. Isso explica a escolha solicitada novamente dentro do carrinho.

A correção local exclui formulários de checkout/carrinho, elimina a seleção genérica de qualquer `form` e não busca a identidade do produto em outro elemento da página para identificar um formulário. Os campos continuam nos formulários de produto e compra rápida.

Validação: 158 testes frontend, TypeScript, sintaxe e diff check aprovados. Foram adicionadas regressões com carrinho, busca, cards e formulário principal do produto na mesma página. No Playwright, substituindo apenas o arquivo do legado naquela sessão e usando a configuração real do produto, desktop e celular mostraram um campo na compra rápida, zero campos editáveis no carrinho antes e depois da tentativa de adição, bloqueio da adição vazia e serialização da opção escolhida na requisição nativa. O painel abriu com opacidade final 1 nos dois formatos.

A plataforma retornou HTTP 403 na adição nessa sessão de teste; o carrinho ficou vazio e nenhum item persistido precisou ser removido. A persistência até checkout/pedido continua sem aceite; essa limitação não invalida a reprodução e correção do local indevido de injeção dos campos.

**Publicação pendente desta correção:** `src/main/resources/static/assets/nuvemshop-personalizer.js` nos registros `Legado-js` e `LegadoV2` que coexistem em produção. Esta loja de teste carrega somente `LegadoV2`. Após publicar, recarregar a vitrine e conferir que o carrinho não contém o seletor. Não exige novo bundle de checkout ou migração.

## Verificação posterior com Playwright e configurações removidas

O lojista informou que removeu todas as configurações para liberar as vendas. A API pública consultada pelo navegador confirmou `enabled=false, fields=[]` para o produto `44960054` e também para `44959877`. Nenhuma configuração da loja foi recriada ou reativada nesta execução.

O HTML e a rede entregam `legado-js/11.js` e `legadov2/4.js`, ambos contendo `installQuickBuyFields`. A compra rápida nativa do produto sem campos abriu no desktop sem navegar para a página do produto.

Em contextos isolados de desktop (1365×1000) e celular (390×844), foi interceptada apenas a resposta de configuração do produto `44960054`, simulando o SELECT obrigatório **Molido o en Grano?** com opções de teste. Essa configuração não foi gravada no servidor. Nos dois contextos:

- Foi localizado um único campo no formulário da compra rápida, sem navegação para a página do produto.
- A tentativa vazia não gerou requisição de adição.
- A escolha de teste foi serializada na requisição nativa `POST /comprar/` como `properties[Molido o en Grano?]=En Grano`.
- A Nuvem respondeu HTTP 403 com `success=false, errors=[]`. O carrinho continuou vazio antes e após recarregar.

Uma comparação em contexto isolado, bloqueando apenas os scripts deste app naquela sessão e usando a compra nativa sem campos, também recebeu HTTP 403 com a mesma resposta e carrinho vazio. Isso comprova que o 403 ocorre sem executar o app; sua causa não foi identificada. Não atribuir o 403 à personalização nem concluir que compradores reais recebem o mesmo erro.

O pop-up da loja interferiu nos cliques e precisou ser fechado pelos controles da interface. Capturas iniciais também ocorreram durante a animação do painel (opacidade zero em um ancestral). Uma repetição visual posterior não encontrou o campo dentro do prazo. Portanto, o aceite visual e de primeiro clique ainda precisa de uma execução estável; localizar o elemento no DOM não comprova sua legibilidade final.

**Aceite ainda pendente:** gravação real no carrinho, passagem ao checkout e properties no pedido. Os testes atuais não comprovam esse fluxo completo. O carrinho ficou vazio nas tentativas observadas, sem item persistido para remover; nenhum pedido ou pagamento foi concluído. Usar uma loja/produto de homologação autorizado com campos ativos para retomar o aceite, preservando as configurações removidas da LAB Cafe.

O histórico abaixo registra a implementação e as verificações anteriores.

## Atualização de 05/11/2026: campos na compra rápida

A pedido do usuário, o legado passa a preencher o formulário local da compra rápida com os mesmos campos e validações da página de produto. Isso substitui o redirecionamento descrito abaixo para cards que possuem formulário. A abertura nativa é retomada depois da injeção; uma primeira tentativa de adicionar/submeter apenas abre o preenchimento, sem adicionar o item. Depois, a compra usa o formulário nativo com `properties[...]`. Reabrir preserva as respostas sem duplicar campos. Recomendações usam a identidade do card, independentemente do produto da página.

Produtos sem campos mantêm a ação nativa. Falha na consulta ou ausência de formulário local mantém o encaminhamento seguro à página do produto. A alteração cobre o legado usado pelo tema Idea da LAB Cafe; o comportamento SDK continua como descrito no histórico abaixo.

Arquivo para publicação: `src/main/resources/static/assets/nuvemshop-personalizer.js`, nos dois registros legados coexistentes (`Legado-js` e `LegadoV2`). Não exige atualização do backend. Em 05/11 a página pública da LAB Cafe entregava `legado-js/10.js` e `legadov2/3.js`; o primeiro tinha SHA-256 idêntico ao código local anterior a esta alteração.

Validação local: 160 testes frontend aprovados, incluindo abertura, campos obrigatórios, serialização nativa, isolamento entre produtos, selects inválidos, recomendações, clique repetido e fallback. Sintaxe e TypeScript verificados. JSDOM não comprova o envio pelo AJAX do tema, a aparência em desktop/celular nem a persistência real no checkout/pedido.

Publicação e aceite real pendentes: não há navegador conectado nesta sessão. Publicar o mesmo conteúdo nos dois legados e verificar, em sessão nova, a abertura na sacola, o bloqueio vazio, a compra preenchida, as respostas no carrinho/checkout e um produto sem campos. Não foi criado pedido/pagamento. O bloqueio de pedidos criados manualmente permanece pendente e não é resolvido por esta mudança na compra rápida.

O restante deste documento registra o comportamento e a publicação anteriores.

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
