# Compra rápida com personalização

## Atualização de 05/11/2026: campos na compra rápida

A pedido do usuário, o legado passa a preencher o formulário local da compra rápida com os mesmos campos e validações da página de produto. Isso substitui o redirecionamento descrito abaixo para cards que possuem formulário. A abertura nativa é retomada depois da injeção; uma primeira tentativa de adicionar/submeter apenas abre o preenchimento, sem adicionar o item. Depois, a compra usa o formulário nativo com `properties[...]`. Reabrir preserva as respostas sem duplicar campos. Recomendações usam a identidade do card, independentemente do produto da página.

Produtos sem campos mantêm a ação nativa. Falha na consulta ou ausência de formulário local mantém o encaminhamento seguro à página do produto. A alteração cobre o legado usado pelo tema Idea da LAB Cafe; o comportamento SDK continua como descrito no histórico abaixo.

Arquivo para publicação: `src/main/resources/static/assets/nuvemshop-personalizer.js`, nos dois registros legados coexistentes (`Legado-js` e `LegadoV2`). Não exige atualização do backend. Em 05/11 a página pública da LAB Cafe entregava `legado-js/10.js` e `legadov2/3.js`; o primeiro tinha SHA-256 idêntico ao código local anterior a esta alteração.

Validação local: 160 testes frontend aprovados, incluindo abertura, campos obrigatórios, serialização nativa, isolamento entre produtos, selects inválidos, recomendações, clique repetido e fallback. Sintaxe e TypeScript verificados. JSDOM não comprova o envio pelo AJAX do tema, a aparência em desktop/celular nem a persistência real no checkout/pedido.

Publicação e aceite real pendentes: não há navegador conectado nesta sessão. Publicar o mesmo conteúdo nos dois legados e verificar, em sessão nova, a abertura na sacola, o bloqueio vazio, a compra preenchida, as respostas no carrinho/checkout e um produto sem campos. Não foi criado pedido/pagamento. O bloqueio de pedidos criados manualmente permanece pendente e não é resolvido por esta mudança na compra rápida.

O restante deste documento registra o comportamento e a publicação anteriores.

Implementação de 03/11/2026 para todas as lojas, em qualquer plano que disponibilize campos na vitrine. Não existe restrição por Store ID. A LAB Cafe motivou a melhoria; a evidência específica está em [LAB Cafe](lab-cafe-compra-rapida.md).

## Comportamento

- Produto com campos habilitados: a compra pelo catálogo encaminha à página do próprio produto para preencher a personalização, antes de adicionar ao carrinho.
- Produto sem campos disponíveis: a ação nativa continua. A API pública já aplica as regras de plano, disponibilidade e ativação da loja.
- Falha ao consultar os campos: encaminha à página do produto. No SDK, se o tema não fornecer a URL, cancela a adição e mostra uma instrução para abrir o produto.
- A compra pela página principal do produto continua usando a validação e o envio de propriedades existentes.
- Recomendações usam o ID do card/produto selecionado, sem reutilizar o ID da página principal.
- No SDK, aumentar a quantidade de uma linha com personalização válida continua permitido. Remoções seguem seu fluxo normal.

## Integrações

**Legado:** captura cliques e submit dos cards `.js-item-product[data-product-id]` ou `[data-component='product-list-item'][data-product-id]`. Reconhece abertura de compra rápida, modal e adição direta por classes e componentes usados pelos temas. Obtém a URL pelo link do card e usa somente navegação para a mesma origem. Delega eventos para cobrir cards carregados dinamicamente. As consultas começam no foco, passagem do ponteiro ou clique; não consulta todo o catálogo ao abrir a página. Cada produto consultado é reaproveitado durante a visita.

Os formulários de cards são excluídos da injeção dos campos da página do produto, inclusive nas recomendações.

**NubeSDK:** trata `quickbuy:open` e `cart:before_update`, aproveitando dados do modal, listas, seções e URLs dos itens do carrinho. Cancela a adição personalizada antes de navegar com `getBrowserAPIs().navigate`. Consultas têm limite de 3,5 segundos, abaixo do prazo de 5 segundos do evento da plataforma. A troca de página invalida as políticas e as respostas atrasadas; fechar o modal invalida seu redirecionamento pendente.

