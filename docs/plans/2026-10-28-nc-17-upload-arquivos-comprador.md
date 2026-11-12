# NC-17 — Upload de arquivos pelo comprador

Branch: `feature/nc-17-upload-arquivos-comprador`.

Fonte comercial: `roadmap/produtos/nuvem-custom-fields/features/upload-arquivos-comprador.md`, aprovada em 28/10/2026. Este documento registra a implementação local; não registra publicação em produção.

## Implementação

- Tipo `FILE_UPLOAD` no editor, título, instrução, obrigatório e formatos JPEG/PNG/PDF configurados em linhas separadas. Vazio aceita os três. Permanecem os limites existentes de produtos e campos.
- Upload liberado pelo plano Pro/Ultra, inclusive em assinaturas existentes, sem oferta ou concessão separada. Loja suspensa, desinstalada ou em apagamento não aceita novos uploads.
- O armazenamento privado deve estar configurado; a cota vem diretamente do plano: Pro 2 GB e Ultra 10 GB (Ultra reduzido de 20 para 10 GB por decisão de 07/11/2026).
- Unidades decimais: máximo de 20.000.000 bytes por arquivo; Pro 2.000.000.000 bytes; Ultra 10.000.000.000 bytes de ocupação simultânea. Alterar entre Pro/Ultra muda a franquia para novos envios, sem apagar objetos existentes.
- Reservas persistentes com bloqueio da loja, sessão aleatória emitida pelo servidor e hash SHA-256 no banco. Repetir a mesma requisição concluída devolve o mesmo identificador e não cobra espaço novamente. Tentativas concorrentes não podem gravar sobre o mesmo objeto.
- Estados `RESERVED → TEMPORARY → CART → ORDERED`, seguidos de `DELETING → DELETED/EXPIRED`. Reservas, temporários, carrinho, pedidos e exclusões pendentes ocupam cota; liberar espaço exige confirmação da exclusão no provedor.
- Validação do tamanho efetivo e do conteúdo JPEG/PNG/PDF, independente do nome/MIME do navegador. Imagens precisam decodificar e ter marcador final. PDFBox valida documentos e rejeita senha, scripts, ações, anexos, formulários XFA e anotações. Isso não é antivírus e não garante ausência de todos os conteúdos maliciosos.
- Limites de processamento: até dois uploads e uma validação de conteúdo por instância, imagens até 40 milhões de pixels, PDF até 500 páginas e até 100 mil objetos na árvore examinada. Nenhum conteúdo é executado/renderizado no servidor.
- S3 privado dedicado, bloqueio de acesso público verificado antes de gravar, nomes internos opacos e SSE-S3. Não reutiliza URLs públicas de `IMAGE_SELECT` nem fornece credenciais/URLs do objeto ao carrinho.
- Componentes NubeSDK, Patagonia e legado: seleção, andamento, resultado com nome/tamanho, remoção, substituição e validação antes do carrinho. As propriedades usam identificador opaco e chave que inclui o campo. Arquivos diferentes mantêm propriedades diferentes, inclusive em produtos iguais.
- NubeSDK usa `Form.Field` e `getFormFile` do runtime atual. Runtime sem essa capacidade informa indisponibilidade e não libera campo obrigatório sem arte. DOM/worker com XMLHttpRequest informa percentual; o caminho fetch informa andamento até concluir a validação.
- Sessão e referência do arquivo preservadas durante navegação na mesma aba. O servidor reconfirma a validade antes de adicionar ao carrinho; referências técnicas ficam em propriedades ocultas, com nome/tamanho legíveis separados. Um arquivo adicionado deve ser enviado novamente para outra personalização; uma referência já confirmada não é reatribuída a outro item/pedido.
- Eventos `order/created`, `order/updated` e `order/paid` recuperam o pedido pela API autorizada. Associação usa ID estável do arquivo, loja, produto, campo e ID real do item, sem depender de label/posição. Reprocessar o mesmo evento não muda o vínculo.
- Conciliação periódica por loja, paginada, antes da limpeza de carrinho. Falha ou varredura incompleta preserva a arte até nova tentativa. A referência nunca ressuscita um arquivo cuja exclusão começou.
- Tela mínima `/admin/buyer-uploads`, acessível pela navegação: consumo, espaço livre, franquia, avisos passivos de 50%/75%/95%/100%, pedidos/itens, vencimento, download e exclusão com confirmação de irreversibilidade. Não dispara emails nem avisos repetidos em segundo plano.
- Download em streaming, sem carregar o arquivo inteiro na memória da resposta, somente autenticado e autorizado à loja correspondente, com janela de retenção validada em cada acesso. Resposta como anexo, `no-store`, `nosniff` e sandbox; nenhuma URL permanente ou assinada é necessária.
- Remover/substituir a seleção exclui temporários; se o envio ao carrinho já começou, a seleção é limpa mas o objeto é preservado até conciliação/expiração, para não apagar arte de um pedido ainda em sincronização. Temporários/carrinho expiram em 24h, renovados por sessão ativa/vínculo do carrinho. Pedidos expiram 90 dias desde a criação, sem extensão por download. Limpeza idempotente, com novas tentativas após cinco minutos e remoção de todas as versões do objeto.
- Downgrade/cancelamento/suspensão/desinstalação impedem novos envios sem remover os arquivos de pedidos. Downloads mantêm a janela original quando existe autenticação administrativa válida. Reinstalar e autenticar a mesma loja recupera a consulta aos pedidos ainda retidos; desinstalada sem autenticação, a loja não tem acesso administrativo prometido.
- Exclusão definitiva remove direitos/sessões e dados de pedido/sessão/nome. Mantém somente os dados necessários à remoção do objeto; o worker apaga esse registro quando o S3 confirma a exclusão.
- Métricas de uploads, downloads, ocupação, fila e resultados da limpeza, sem conteúdo, nomes, URLs assinadas ou credenciais.

