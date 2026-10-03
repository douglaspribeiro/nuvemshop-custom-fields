# Tráfego de vitrine no Grafana

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

O rótulo `store_id` identifica somente lojas ativas validadas pelo backend; parâmetros públicos
não validados ficam como `unknown`. Não são usados IP, e-mail ou caminho como rótulos.

O contador `ncf_storefront_personalized_product_requests_total`, com `store_id` e `product_id`,
registra apenas GETs bem-sucedidos da configuração de produtos habilitados com campos disponíveis.
IDs enviados arbitrariamente pelo cliente não criam séries de produtos.

Os nomes vêm do banco, em métricas separadas: `ncf_store_info` (`store_id`, `store_name`) e
`ncf_personalized_product_info` (`store_id`, `product_id`, `store_name`, `product_name`). O catálogo
é atualizado na inicialização e a cada 60 segundos. Produtos desabilitados, sem campos ou de lojas
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
