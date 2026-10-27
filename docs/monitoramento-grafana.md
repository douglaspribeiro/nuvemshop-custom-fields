# Tráfego de vitrine no Grafana

## Fonte oficial após centralização

O catálogo gerenciado fica no repositório `server-docker-1`, em
`grafana/dashboards/ncf-storefront-traffic.json`. Novas alterações devem ser feitas
nesse arquivo e seguir o reconciliador da infraestrutura. O JSON neste repositório
é uma referência de exportação; não é uma segunda fonte para o sync automático.
As instruções de importação manual abaixo continuam úteis antes da ativação do sync.

O app publica métricas Prometheus em `GET /actuator/prometheus`. O endpoint precisa ficar
restrito à rede do Prometheus ou a um proxy autenticado; ele não deve ser público.

O contador `ncf_storefront_http_requests_total` inclui apenas requisições de vitrine e checkout:

| `kind` | O que mede |
| --- | --- |
| `storefront_legacy_script` | Download de `nuvemshop-personalizer.js` |
| `storefront_sdk_script` | Download do bundle NubeSDK da vitrine |
| `storefront_patagonia_script` | Download do fallback Patagonia |
| `checkout_sdk_script` | Download do bundle do checkout |
| `storefront_personalization` | Leitura da configuração de campos por uma página de produto |
| `checkout_style` | Leitura de estilo pelo checkout |
| `storefront_script_beacon` | Beacon emitido pelo JavaScript em execução |

O contador não tem `store_id`: `kind`, `method` e `status` bastam para volume, tipo e erros.
O ranking de lojas usa `ncf_storefront_store_requests_total`, somente com `store_id`, registrado
apenas para lojas ativas validadas pelo backend; requisições sem loja validada entram só no
contador geral. Cruzar loja com tipo/método/status multiplicava as séries sem consumidor.
Não são usados IP, e-mail ou caminho como rótulos.

O contador `ncf_storefront_personalized_product_requests_total`, com `store_id` e `product_id`,
registra apenas GETs bem-sucedidos da configuração de produtos habilitados com campos disponíveis.
IDs enviados arbitrariamente pelo cliente não criam séries de produtos.

Os nomes vêm do banco, em métricas separadas: `ncf_store_info` (`store_id`, `store_name`, somente
lojas ativas) e `ncf_personalized_product_info` (`store_id`, `product_id`, `store_name`, `product_name`).
O ranking de lojas usa `last_over_time` no período, para manter o nome de lojas desinstaladas
durante o intervalo. O catálogo é atualizado na inicialização e a cada 60 segundos. Produtos desabilitados, sem campos ou de lojas
inativas deixam de aparecer no ranking. Renomear uma loja ou produto atualiza os metadados sem
reiniciar seu contador. Essas métricas contêm nomes comerciais: mantenha o endpoint restrito.

Exemplo de scrape no Prometheus:

```yaml
scrape_configs:
  - job_name: nuvemshop-custom-fields
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["app-interno:8080"]
```

No Grafana, selecione Prometheus como datasource e use estas consultas:

```promql
# Chamadas recebidas no período selecionado, separadas por tipo.
sum by (kind) (increase(ncf_storefront_http_requests_total[$__range]))

# Requisições por minuto nos últimos intervalos.
sum by (kind) (rate(ncf_storefront_http_requests_total[$__rate_interval])) * 60

# Taxa de erro da vitrine/checkout.
sum(rate(ncf_storefront_http_requests_total{status=~"5.."}[$__rate_interval]))
/ clamp_min(sum(rate(ncf_storefront_http_requests_total[$__rate_interval])), 0.001)
```

Tambem ha um dashboard pronto para importar em
`grafana/storefront-traffic-dashboard.json`. No Grafana, abra **Dashboards → New → Import**,
envie o arquivo e escolha o datasource Prometheus solicitado.

Para atualizar um dashboard já importado, importe o JSON novamente mantendo seu UID
`ncf-storefront-traffic` e confirme a substituição. O ranking de lojas mostra o nome cadastrado;
a nova tabela mostra **Loja · Produto · Quantidade**, com os 20 produtos mais consultados no período.
Publique também a aplicação desta versão: importar somente o JSON não cria as novas métricas.
Não há preenchimento retroativo de consultas anteriores à implantação.

