# Plano: modelos personalizados da loja

Data: 21/10/2026. Status: planejado, implementação pendente.

## Necessidade

A loja Idealiza Laser solicitou salvar um conjunto de campos e reutilizá-lo em
vários produtos, sem configurar cada produto individualmente. O exemplo contém
Nome da criança obrigatório, Frase opcional e WhatsApp obrigatório no formato
`(xx)xxxxx-xxxx`.

O modelo pronto “Personalização infantil” já foi adicionado ao catálogo existente.
Ele atende à configuração inicial, mas continua sendo aplicado a um produto por
vez. A próxima evolução é permitir que o próprio lojista crie, edite e reutilize
seus modelos em massa.

## Escopo da primeira versão

- Disponibilizar “Meus modelos” junto aos modelos prontos existentes.
- Criar, nomear, editar e excluir modelos exclusivos da loja.
- Criar um modelo do zero ou salvar como modelo os campos de um produto existente.
- Preservar tipo, rótulo, obrigatoriedade, ordem, opções, limites, exemplo,
  validação e configurações de imagens dos campos.
- Selecionar vários produtos, inclusive entre páginas de busca, e aplicar o
  modelo em uma única operação.
- Exibir uma revisão com o modelo escolhido, produtos selecionados e eventuais
  impedimentos antes de confirmar a aplicação.
- Informar ao final quais produtos receberam os campos e quais foram ignorados
  ou falharam, com os respectivos motivos.

## Comportamento proposto

Aplicar um modelo cria campos independentes no produto. Editar ou excluir o
modelo depois não altera os produtos que já receberam uma cópia. O lojista
continua podendo ajustar os campos de cada produto pelo editor existente.

Na primeira versão, a aplicação em massa será permitida somente em produtos sem
campos. Produtos já configurados serão identificados e ignorados, sem acrescentar
campos duplicados nem substituir configurações. Uma tentativa repetida não deve
duplicar campos, inclusive em requisições concorrentes.

Os limites efetivos do plano precisam ser respeitados por produto, quantidade de
campos e recursos de imagem. Um produto deve receber o conjunto completo ou não
receber nenhum campo; não aplicar modelos parcialmente por atingir um limite.

Modelos e campos devem ser isolados por loja, inclusive nas operações de copiar,
editar, excluir e aplicar. Excluir um modelo não pode remover imagens ainda
utilizadas por produtos ou por outros modelos.

## Etapas de implementação

1. Definir persistência e migração para modelos e seus campos, associados à loja,
   com ordenação e representação de todas as configurações do editor atual.
2. Implementar serviço e rotas de criação, edição, exclusão e cópia a partir de
   produto, reutilizando as validações existentes e protegendo o acesso por loja.
3. Criar as telas “Meus modelos” e editor do modelo, além da ação “Salvar como
   modelo” no editor de campos do produto. Incluir textos em português e espanhol.
4. Implementar seleção de produtos, revisão e aplicação em massa. Trabalhar com
   transação por produto e controle de concorrência para evitar cópias parciais
   ou duplicadas; retornar resultados individualizados.
5. Integrar invalidação do cache da vitrine e tratamento de referências de imagens
   aos fluxos existentes. Manter os modelos prontos disponíveis.
6. Validar o fluxo completo localmente e preparar a publicação da aplicação e da
   migração. Avaliar mudanças nos bundles da vitrine somente se a implementação
   introduzir um novo contrato; registrar separadamente qualquer publicação de
   bundle necessária.

## Validação e critérios de aceite

- Criar e editar um modelo com os três campos do chamado e sua validação de WhatsApp.
- Salvar os campos de um produto como modelo sem perder suas configurações.
- Aplicar a vários produtos e conferir ordem, obrigatoriedade, opções e validação
  na vitrine e no carrinho.
- Confirmar independência das cópias após editar ou excluir o modelo original.
- Bloquear acesso e aplicação de modelos pertencentes a outra loja.
- Respeitar limites de plano sem deixar um produto com apenas parte do modelo.
- Ignorar produtos já configurados e impedir duplicação em repetição/concorrência.
- Conferir seleção entre páginas, resultados de falhas parciais e nova tentativa.
- Conferir modelos com opções de imagem e preservação dos arquivos compartilhados.
- Executar testes relevantes de serviço, persistência e autorização e verificar
  o fluxo no navegador. Distinguir evidência local de validação em produção.

## Decisões a fechar durante a implementação

- Disponibilidade por plano e eventual limite de modelos por loja. A demanda
  inicial veio de uma loja Ultra; nenhuma nova regra comercial está definida.
- Tamanho máximo do lote e necessidade de processamento em segundo plano,
  conforme volume e tempo de execução medidos.
- Política de retenção e exclusão de imagens referenciadas por modelos, alinhada
  ao mecanismo existente de armazenamento e limpeza.

## Evoluções posteriores

- Acrescentar ou substituir campos de produtos já configurados com revisão explícita.
- Reaplicar alterações de um modelo a produtos escolhidos pelo lojista.
- Aplicar por categorias ou coleções, se houver suporte adequado na integração.

Não há prazo de entrega definido. Este documento registra o plano; não representa
funcionalidade já implementada ou publicada.
