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

Os contadores não recebem `storeId`, produto, caminho da loja ou IP como rótulos. Esses valores
vêm de endpoints públicos e fariam o Prometheus acumular séries sem limite. Para diagnóstico por
loja, mantenha os `integration_logs` existentes; para volume agregado, use o Grafana.

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

O download do arquivo representa uma requisição que chegou à origem. Se uma CDN passar a servir
o asset do cache, ela não chega ao app e deve ser acompanhada também pelas métricas da própria
CDN. Já o beacon indica que o JavaScript de fato executou no navegador, mas pode faltar quando o
cliente bloqueia requisições de telemetria.

As telas administrativas já carregam GA4. Elas não entram no contador `ncf_storefront_http_requests_total`.
