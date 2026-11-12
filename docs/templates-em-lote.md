# Templates em lote (NC-19)

Na biblioteca de templates, use **Aplicar em lote**. Filtre por categoria, marca
(quando informada pela Nuvemshop) e situação da configuração. Selecione até 200
produtos; a seleção permanece ao mudar os filtros. O catálogo suporta até 5.000
produtos e recusa catálogos maiores sem apresentar uma seleção incompleta.

* **Adicionar:** mantém os campos atuais e acrescenta campos com nomes diferentes.
* **Substituir:** troca todos os campos dos produtos selecionados pelos do template.
* **Atualizar:** atualiza os campos vinculados à origem registrada e mantém os demais.

Revise os campos antes/depois e confirme a execução. Produtos com edição local
começam desmarcados e exigem autorização individual para atualizar. Nomes em
conflito e limites do plano impedem a aplicação naquele produto. Mudanças no
template ou no produto após a revisão exigem uma nova revisão.

Cada produto é aplicado em uma transação, junto com seu resultado e procedência.
Uma falha não desfaz produtos já concluídos nem deixa um produto parcialmente
alterado. O histórico permite retomar e tentar novamente os que falharam;
repetir uma execução concluída não duplica campos. A página conduz a execução:
fechá-la interrompe os próximos envios, mas preserva os resultados já gravados.

Aplicações individuais e em lote feitas a partir desta versão registram origem
e versão. Aplicações antigas sem essa informação precisam ser revisadas e
aplicadas antes de usar **Atualizar**. O vínculo identifica o último template
aplicado ao produto. Não existe propagação automática para produtos futuros.
O recurso usa os tipos e opções de campos já disponíveis no editor; configurações
por variante e condicionais ainda não integradas não fazem parte desta versão.

Histórico de campos e imagens compartilhadas seguem as proteções existentes.
Os dados dos lotes e vínculos são eliminados com os dados da loja.
