# NC-23 — Seleção de cores nomeadas

Implementação local. Publicação e aceite em loja real ainda pendentes.

- Novo tipo **Seleção de cores**: cada opção tem nome e seletor de cor. De 1 a 25 opções, nomes únicos sem distinguir maiúsculas/minúsculas, até 100 caracteres, e cores hexadecimais de seis dígitos.
- O comprador escolhe uma das cores oferecidas. O nome permanece visível e é enviado como propriedade do item ao carrinho/pedido. Não há escolha de cor livre pelo comprador. Pedidos anteriores mantêm o texto comprado após editar ou excluir opções.
- Opções e campos podem ser ordenados pela alça de arraste, inclusive por toque, ou por botões acessíveis por teclado. A ordem das opções acompanha o salvamento do campo/template; a ordem dos campos do produto tem o botão **Salvar ordem dos campos**. Alterações dos demais controles não são enviadas por esse botão.
- Templates próprios, aplicação individual/em lote e prévias preservam definições e ordem. Os limites gerais do plano continuam valendo, sem uma restrição adicional para cores.
- Renderização tradicional e adaptador Patagonia mostram amostras selecionáveis e um select nativo como alternativa textual. NubeSDK mostra amostras, nomes e indicação da escolha; campos opcionais permitem limpar a seleção.
- `COLOR_SELECT` guarda as opções ordenadas como JSON em `options_text`, reaproveitado exclusivamente para esse tipo; os tipos antigos preservam seu formato. Não precisa de migration. A resposta pública inclui `colorOptions` e `options` com os nomes.
- Reordenação no servidor exige a lista completa de IDs do produto/loja, sem duplicatas, sob a trava da loja. Rejeita conjuntos parciais, desatualizados e IDs de outra loja; invalida o cache após commit.

## Validação local

Testes Java cobrem normalização, duplicatas/cores inválidas/limites, persistência e edição da ordem, templates e lote, resposta pública, escopo de loja/produto e preservação do texto em um item de pedido. Testes frontend cobrem edição, arraste/toque/botões, prévia, nomes como texto, obrigatoriedade, seleção fora do catálogo e propriedades do carrinho nos três renderizadores. Typecheck e build dos bundles também devem passar.

## Publicação e aceite

Publicar o backend (inclui assets administrativos e script tradicional) e os bundles `nuvemshop-storefront-sdk.js` e `nuvemshop-patagonia.js` gerados em `src/main/frontend/dist`. O checkout SDK não recebeu alteração para esta funcionalidade.

Depois da publicação: configurar e reabrir cores/ordens no admin, testar toque e teclado em dispositivos reais e concluir uma compra conferindo o nome escolhido no carrinho e em cada item do pedido. Esse aceite não foi presumido pelos testes locais.
