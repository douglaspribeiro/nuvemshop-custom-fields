# Versões de scripts no Grafana

`npm run build` em `src/main/frontend` gera um identificador UTC de 14 dígitos, comum aos artefatos gerados no mesmo build. Cada execução envia um beacon best-effort sem dados do comprador. A aplicação conta os beacons por versão, limitando a 128 versões por processo; nomes inesperados não criam séries.

Para atualizar o Partner, usar os arquivos gerados no mesmo build:

- Legado-js: `dist/partner-legado-js.js`.
- LegadoV2: `dist/partner-legadov2.js`.
- Storefront SDK: `dist/nuvemshop-storefront-sdk.js`.
- Checkout SDK: `dist/nuvemshop-checkout-sdk.js`.

O build também gera os assets `nuvemshop-personalizer.js`, `nuvemshop-checkout.js` (checkout legado), `nuvemshop-patagonia.js`, `buyer-upload-dom.js` e `buyer-upload-widget.js`, entregues pela aplicação. O backend precisa ser implantado antes de os beacons serem contabilizados. Sincronizar o dashboard do repositório de infraestrutura e publicar os arquivos do Partner são etapas separadas do build e deploy.

O gráfico mostra execuções recebidas e seu total, não todas as chamadas HTTP. Versões antigas sem beacon não aparecem; bloqueadores, falhas de rede e coleta Prometheus podem reduzir os números. Cada série passa a existir quando a aplicação recebe o primeiro beacon; não há reconstrução histórica.
