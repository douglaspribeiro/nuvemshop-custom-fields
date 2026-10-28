# NC-17 — Upload de arquivos pelo comprador

Branch: `feature/nc-17-upload-arquivos-comprador`.

Fonte comercial: `roadmap/produtos/nuvem-custom-fields/features/upload-arquivos-comprador.md`, aprovada em 28/10/2026. Este documento registra a implementação local; não registra publicação em produção.

## Implementação

- Tipo `FILE_UPLOAD` no editor, título, instrução, obrigatório e formatos JPEG/PNG/PDF configurados em linhas separadas. Vazio aceita os três. Permanecem os limites existentes de produtos e campos.
- Identificação explícita `BUYER_UPLOAD_V1` capturada na primeira assinatura local criada após habilitar a nova oferta. O direito depende também de assinatura de produção ativa e plano Pro/Ultra. Assinaturas existentes continuam sem essa identificação. Atualizações de cobrança não migram contratos automaticamente.
- Liberação desligada por padrão: `BUYER_UPLOAD_NEW_OFFER_ENABLED=false`. A tabela de concessões explícitas também permite identificar migrações futuras deliberadas; nenhum registro é criado automaticamente para contratos antigos.
- Unidades decimais: máximo de 20.000.000 bytes por arquivo; Pro 5.000.000.000 bytes; Ultra 25.000.000.000 bytes de ocupação simultânea. Alterar entre Pro/Ultra muda a franquia para novos envios, sem apagar objetos existentes.
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
- Tela mínima `/admin/buyer-uploads`, acessível pela navegação: consumo, espaço livre, franquia, avisos passivos de 80%/95%/100%, pedidos/itens, vencimento, download e exclusão com confirmação de irreversibilidade. Não dispara emails nem avisos repetidos em segundo plano.
- Download em streaming, sem carregar o arquivo inteiro na memória da resposta, somente autenticado e autorizado à loja correspondente, com janela de retenção validada em cada acesso. Resposta como anexo, `no-store`, `nosniff` e sandbox; nenhuma URL permanente ou assinada é necessária.
- Remover/substituir a seleção exclui temporários; se o envio ao carrinho já começou, a seleção é limpa mas o objeto é preservado até conciliação/expiração, para não apagar arte de um pedido ainda em sincronização. Temporários/carrinho expiram em 24h, renovados por sessão ativa/vínculo do carrinho. Pedidos expiram 90 dias desde a criação, sem extensão por download. Limpeza idempotente, com novas tentativas após cinco minutos e remoção de todas as versões do objeto.
- Downgrade/cancelamento/suspensão/desinstalação impedem novos envios sem remover os arquivos de pedidos. Downloads mantêm a janela original quando existe autenticação administrativa válida. Reinstalar e autenticar a mesma loja recupera a consulta aos pedidos ainda retidos; desinstalada sem autenticação, a loja não tem acesso administrativo prometido.
- Exclusão definitiva remove direitos/sessões e dados de pedido/sessão/nome. Mantém somente os dados necessários à remoção do objeto; o worker apaga esse registro quando o S3 confirma a exclusão.
- Métricas de uploads, downloads, ocupação, fila e resultados da limpeza, sem conteúdo, nomes, URLs assinadas ou credenciais.

## Armazenamento, tráfego e custo

Provedor escolhido: S3 Standard, região configurável (padrão `us-east-2`), bucket separado e privado. O bucket real ainda não foi criado/configurado por esta tarefa. Não alterar automaticamente IAM, backups ou infraestrutura de produção.

Limites técnicos implementados: 60 requisições de upload por IP/minuto/instância antes do processamento multipart; até 1.000 sessões por loja/hora; até 1.000 tentativas de upload por loja em 24h; até 20 arquivos pendentes por sessão; download de até 1 GB por loja a cada cinco minutos por instância. Erros orientam aguardar/repetir; não reduzem a retenção. Em múltiplas réplicas, o limite de IP/download é por réplica; reservas, tentativas e ocupação são compartilhadas pelo banco. A implantação precisa considerar a quantidade de réplicas e a configuração confiável do proxy.

Simulação de custo, não medição de faturamento: assumindo US$ 0,023 por GB/mês de armazenamento, US$ 0,005 por mil PUT/LIST, US$ 0,0004 por mil GET e US$ 0,09 por GB de saída, uma ocupação constante de 5/25 GB com um download completo por mês custa aproximadamente US$ 0,57/2,84 por loja, antes de impostos, câmbio, backend, validação, banco, observabilidade e backups. A conversão decimal/binarizada do provedor deve ser incluída na medição real. Downloads repetidos e versões antigas aumentam o custo; o limite de velocidade não é um teto mensal de despesas. Nenhum reajuste foi feito.

Referências: [preços S3](https://aws.amazon.com/s3/pricing/), [exemplo oficial de composição de custos AWS](https://d1.awsstatic.com/events/Summits/dcsummit2023/COP201_CloudCost_E1_20230530_ZMEdited.pdf). As taxas acima são premissas de simulação; conferir a cotação da região/conta na liberação. A franquia gratuita de saída é compartilhada na conta e não foi atribuída por loja nesta simulação.

Antes de ofertar: provisionar o bucket privado e IAM mínimo; medir armazenamento, PUT/GET/LIST, chamadas de controle, validação, saída S3 → backend → lojista e custos de versões/backups; configurar alertas de orçamento e observar abuso. A oferta continua desligada enquanto isso não estiver validado.

## Configuração e publicação

- `BUYER_UPLOAD_S3_BUCKET`: bucket privado dedicado, obrigatório para armazenar.
- `BUYER_UPLOAD_S3_REGION`: padrão `us-east-2`.
- `BUYER_UPLOAD_S3_PREFIX`: padrão `production/buyer-files`.
- `BUYER_UPLOAD_NEW_OFFER_ENABLED`: falso por padrão; habilitar somente após homologação. Identifica contratos novos no momento da criação do checkout; acesso só após ativação de pagamento.
- `BUYER_UPLOAD_CLEANUP_DELAY_MS` / `BUYER_UPLOAD_RECONCILE_DELAY_MS`: 60.000 / 300.000 por padrão.

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