O cartão **Chamadas no período** usa uma consulta instantânea no fim do intervalo selecionado,
sem a antiga linha de fundo. Essa linha mostrava totais em janelas móveis e não um acumulado
desde o início do período. O painel **Chamadas acumuladas no período · estimativa** soma os
incrementos em blocos sem sobreposição, usando a transformação cumulativa do Grafana.
Cada bloco tem no mínimo quatro minutos, para haver amostras suficientes com a coleta de 60s.
Somente blocos completos após o início entram na curva; frações nas bordas e lacunas de coleta
não são reconstruídas. Por isso, o final da curva pode diferir do cartão, que usa `increase`
em todo o intervalo. A curva é uma estimativa dos incrementos observados, não um registro
exato de todas as chamadas nem um indicador de disponibilidade da coleta.

Todos os gráficos de séries temporais usam **Max data points = 100**, para que o
Grafana amplie automaticamente o intervalo conforme o período selecionado, sem
depender da largura do painel. Em 24h, o intervalo calculado é de aproximadamente
15 minutos; períodos menores recuperam mais detalhe e períodos maiores usam blocos
maiores. O mínimo dos painéis é **1m**, exceto o acumulado, que preserva **4m**.

As consultas de taxas mantêm **Min step = 1m**, alinhado ao scrape do Alloy.
`$__rate_interval` amplia também a janela de cálculo, com mínimo de quatro minutos,
evitando apenas espaçar pontos de uma taxa ainda calculada em uma janela curta.
Acertos do cache e tempo médio continuam ponderados pelas respectivas quantidades;
o tamanho do cache mostra a média das amostras em `$__interval`.
O refresh de 30s não altera a frequência de coleta. A unidade de chamadas continua
sendo quantidade por minuto, pois a consulta multiplica a taxa por segundo por 60.
Veja as [opções de consulta do Grafana](https://grafana.com/docs/grafana/latest/visualizations/panels-visualizations/query-transform-data/).

“Quantidade” significa consultas à configuração de personalização, não visitantes únicos nem
visualizações de qualquer produto da loja. Recargas, robôs e consultas repetidas podem incrementar
o contador; respostas servidas por cache fora do app não entram. O Prometheus precisa observar
amostras para calcular o aumento; `increase` usa extrapolação e o painel arredonda o resultado.
O catálogo registra contadores zerados para os produtos configurados antes das primeiras consultas.

O dashboard associa contadores e nomes por IDs usando
[correspondência de vetores do Prometheus](https://prometheus.io/docs/prometheus/latest/querying/operators/).
A tabela usa uma [consulta instantânea do Grafana](https://grafana.com/docs/grafana/latest/datasources/prometheus/query-editor/)
que calcula o aumento em todo o período selecionado.

O download do arquivo representa uma requisição que chegou à origem. Se uma CDN passar a servir
o asset do cache, ela não chega ao app e deve ser acompanhada também pelas métricas da própria
CDN. Já o beacon indica que o JavaScript de fato executou no navegador, mas pode faltar quando o
cliente bloqueia requisições de telemetria.

As telas administrativas já carregam GA4. Elas não entram no contador `ncf_storefront_http_requests_total`.

## Cache e orçamento de séries

O Caffeine guarda respostas de personalização por loja/produto, estilo por loja e
validação de loja ativa para beacons e script legado. Expira 1 minuto após a
gravação (`STOREFRONT_CACHE_TTL`), com limite compartilhado de 10.000 entradas por
instância (`STOREFRONT_CACHE_MAXIMUM_SIZE`). Não guarda entidades JPA nem elimina
gravações de beacons. Requests continuam sendo contados nos acertos do cache.

Alterações em lojas, planos, regras e campos invalidam o cache local após commit;
rollback não invalida. A exclusão de campo em lote invalida explicitamente.
A invalidação avança a geração do cache da instância, cobrindo limites de
plano que dependem de outros produtos, sem bloquear carregamentos em andamento.
Entradas da geração anterior deixam de ser reutilizadas e são removidas pelo TTL
ou pelo limite de tamanho. SQL externo e alterações em outra
instância são percebidos pela expiração de 1 minuto.

Os novos painéis mostram taxa de acertos e tamanho estimado com `cache_gets_total`
e `cache_size`, separadas pelas etiquetas fixas `cache="storefront"` e
`cache="plans"`, sem loja/produto/URL.

O filtro `spring_apps_budget` no Alloy da infraestrutura vale para todos os apps:
famílias técnicas do Micrometer (`jvm_`, `http_`, `hikaricp_`, `cache_`, `tasks_`,
`spring_`...) só são enviadas se estiverem no allowlist consumido pelos dashboards;
`ncf_*` e demais métricas de negócio passam sem lista. Do Custom Fields seguem
`cache_gets_total`, `cache_size`, `http_server_requests_seconds_(count|sum)`,
memória/threads/GC da JVM, HikariCP e `logback_events_total`. Ver
`server-docker-1/docs/plans/reducao-series-allowlist.md`. O filtro precisa ser
publicado/recarregado na infraestrutura para afetar o envio.

O ranking aceita, por transição, o contador antigo com `store_id`; o `or` na consulta
pode ser removido após a retenção do Grafana Cloud (14 dias) desde a publicação.

O catálogo de planos ativos tem cache Caffeine separado de **24 horas**
(`PLANS_CACHE_TTL`), com limite de 128 entradas e métricas `cache="plans"`.
A chave inclui o tipo e a data de vigência em São Paulo, respeitando a virada do
dia para versões agendadas. Qualquer criação, alteração ou exclusão de plano invalida
todo o cache de planos, de todos os tipos e datas, após commit; rollback preserva o cache.
Alterações no catálogo invalidam os caches de planos
e vitrine após commit. Leituras transacionais consultam o banco diretamente;
fora de transações, cada consumidor recebe uma cópia independente do plano.
O histórico administrativo de versões continua consultando o banco. Alterações
por SQL externo ou em outra instância dependem do TTL para atualizar os planos.

## Tempo médio por tipo de chamada

O gráfico de chamadas/minuto também mostra o tempo médio no eixo direito em ms.
O timer `ncf_storefront_http_duration_seconds` usa somente `kind` (os sete tipos
fixos de tráfego), sem loja/produto/URL, sem histogramas ou percentis por padrão.
A média usa soma das durações dividida pela quantidade de chamadas na mesma
janela de `rate`, ponderando corretamente entre instâncias. Intervalos sem
chamadas não mostram um tempo médio artificial de zero.

A medição abrange o processamento dentro da cadeia do filtro, inclusive acertos
do cache e erros. Exclui a rede até o comprador, a renderização do navegador e o
trabalho posterior de registro das métricas. Os dados começam após publicar a
aplicação instrumentada; o contador de tráfego anterior permanece preservado.

## Falhas de instalação de scripts

O dashboard canônico inclui três cartões: quantidade estimada de falhas nas últimas
24 horas, horário da última falha e sinal vermelho quando houve uma falha nas últimas
24 horas. São indicadores visuais; não configuram envio de notificações.

- `ncf_scripts_install_failures_total{operation="associate|list|diagnose",status="404|...|other"}`:
  falhas de associação ou consulta; cada falha de chamada conta uma ocorrência.
- `ncf_scripts_install_last_failure_timestamp_seconds`: instante da última falha
  desde a inicialização do processo, zero antes da primeira. O horário permite
  identificar também uma falha anterior à primeira coleta do contador.

Sem rótulos de loja, script ou mensagem, para limitar séries. Use o log
`script.install.script_failed` para identificar loja e script. Não há retroativo;
o contador exige amostras para `increase` e o horário reinicia com o processo.
As métricas `ncf_*` já passam pelo filtro de coleta do Alloy.

## Verificação global no backoffice

Acesse **Backoffice → Scripts** (`/backoffice/scripts`) e inicie a verificação.
Apenas lojas ativas com token são selecionadas; a situação da loja é revalidada
antes de processá-la. O lote preserva scripts existentes e associa os ausentes,
processando uma loja por vez com pausa mínima de um segundo entre lojas.
O resultado é confirmado com nova leitura da API, inclusive quando uma tentativa
individual falha sem interromper os demais scripts. Falhas de uma loja não
interrompem o lote. Uma execução simultânea é impedida na instância atual.

Progresso e resultados ficam em memória até outro lote ou restart. Após reiniciar,
inicie outra verificação: a operação é idempotente. Este controle pressupõe uma
única instância do app; múltiplas réplicas exigem coordenação persistente antes
de habilitar a ação. Não remove scripts, não publica versões no Partner Portal
e não garante execução no navegador; verifica associações na API.
