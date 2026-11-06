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

Os gráficos de séries temporais usam normalmente **Max data points = 100**, para que o
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

## Organização e comparação de downloads

O painel de variação diária usa barras. Os quatro indicadores de falhas de consulta
ou associação ficam no rodapé; o painel “Volume por tipo no período” foi removido.
Saúde e cobertura da coleta ocupam cartões compactos ao lado dos totais.

“Chamadas por minuto por tipo” usa `rate(...[4m]) * 60`, com janela fixa de quatro
minutos e até 1.500 pontos. A unidade é chamadas/minuto; cada ponto representa uma
média nessa janela, mesmo quando o espaçamento entre pontos é maior.

O cartão de downloads mostra a oscilação percentual do dia em São Paulo até o fim
do período consultado contra a média do mesmo trecho horário dos sete dias
anteriores. No período padrão encerrado em agora, corresponde ao dia atual.
A consulta integra taxas de quatro minutos em passos de um minuto. Trata-se de
uma estimativa; lacunas parciais podem distorcer a comparação. Sem base positiva
ou dados em algum dos sete dias, o cartão não apresenta um percentual.

## Consumo horário dos eventos de e-mail SES

O dashboard canônico `ncf-storefront-traffic` inclui a seção **E-mails · consumo
horário da email-tracking-events**. Os indicadores usam métricas da aplicação,
coletadas pelo Alloy já existente. `ncf_*` passa pelo allowlist atual; não é
necessário habilitar outro consumidor SQS para observar a fila.

`SesEventConsumer` atende winback e NC-39. Por padrão, consulta mensagens **uma
vez por hora**, e a primeira execução ocorre uma hora após iniciar a aplicação.
Cada execução lê lotes de até dez mensagens até a primeira resposta vazia, com
limite de cem lotes (até mil retornos) e orçamento de 45 segundos verificado entre
lotes. O restante aguarda o próximo ciclo. A configuração é
`AWS_SES_EVENTS_POLL_DELAY_MS=3600000`. Não há preenchimento retroativo de métricas.

A quantidade disponível, em processamento e com atraso é consultada separadamente
via GetQueueAttributes, também a cada hora (`AWS_SES_EVENTS_ATTRIBUTES_DELAY_MS=3600000`).
A role da aplicação precisa de `sqs:GetQueueAttributes` na mesma fila para esse
indicador. Falta dessa permissão não impede ReceiveMessage/DeleteMessage.
As quantidades são [aproximadas pelo SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_GetQueueAttributes.html).

| Métrica Prometheus | Significado |
| --- | --- |
| `ncf_ses_events_consumer_enabled` | Flag de habilitação; não comprova conectividade |
| `ncf_ses_events_messages_total{result="received"}` | Retornos de mensagens pelo SQS, incluindo reentregas |
| `ncf_ses_events_messages_total{result="processed"}` | Ambos os processadores terminaram sem exceção, inclusive eventos ignorados |
| `ncf_ses_events_messages_total{result="acknowledged"}` | DeleteMessage retornou sucesso após o processamento |
| `ncf_ses_events_polls_total` | Chamadas ReceiveMessage bem-sucedidas, inclusive respostas vazias |
| `ncf_ses_events_failures_total{stage="receive\|process\|acknowledge\|attributes"}` | Falhas separadas por etapa; os quatro valores são fixos |
| `ncf_ses_events_last_poll_timestamp_seconds` | Última consulta de mensagens bem-sucedida neste processo |
| `ncf_ses_events_last_ack_timestamp_seconds` | Última remoção confirmada neste processo |
| `ncf_ses_events_queue_depth{state="visible\|in_flight\|delayed"}` | Quantidade aproximada por estado |
| `ncf_ses_events_last_attributes_timestamp_seconds` | Última amostra completa dos atributos da fila |

Consumir/remover uma mensagem SQS não comprova envio, entrega ou abertura de e-mail.
Os gráficos de volume mostram picos horários. Fila vazia ainda atualiza a última
leitura; nenhuma leitura neste processo tem timestamp zero e aparece com indicação
própria. Sem séries significa versão instrumentada ainda não publicada ou falha de
coleta, não ausência de eventos.

Antes da primeira amostra, ou quando GetQueueAttributes falha, o backlog é NaN
(desconhecido), nunca zero. O gráfico oculta amostras com mais de 65 minutos e usa
máximo entre réplicas para não somar o mesmo backlog. Avaliar timestamps junto à
saúde da coleta do app. As métricas não têm rótulos de loja, destinatário, token,
ID de mensagem ou URL da fila. Os contadores/timestamps reiniciam com o processo.

