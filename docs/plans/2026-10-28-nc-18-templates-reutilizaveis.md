# NC-18 — Templates reutilizáveis

Branch de implementação: `feature/nc-18-templates-reutilizaveis`, criada a partir da NC-17 (`eccc31b`). Durante o trabalho, a remoção do infantil foi registrada separadamente no commit `4ed1822`; esse commit foi preservado.

Fonte de escopo: `roadmap/produtos/nuvem-custom-fields/propostas/nc18-templates-reutilizaveis.md`. A solicitação de implementar orienta esta branch. Esta alteração não promove nem pontua automaticamente documentos no repositório de roadmap e não altera preços ou contratos.

## Comportamento implementado

- Biblioteca `/admin/templates`, com catálogo inicial somente leitura (camiseta, caneca e convite) separado dos modelos privados da loja.
- Criar do zero, editar nome/descrição/campos ordenados, duplicar um modelo inicial/próprio e excluir com confirmação. A revisão do registro impede sobrescrita de edição concorrente.
- “Salvar como template” no editor de produto copia apenas definições. Não inclui IDs dos campos, valores de compradores, objetos de upload ou referências a pedidos.
- Editor reaproveita inicializadores, estilos, validações e prévia do editor existente, incluindo opções de imagem, zoom e colunas no celular. Interface e erros em português/espanhol.
- Aplicação a um produto por operação. A seleção consulta produtos pela API autorizada da loja e a revisão compara campos existentes com as definições do template.
- Escolha explícita entre adicionar e substituir. Substituir exige confirmação; o histórico de nomes de campos anteriores permanece no mecanismo existente. Campos aplicados recebem novos IDs.
- Revisão vinculada ao template, versão, loja, produto e configuração atual. Alterações posteriores exigem nova revisão. Uma chave de requisição persistida impede duplicação no reenvio; reutilizá-la com outra operação gera conflito.
- Operação transacional e serializada pelo bloqueio da loja, com plano efetivo, cotas de produtos/campos/imagens e elegibilidade de upload verificados no servidor. Erros deixam o produto inteiro como estava; não há aplicação parcial.
- Rótulos normalizados não podem colidir, inclusive ao adicionar aos campos existentes. Títulos iniciados por `_` são recusados para não gerar propriedades ocultas/reservadas.
- Upload só é oferecido no editor quando o armazenamento e o direito da NC-17 estão disponíveis. Templates não concedem esse direito. A aplicação também confere a elegibilidade atual.
- Imagens do lojista são objetos imutáveis com referências independentes de campo/template. A migration preenche referências dos campos existentes. Excluir um proprietário só agenda exclusão do objeto quando nenhuma referência resta; modelos somente privados não tornam imagens públicas.
- Imagens novas podem ser enviadas diretamente no editor de template. Temporários sem referência seguem a política existente de limpeza; imagens de template não expiram como uploads abandonados enquanto referenciadas.
- Ações de criar, editar, duplicar, salvar produto, aplicar e excluir integram a auditoria NC-32. Os estados registram contagens, sem nomes, opções, valores ou credenciais.
- Exclusão definitiva remove templates, aplicações e referências antes da limpeza dos objetos. Desinstalar não exclui templates por inferência; o acesso continua dependente da autenticação da loja.

Editar um template não modifica cópias já aplicadas. Editar um produto não modifica o template de origem. Imagens compartilhadas continuam disponíveis porque seu conteúdo não é sobrescrito; trocar uma imagem usa outro identificador.

O endpoint antigo de onboarding agora encaminha à revisão. O identificador `personalizacao-infantil` é recusado de forma controlada, sem apagar campos já existentes. O método interno legado também passou a verificar o conjunto completo antes de aplicar, eliminando o corte silencioso pelo limite.

## Persistência e limites técnicos

Migration adicional `V65__reusable_store_templates.sql`:

- `store_templates`: identidade, loja, origem, nome/descrição, definições ordenadas em JSON, revisão e datas.
- `template_applications`: operação idempotente por loja/requisição, produto, origem, modo e revisão apresentada.
- `personalization_image_references`: proprietários de imagens existentes, com preenchimento inicial a partir do ledger atual.

As definições JSON usam um DTO próprio, sem serializar entidades ou dados de compradores. Cada template aceita de 1 a 100 definições por limite técnico da operação; isso não aumenta os limites de campos/produtos do plano. Nome até 100 caracteres; descrição até 500; opções e validações respeitam o editor e os limites de imagens existentes. Não foi criada cobrança ou franquia comercial de templates.

As imagens continuam no armazenamento já configurado para opções do lojista. Nenhuma permissão ou bucket de produção foi alterado. A migração não muda migrations anteriores nem copia objetos S3.

## Validação

Execuções locais concluídas:

- `mvn -o -q -Dskip.frontend=true test`: 840 testes, sem falhas/erros, um ignorado.
- O cenário adicional de pedido após aplicação/exclusão/substituição foi validado com `-Dtest=PersonalizationHistoryIntegrationTest`: quatro testes passaram.
- `node/node node_modules/vitest/vitest.mjs run`: 120 testes JavaScript passaram.
- `node/node node_modules/typescript/bin/tsc --noEmit`: passou.
- `mvn -o -q -Dskip.frontend=true -DskipTests package`: passou, empacotando os novos recursos administrativos e a migration.
- `git diff --check`: passou.


- Testes integrados de CRUD, independência, confirmação de substituição, novos IDs, histórico, revisão obsoleta, reenvio e concorrência.
- Testes de limites sem aplicação parcial, concorrência entre produtos no Free, colisões, regex inválida, isolamento de loja e elegibilidade NC-17.
- Imagens: upload próprio, isolamento, acesso privado antes da aplicação, preservação após excluir produto de origem/template e liberação apenas após o último proprietário.
- Teste da migration em H2/MySQL mode, incluindo o preenchimento de referências existentes sem perda das linhas de imagem.
- Telas Thymeleaf reais renderizadas por MockMvc, português/espanhol, auditoria e exclusão definitiva; resposta pública confirma definições aplicadas na vitrine.
- JavaScript: reordenação/serialização, reutilização dos editores, preservação de alterações em erro de rede, bloqueio durante upload, confirmação e mesma chave de reenvio.
- Prévia real do editor renderizada pelo Chrome em 1440px e 390px, com perfil isolado e sem rede externa. Evidências locais em `/tmp/ncf-nc18-editor-desktop.png` e `/tmp/ncf-nc18-editor-mobile.png`.

Os testes não representam instalação em loja real: API de produtos e armazenamento são simulados. Antes da liberação, validar migration/concorrência no MySQL de homologação, imagens reais, aplicação em uma loja, carrinho/pedido, versão dos scripts e reinstalação. Não é necessário introduzir contrato novo de vitrine: os produtos continuam publicando campos no formato existente.

## Estado de entrega

Implementação local da biblioteca nesta branch, ainda sem commit/push destas alterações, sem deploy e sem verificação de produção. A remoção do infantil já tem seu commit separado. O plano dLocal e `node_modules/` preexistentes foram preservados fora do escopo.

Aplicação em lote e atualização vinculada continuam fora desta entrega (NC-19).
