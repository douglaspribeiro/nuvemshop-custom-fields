# Limites de seleção do checkbox

O checkbox com várias opções aceita **Mínimo de seleções** e **Máximo de seleções**, tanto no produto quanto nos modelos reutilizáveis.

- Ambos em branco: mantém o comportamento anterior. Se obrigatório, exige pelo menos uma opção.
- Mínimo 7 e máximo 7: exige exatamente sete opções distintas.
- Apenas máximo 7: aceita de zero a sete escolhas; se obrigatório, de uma a sete.
- Apenas mínimo 2: exige pelo menos duas escolhas, sem outro limite além das opções disponíveis.
- Mínimo 0: não torna o campo obrigatório. A opção “Obrigatório” continua exigindo pelo menos uma escolha.

O mínimo precisa ser um inteiro a partir de zero; o máximo, um inteiro a partir de um. O mínimo não pode superar o máximo e nenhum limite pode superar a quantidade de opções distintas. Checkboxes sem lista de opções não aceitam limites.

A vitrine mostra a contagem de seleções e valida os limites antes de adicionar ao carrinho nos fluxos SDK, Patagonia e legado. As escolhas seguem no pedido como os nomes/números das opções. Flores repetidas por quantidade não fazem parte deste recurso.

Os limites são preservados ao salvar, editar e aplicar modelos, inclusive em lote, e nos snapshots de configuração. Trocar o tipo de campo remove os limites salvos. A migração `V85__checkbox_selection_limits.sql` adiciona colunas opcionais e mantém os campos existentes sem limites.

Origem: ticket SUP-44, Pieroly (8509843). A implementação local não responde nem encerra automaticamente o atendimento.