Logs compartilhados: `ses.events.poll_failed`, `ses.events.event_failed` e
`ses.events.attributes_failed`; somente classe da exceção, sem corpo do evento.
As falhas de processamento e de remoção mantêm a mensagem para nova tentativa.
O intervalo horário significa que um bounce/complaint pode levar até o próximo
ciclo para ser aplicado; o link próprio de descadastro segue imediato.

Para aparecerem dados, publicar também a versão instrumentada da aplicação e
carregar as variáveis SES no novo container. A atualização isolada do dashboard
não altera o processo nem habilita a campanha de e-mails NC-39.

Validação em 04/11/2026: 940 testes Java, zero falhas/erros e um ignorado;
11 testes específicos das métricas, incluindo ACK falho, leitura vazia, falha de
atributos, múltiplos lotes, limite de lotes e exportação Prometheus. O build limpo
removeu a classe antiga `WinbackSesEventConsumer`; o bean atual é `SesEventConsumer`.
Catálogo Grafana validado, cópias app/infra iguais e diff sem erros de whitespace.

O painel foi publicado no Grafana Cloud, UID `ncf-storefront-traffic`, versão 35,
com backup `1793847309-a7b5fcb1.json` pelo reconciliador. O plano posterior mostrou
`noop`/sem drift para todo o catálogo. As nove consultas novas executaram com
sucesso no datasource real, ainda sem séries porque esta versão instrumentada do
app não foi implantada. Sem verificação visual no navegador. A atualização do JSON
canônico está pendente de commit nos checkouts local e do servidor; versionar e
publicar o catálogo para preservar a mudança nas próximas sincronizações da infra.

## Consumo manual no backoffice

Em **Backoffice → Eventos de e-mail** (`/backoffice/email-events`), o botão
**Executar consumo da fila** solicita uma execução imediata em segundo plano.
A página acompanha a execução e mostra recebidas, tratadas, remoções confirmadas,
falhas, horários e atualização da quantidade na fila para o Grafana. Atualiza a
cada cinco segundos somente enquanto a execução estiver em andamento.

A ação exige sessão autenticada do backoffice, POST e token vinculado à sessão.
GET não consome a fila. O botão fica desabilitado quando há uma execução em
andamento ou a configuração está incompleta; erros de URL indicam que se deve
usar a URL completa da fila, em vez do nome isolado. A flag
`AWS_SES_EVENTS_ENABLED` continua necessária.

Execução manual e agendada compartilham uma reserva em memória por instância:
um clique repetido ou a rotina automática não inicia outro consumo simultâneo.
A reserva é liberada também em falhas e rejeição do executor durante shutdown.
O resultado fica em memória até a próxima execução ou reinicialização. Instâncias
diferentes continuam sujeitas à entrega pelo menos uma vez do SQS e à deduplicação
persistida dos processadores.

O consumo manual usa os mesmos limites de lotes e duração, a mesma confirmação
de remoção após processamento e as mesmas métricas. Também consulta os atributos
da fila imediatamente, sem aguardar o próximo horário. Uma falha nessa consulta
é apresentada separadamente e não transforma falha de leitura em fila vazia.
O botão não dispara campanhas e não altera o intervalo automático de uma hora.

## Elegibilidade de ativação e gráfico de envios

A seção **Ativação · elegibilidade e envios D3 / D7 / D11** mostra lojas elegíveis,
liberadas para envio real, pendências persistidas, revisões de entrega, bloqueios
e controles da campanha. O snapshot é calculado somente em leitura na inicialização
e a cada hora (`ACTIVATION_EMAIL_METRICS_DELAY_MS`, padrão `3600000`), mesmo quando
a campanha está desligada. A consulta não enfileira, reserva, simula ou envia.

D11 inclui qualquer instalação ativa de pelo menos 11 dias sem campos configurados
atualmente, sem limite máximo de idade e sem restrição de corte/lista. D3/D7
preservam essas restrições e sua janela de 24h. Todas as etapas respeitam o
bloqueio de contato e o histórico de entrega. A avaliação usa o mesmo método de
público do worker e exige endereço válido para contar uma loja como elegível.