Sem URL fornecida pelo tema, não inventa o endereço: bloqueia a adição personalizada e exibe instrução no slot `before_main_content`. Temas personalizados que não emitam os eventos SDK nem usem os seletores legados exigem adaptação e validação própria. Cobertura no código não comprova comportamento em todos os temas publicados.

## Arquivos para publicação

1. Legado: conteúdo completo de `src/main/resources/static/assets/nuvemshop-personalizer.js` no script `legado-js` do Partner Portal.
2. SDK storefront: `src/main/frontend/dist/nuvemshop-storefront-sdk.js` no script de vitrine. O script observado anteriormente era `store-front-v5`, ID 11470; confirmar no portal antes da publicação.
3. Antecipar o carregamento de ambos os scripts, verificando a opção `onload`. Com `onfirstinteraction`, o primeiro clique pode ocorrer antes da instalação dos listeners.

O checkout não mudou. Atualizar apenas a aplicação não atualiza os scripts hospedados no Partner Portal.

## Aceite após publicação

- Abrir sessão nova e testar o primeiro clique, desktop e celular, na LAB Cafe/Idea e em uma loja SDK/Patagonia.
- Conferir abertura da compra rápida e adição direta no catálogo, busca e recomendações. Produto personalizado abre sua página e não altera o carrinho; produto sem campos mantém a compra rápida.
- Preencher os campos na página e confirmar a personalização no carrinho e checkout.
- Aumentar/diminuir quantidades de item já personalizado e testar remoção.
- Simular falha da API e fechar/trocar de modal durante a consulta: não deve adicionar sem os campos nem navegar para um produto anterior.

Não houve publicação, commit, push, pedido ou pagamento nesta execução. Não há navegador conectado nesta sessão para operar o Partner Portal ou verificar a interface publicada.

## Verificação local

### Proteção contra dois legados ativos (03/11/2026)

No produto `https://www.velopapelariacatolica.com.br/produtos/agenda-catolica-2027-carlo-acutis/`, loja 8389134, o HTML público entrega os dois scripts `legado-js/8.js` (primeira interação) e `legadov2/1.js` (onload). A checagem `data-ncf-injected` anterior à consulta não impedia duas inicializações pendentes de renderizar os campos.

O legado agora reserva um runtime por loja e página antes de qualquer consulta. Também confere novamente o formulário imediatamente antes de renderizar, preservando campos e valores já existentes. O teste com dois IDs carregados antes da resposta da API verifica apenas uma consulta, um conjunto de campos e um único valor por propriedade.

150 testes aprovados, TypeScript, sintaxe do legado e `git diff --check` aprovados nesta correção. Posteriormente, o usuário publicou o mesmo arquivo nos dois registros: `legado-js/9.js` e `legadov2/2.js`, ambos com SHA-256 `4c3adc5225b8710cb505670dd687b3e5d99af8d68b51afbed986feaa4b93cbb0`, idêntico ao arquivo local corrigido. A última consulta HTTP da Velo nesta sessão já entregou as versões 9 e 2. O usuário informou funcionamento correto no Chrome; não houve verificação visual independente nesta sessão.

Durante a coexistência, publicar o mesmo arquivo corrigido nos dois registros legados; um arquivo antigo já publicado não passa a respeitar a proteção nova automaticamente. A retirada do legado antigo precisa ocorrer após confirmar a entrega do novo nas lojas, pois a LAB Cafe ainda vinha recebendo somente o antigo nas checagens anteriores. Não alterar o script NubeSDK como parte desta correção de duplicidade entre legados.

149 testes aprovados em 16 arquivos. TypeScript, build ESM/IIFE, sintaxe do JavaScript legado e `git diff --check` aprovados.

Os testes cobrem múltiplos IDs de loja, compra rápida normal, redirecionamento, ID de recomendação, cards dinâmicos, falha de política, cliques repetidos, submit com o botão original, modal SDK, adição direta SDK, quantidade de item personalizado, ausência de URL e descarte de respostas atrasadas. JSDOM substitui apenas a navegação por uma função de teste, pois não implementa mudança de página; isso não substitui o aceite no navegador.

Documentação da plataforma: [UI e compra rápida](https://nuvemshop.dev/apps/nube-sdk/events/ui-and-custom), [eventos do carrinho](https://nuvemshop.dev/apps/nube-sdk/events/cart).