## Armazenamento, tráfego e custo

Provedor escolhido: S3 Standard, região configurável (padrão `us-east-2`), bucket separado e privado. O bucket real ainda não foi criado/configurado por esta tarefa. Não alterar automaticamente IAM, backups ou infraestrutura de produção.

Limites técnicos implementados: 60 requisições de upload por IP/minuto/instância antes do processamento multipart; até 1.000 sessões por loja/hora; até 1.000 tentativas de upload por loja em 24h; até 20 arquivos pendentes por sessão; download de até 1 GB por loja a cada cinco minutos por instância. Erros orientam aguardar/repetir; não reduzem a retenção. Em múltiplas réplicas, o limite de IP/download é por réplica; reservas, tentativas e ocupação são compartilhadas pelo banco. A implantação precisa considerar a quantidade de réplicas e a configuração confiável do proxy.

Simulação de custo, não medição de faturamento: assumindo US$ 0,023 por GB/mês de armazenamento e US$ 0,09 por GB de saída, uma ocupação constante de 2/10 GB com um download completo por mês custa aproximadamente US$ 0,23/1,13 por loja, antes de requisições, impostos, câmbio, backend, validação, banco, observabilidade e backups. Premissas de requisições: US$ 0,005 por mil PUT/LIST e US$ 0,0004 por mil GET. A conversão decimal/binarizada do provedor deve ser incluída na medição real. Downloads repetidos e versões antigas aumentam o custo; o limite por janela de cinco minutos não é um teto mensal de despesas. A redução do Ultra para 10 GB não implementa franquia mensal de transferência nem altera a mensalidade.

