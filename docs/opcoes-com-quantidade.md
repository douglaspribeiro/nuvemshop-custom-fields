# Opções com quantidade

Novo tipo `QUANTITY_SELECT`, disponível nos planos Pro e Ultra, respeitando os limites de produtos e campos existentes. Não altera os checkboxes configurados.

O lojista informa as opções, uma por linha, e pode definir mínimo e máximo de unidades no total. Para um buquê de sete flores: mínimo 7 e máximo 7. Duas opções são suficientes para permitir, por exemplo, Rosa × 3 e Margarida × 4. Não há preço adicional nem estoque por opção.

A vitrine apresenta controles de quantidade por opção e um contador total. Valores negativos, fracionários, inválidos ou fora dos limites impedem adicionar ao carrinho. Campo obrigatório sem mínimo exige pelo menos uma unidade. Opções com quantidade zero não seguem para o pedido.

SDK, Patagonia e legado enviam uma propriedade legível, por exemplo: `Flores: Rosa × 3; Margarida × 4`. A composição vale por unidade do produto principal. Os tipos e limites são preservados nos modelos, na aplicação em lote e nos snapshots existentes.

O servidor impede criar, editar ou aplicar esse tipo em planos inferiores ao Pro. No downgrade, a configuração do produto fica pausada, sem excluir os campos; volta a ser elegível ao retornar ao Pro/Ultra. As demais configurações seguem as regras existentes.

A apresentação padrão é uma lista com controles redondos e cores do tema da loja. O lojista pode escolher até três colunas; no celular, as opções ficam em uma coluna. A escolha é preservada em templates e snapshots, e configurações anteriores usam uma coluna.

Os limites usam a migração V85 e a quantidade de colunas usa a V86. A atualização exige publicar o backend e os scripts de vitrine alterados; o commit local não publica essas alterações.
