# NC-20 / NC-21 — Personalização opcional por variante

Implementação local de 28/10/2026 na branch `feature/nc-20-nc-21-personalizacao-por-variante`, criada a partir de `feature/nc-18-templates-reutilizaveis`.

## Configuração

No editor de campos do produto, marque **Exibir personalização somente nas variantes selecionadas**, escolha as combinações e salve a condição. Desmarcar a condição mantém os campos em todas as variantes, como nos produtos antigos. A migração V66 preserva esse comportamento como padrão.

Cadastre variantes, preços e estoque na Nuvemshop: por exemplo, P / sem personalização por R$ 50 e P / com personalização por R$ 60. Repita para cada tamanho/cor. O app não cria variantes, altera preços, cobra adicionais nem compartilha estoque.

A seleção usa IDs, validados no catálogo do produto com as credenciais da loja autenticada. Uma condição ativa exige pelo menos um ID válido ao salvar. Uma variante removida é indicada no editor; os IDs antigos continuam salvos até a revisão, sem liberar outras variantes automaticamente. Falhas ao consultar o catálogo preservam a condição e impedem salvar uma seleção não validada.

## Vitrine e carrinho

A API pública inclui `variantRestricted` e `variantIds`. A alteração invalida o cache da loja após commit. Os caminhos SDK, Patagonia e legado ocultam campos nas variantes excluídas, limpam os valores e não enviam propriedades antigas. Ao voltar à personalização, os campos começam vazios e os obrigatórios são validados novamente. Valores de upload são esquecidos no rascunho, sem excluir arquivos vinculados a carrinhos/pedidos; a limpeza do armazenamento segue o ciclo de vida existente.

SDK e Patagonia enviam o ID real da variante, quantidade e propriedades; o legado mantém a variante do formulário nativo. O preço é sempre calculado pela Nuvemshop. Não há cálculo paralelo de acréscimo ou estorno no app.

O bridge SDK possui uma limitação de mesclagem de itens: pode aumentar a quantidade de uma variante já no carrinho e descartar as propriedades novas. Para impedir mistura de textos/artes, SDK e Patagonia bloqueiam a adição se a mesma variante já possui propriedades diferentes e orientam revisar o carrinho. Esta entrega não demonstra suporte simultâneo a duas artes diferentes na mesma variante. Variantes distintas permanecem distintas.

Quando não há identificação confiável da variante inicial, a condição fica oculta até a plataforma informar uma seleção válida. SDK aceita o evento de variante, o parâmetro `variant` na URL validado no catálogo e produtos de variante única. Patagonia também resolve os controles selecionados do tema. O legado acompanha o ID no formulário, inclusive mudanças sem evento `change`.

## Validação

Testes locais cobrem condição persistida e publicada, aviso de variante excluída, isolamento da seleção por loja/produto, compatibilidade dos produtos antigos, seleção vazia/inválida, trocas de variante, descarte de callbacks antigos, campos obrigatórios e prevenção de conflito entre propriedades, incluindo referências privadas de uploads.

Resultado local: suíte Java com 847 testes, zero falhas/erros e um ignorado; frontend com 128 testes aprovados em 17 arquivos; TypeScript sem erros; bundles gerados com tsup e `git diff --check` sem problemas. A suíte Java usa H2 e não comprova a execução da migração em MySQL de produção.

Não houve commit, push, deploy nem validação comercial em loja. Antes de considerar NC-21 validada em produção, executar em loja autorizada:

1. Produto com variantes reais sem personalização (R$ 50) e com personalização (R$ 60), incluindo combinações de tamanho/cor.
2. Seleção inicial, URL de variante, recarga, mobile e alternância com texto, imagens, checkbox e upload; conferir ausência de propriedades na opção sem personalização.
3. Obrigatório vazio nos caminhos de compra suportados; adicionar duas unidades da variante personalizada e conferir R$ 120 antes de descontos/frete.
4. Conferir ID, quantidade, propriedades e preço real em carrinho, checkout e pedido. Testar variante diferente e tentativa de arte/texto conflitante na mesma variante.
5. Remover uma variante selecionada, revisar aviso no editor e confirmar que nenhuma outra passou a receber campos. Conferir produtos excluídos e lojas distintas.
6. Conferir descontos, cancelamento e estorno pelos valores nativos da plataforma.

O recorte não inclui condições entre campos, fórmulas, criação automática de variantes, estoque compartilhado, item extra ou alteração de checkout. NC-21 corresponde ao item 09 existente, sem criar outra entrega de upsell.