Referências: [preços S3](https://aws.amazon.com/s3/pricing/), [exemplo oficial de composição de custos AWS](https://d1.awsstatic.com/events/Summits/dcsummit2023/COP201_CloudCost_E1_20230530_ZMEdited.pdf). As taxas acima são premissas de simulação; conferir a cotação da região/conta na liberação. A franquia gratuita de saída é compartilhada na conta e não foi atribuída por loja nesta simulação.

Antes de ofertar: provisionar o bucket privado e IAM mínimo; medir armazenamento, PUT/GET/LIST, chamadas de controle, validação, saída S3 → backend → lojista e custos de versões/backups; configurar alertas de orçamento e observar abuso. A oferta continua desligada enquanto isso não estiver validado.

### Pendência registrada em 07/11/2026 — medidor de transferência

Solicitação: implementar medição de downloads por loja e mês, separada da ocupação de armazenamento. Avisos de consumo em 80% e 95% e compra de franquia adicional de transferência foram aprovados como entregas necessárias para concluir a NC-17 em 07/11/2026. Esta seção registra trabalho pendente, não uma funcionalidade entregue.

- [ ] Persistir bytes consumidos por loja/período no banco, compartilhados entre instâncias e preservados em reinicializações. Definir explicitamente período, fuso e data de renovação exibida ao lojista.
- [ ] Reservar consumo antes de iniciar downloads concorrentes e conciliar transferências interrompidas com o consumo efetivo no provedor. Contar downloads repetidos; exclusão de arquivos não devolve tráfego consumido.
- [ ] Exibir no painel "Espaço para arquivos" e "Downloads do mês", com consumo, franquia quando aprovada e próxima renovação. Manter textos em português e espanhol, sem confundir armazenamento com velocidade de download.
- [ ] Implementar avisos ao lojista em 80% e 95% da franquia mensal de transferência, mostrando consumo, saldo, próxima renovação e acesso à compra de franquia adicional. Evitar avisos repetidos para o mesmo patamar/período; manter textos em português e espanhol.
- [ ] Implementar compra opcional de franquia adicional de transferência pelo painel. Exibir volume, preço e validade antes da confirmação; creditar a franquia somente após confirmação de pagamento pelo provedor, de forma idempotente. Atualizar saldo e disponibilidade de downloads após o crédito, sem cobrar excedentes automaticamente e sem alterar a cota de armazenamento.
- [ ] Definir volumes, preços, validade dos pacotes, franquias comerciais dos planos e regra ao esgotar o saldo antes de ativar bloqueio mensal. Os exemplos discutidos de Pro 10 GB/mês e Ultra 30 GB/mês são hipóteses, não limites aprovados. Comunicar antecipadamente o esgotamento, oferecer compra adicional e não excluir arquivos por exceder tráfego.
- Exemplos de preços dos pacotes, com acréscimo de 20% solicitado em 07/11/2026: 10 GB por R$ 17,88; 30 GB por R$ 47,88; 50 GB por R$ 71,88. São referências para implementação e validação econômica, sem cadastro ou cobrança no provedor. A validade de 90 dias permanece uma proposta. A avaliação de OCI Object Storage pode alterar o custo, mas não autoriza migração de infraestrutura.
- [ ] Registrar eventos/indicadores operacionais para detectar abuso e estimar custo por loja. Validar avisos nos dois patamares, compra aprovada/pendente/recusada, repetição da confirmação de pagamento sem crédito duplicado e retomada de downloads após recomposição do saldo.
- [ ] Se adotados links assinados no futuro, conciliar acessos reais no S3: emissão de URL não comprova transferência e a URL pode ser reutilizada até expirar. ZIP permanece adiado.

Comportamento atual verificado no código: armazenamento é limitado a 2 GB no Pro e 10 GB no Ultra; a reserva recusa qualquer novo arquivo que não caiba no espaço livre. Arquivos já armazenados não são apagados por atingir a cota e os downloads de pedidos continuam disponíveis durante sua retenção e mediante autorização. O espaço só é liberado após confirmação de remoção no provedor. No tráfego, existe somente o limite de 1 GB por loja a cada cinco minutos por instância, que recusa temporariamente downloads excedentes; não há franquia mensal, cobrança de excedente ou medidor mensal implementados.

## Configuração e publicação

- `BUYER_UPLOAD_S3_BUCKET`: bucket privado dedicado, obrigatório para armazenar.
- `BUYER_UPLOAD_S3_REGION`: padrão `us-east-2`.
- `BUYER_UPLOAD_S3_PREFIX`: padrão `production/buyer-files`.
- `BUYER_UPLOAD_CLEANUP_DELAY_MS` / `BUYER_UPLOAD_RECONCILE_DELAY_MS`: 3.600.000 (1 hora) / 300.000 por padrão.

IAM precisa permitir gravar/ler/apagar objetos e versões, listar versões, ler versionamento e consultar bloqueio público do bucket. Exigir os quatro bloqueios de acesso público. Não usar ACL pública, política pública, hospedagem estática ou caminhos de execução para os objetos. O backend faz o download e não precisa de CORS no bucket.

Não há promessa de apagamento instantâneo em backups: o worker remove objetos e versões online; restaurações do banco podem reintroduzir referências antigas e precisam reaplicar a política de exclusão antes de servir tráfego. Definir e validar retenção e acesso dos backups do bucket/banco antes da liberação. Falhas de S3 mantêm a intenção persistente, a cota ocupada e a métrica de pendência até o processamento com sucesso.

Comunicação de desinstalação: a retenção de pedidos segue correndo; não há acesso administrativo enquanto a loja não reinstalar e autenticar. A tela informa 90 dias a partir da criação do pedido. Validar essa comunicação na desinstalação/central de ajuda antes da oferta pública.

## Validação

- Suíte Java completa: 820 testes, sem falhas, um ignorado. O teste integrado foi repetido após o ajuste final de reagendamento da limpeza quando a conciliação está indisponível.
- Testes focados cobrem reserva concorrente, isolamento, conteúdo real, script PDF, arquivo truncado, fluxo configuração → upload → pedido → download → exclusão, dois itens, replay, indisponibilidade da conciliação e apagamento após exclusão da loja. S3 e API de pedidos são simulados nesses testes.
- JavaScript: 116 testes, sem falhas, incluindo validação obrigatória/opcional durante envio, persistência da referência somente após vínculo ao carrinho, erro de rede, componente DOM e descarte após consumo.
- TypeScript e bundles validados localmente. `git diff --check` passou.

Ainda exigem evidência de homologação: migração/bloqueios no MySQL real, S3/IAM privado real, NubeSDK/legado/Patagonia em loja com checkout real, dois itens iguais com artes diferentes, quantidades/variações, desktop/mobile, sincronização atrasada, custos, backups e reinstalação autenticada. A janela de 24h precisa ser conferida contra o tempo real do checkout. Os testes locais não comprovam essas integrações externas.

Implementação local nesta branch; sem commit, push, deploy ou verificação em produção. O documento preexistente `2026-10-23-dlocal-planos-compartilhados.md` e alterações paralelas em `NicheTemplateService.java` ficam fora desta tarefa.

## Regra simplificada de acesso

V73 remove a tabela de concessões e o marcador de oferta das assinaturas. Arquivos, sessões, pedidos e retenção são preservados. Pro/Ultra não exigem habilitação adicional.

## Gerenciamento de arquivos pelo lojista

A tela Arquivos dos compradores agrupa os arquivos por pedido e item, mostra consumo/espaço livre, permite ordenar por antiguidade ou tamanho e filtrar pela data do pedido. A paginação traz 20 pedidos completos por página, sem dividir os arquivos de um pedido.

A seleção aceita arquivos individuais ou pedidos inteiros e permanece ao trocar de página. Antes de excluir, a confirmação mostra quantidade de arquivos/pedidos e espaço a liberar. A exclusão valida toda a seleção na loja autenticada, é idempotente e mantém a ocupação até a remoção no armazenamento ser confirmada; pedidos e metadados de vínculo são preservados. Há até 200 arquivos por operação.

Downloads individuais continuam disponíveis, limitados a 1 GB por loja a cada cinco minutos por instância. Downloads e exclusões usam autorizações independentes do plano atual para os arquivos de pedidos ainda disponíveis.

Em 07/11/2026, o download em ZIP foi adiado: botões de ZIP por pedido/seleção, endpoint e geração do arquivo foram removidos. A seleção permanece para exclusão em lote. A implementação anterior fazia streaming, sem carregar todo o lote na RAM, e recusava lotes acima de 1 GB; ainda assim consumia processamento, conexões e tráfego S3 → backend → lojista. Uma retomada deve avaliar geração assíncrona, concorrência, orçamento e entrega direta por link temporário, além da contabilização de transferência.

Validação local deve cobrir indisponibilidade do endpoint ZIP, downloads individuais, isolamento entre lojas, confirmação/idempotência de exclusão, preservação de consumo até a limpeza, paginação/filtros e navegação em português/espanhol. A revisão visual usa o HTML real com dados fictícios; não comprova transferência ou exclusão no S3 de produção.