| Métrica | Significado |
| --- | --- |
| `ncf_activation_email_stores{status}` | Lojas distintas por eligible/waiting/blocked/handled/review |
| `ncf_activation_email_eligible{step}` | Candidatas D3/D7/D11, independentemente das flags globais |
| `ncf_activation_email_ready{step}` | Candidatas com os controles locais de envio real liberados |
| `ncf_activation_email_blocked{reason}` | Lojas cuja situação final é blocked, uma causa por loja |
| `ncf_activation_email_outbox{step,state}` | Quantidade atual de mensagens persistidas, excluindo ACTIVATED |
| `ncf_activation_email_control{control}` | Habilitação, simulação, proteções e configuração local |
| `ncf_activation_email_snapshot_timestamp_seconds` | Último snapshot bem-sucedido |
| `ncf_activation_email_metrics_failures_total` | Falhas de atualização do snapshot |
| `ncf_activation_email_sent_total{step}` | Retornos confirmados do transporte, separados por etapa |

A situação da loja prioriza uma etapa elegível; depois revisão, D11 já tratado,
aguardo de prazo e bloqueio. O motivo de bloqueio prioriza D11 quando aplicável.
As contagens da fila incluem histórico de lojas inativas e não são o mesmo público
das lojas elegíveis. SIMULATED, CLAIMED, REVIEW e FAILED não são envios novos aptos
a reprocessamento automático. O worker sempre revalida antes do envio.

O **gráfico de linha** usa `increase(ncf_activation_email_sent_total[1h])`:
cada ponto estima quantos envios foram confirmados na hora móvel anterior, em
linhas D3/D7/D11. O contador aumenta após retorno bem-sucedido do transporte,
inclusive se houver falha posterior ao registrar o estado no banco. Resultados
SMTP incertos não são contados como confirmados. Simulações ficam separadas no
estado SIMULATED da fila. O gráfico não comprova entrega, abertura ou leitura.

Contadores de envio começam em zero ao iniciar a versão instrumentada e não
recuperam envios anteriores. `increase` exige amostras e trata reinícios; lacunas
e envios anteriores à primeira coleta não são reconstruídos. Os stocks persistidos
podem diminuir por exclusão LGPD e são gauges, nunca tratados como contadores.
Os gráficos usam máximo entre réplicas para não somar snapshots duplicados do
mesmo banco. Contadores de envio, por outro lado, são somados entre instâncias.

Antes do primeiro snapshot, as contagens são desconhecidas (NaN), não zero.
Falhas mantêm o timestamp anterior; o dashboard oculta snapshots com mais de
65 minutos. Não há labels de loja, e-mail, ID de mensagem ou token. Os valores
são estimativas operacionais com atualização horária; configuração de SMTP/SES
não comprova autorização ou conectividade do provedor.

Publicar o JSON pelo fluxo normal da infraestrutura e a imagem do app é necessário
para os novos dados. Esta alteração não modifica o checkout no servidor, não
publica imagens e não habilita o envio real da campanha.

Validação desta etapa em 05/11/2026: suíte Java com 994 testes, zero falhas/erros e
um ignorado; catálogo Grafana válido e cópias do dashboard iguais. As onze consultas
novas foram executadas com sucesso no datasource real, ainda sem séries desta versão.
A consulta remota foi somente leitura e confirmou checkout limpo no servidor.
Nenhuma publicação de dashboard, alteração do servidor, push ou deploy nesta etapa.

## Pendências registradas em 05/11/2026

- **Nove falhas de validação D11:** a verificação de produção de 05/11/2026 encontrou
  nove mensagens FAILED após três tentativas pré-SMTP por loja. Revisar e corrigir
  os cadastros antes de reabrir tentativas de forma controlada. Não retentar SENT,
  CLAIMED ou REVIEW nem remover bloqueios de contato. Nesta data ainda não há ação
  no backoffice para reabrir FAILED; o botão de consumo SQS não reenvia esses e-mails.
- **Comparação diária de envios e consumo:** substituir as visualizações temporais
  dessas duas operações por **últimos 15 dias de calendário**, no fuso
  `America/Sao_Paulo`, com um ponto por dia e totais diários comparáveis. Não usar
  uma janela móvel das últimas 24 horas. Se incluir o dia atual, identificá-lo como
  parcial e distingui-lo dos dias completos. Sem dados não significa zero e não
  reconstruir histórico ausente. Esta mudança de visualização está registrada como
  pendência; o gráfico atual de envios ainda usa uma hora móvel e o de consumo, taxa.
