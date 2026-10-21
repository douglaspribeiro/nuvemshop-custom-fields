# Plano: máscara de entrada para WhatsApp

Data: 21/10/2026.

## Status e localização

Implementação salva para uso futuro, sem integrar à `main` e sem publicar.
Por decisão do responsável, não utilizar agora.

- Branch local: `feat/whatsapp-input-mask`.
- Commit da implementação: `a56d3a9` (`feat: adiciona máscara de entrada para WhatsApp`).
- A `main` mantém somente o comportamento anterior: regex validado ao tentar
  adicionar ao carrinho, sem máscara durante a digitação.
- Este plano é independente do [criador de modelos personalizados](plano-templates-personalizados.md).

## Objetivo e escopo preservado na branch

Formatar automaticamente o WhatsApp como `(11)99999-9999` durante a digitação,
mantendo o regex como validação final antes da compra. Ativar somente em campos
`TEXT` com o regex exato `^\([0-9]{2}\)[0-9]{5}-[0-9]{4}$`, utilizado pelo modelo
“Personalização infantil”. Campos existentes com esse regex também são elegíveis,
sem reaplicar o modelo nem alterar dados no banco.

A implementação contempla:

- NubeSDK: propriedade nativa `Field.mask` com `(00)00000-0000` e normalização
  do valor entregue ao carrinho.
- Legado e adaptador DOM Patagonia: formatação progressiva, teclado de telefone,
  colagem, exclusão junto à pontuação e preservação do cursor na edição.
- Helpers DOM aceitam colagem com `+55` quando há 13 dígitos; validar o
  comportamento equivalente do componente nativo SDK na loja antes de publicar.
- Testes do regex do modelo, helpers, renderização, validação e valor do carrinho.
- Documentação técnica no README do frontend da branch.

## Validação já executada

- Suíte frontend: 118 testes aprovados.
- TypeScript: verificação sem erros.
- Build dos bundles SDK, checkout e adaptador Patagonia concluído.
- Teste Java `NicheTemplateServiceTest` aprovado.
- Sintaxe do script legado e `git diff --check` sem erros.

As verificações são locais e automatizadas. Não houve validação manual na loja
nem publicação dos scripts. O teste confirmou que o regex atual está correto;
não foi necessária migração de correção.

## Como retomar

1. Revisar `git show a56d3a9` e comparar a branch com a `main` atual para avaliar
   mudanças posteriores nos mesmos fluxos.
2. Integrar o commit somente quando houver decisão de ativar a funcionalidade,
   resolvendo possíveis conflitos com as alterações mais recentes.
3. Reexecutar testes frontend, TypeScript, build dos bundles e teste Java do modelo.
4. Validar em homologação: digitação, apagar toda a entrada, Backspace/Delete
   perto da pontuação, edição no meio, seleção/substituição, colagem e dispositivos
   móveis. Conferir o bloqueio de número incompleto e a persistência do número
   formatado no carrinho/pedido, incluindo o fluxo SDK no Patagonia.
5. Publicar os scripts storefront SDK e legado no Partner Portal. Distribuir os
   assets da aplicação conforme o fluxo vigente, incluindo o adaptador DOM
   somente onde ele ainda for utilizado. O SDK atual não depende desse adaptador.
6. Confirmar na loja os bundles efetivamente carregados e o comportamento real.

Não há migração de banco nem mudança necessária no script de checkout. Publicar
a aplicação não substitui os bundles hospedados pela plataforma. Nenhuma etapa
de integração ou publicação está autorizada apenas pela existência deste plano.
