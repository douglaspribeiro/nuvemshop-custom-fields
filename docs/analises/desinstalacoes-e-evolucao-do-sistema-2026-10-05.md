# Desinstalações e evolução do sistema — lista recebida em 05/10/2026

## Método e limites

- Lista fornecida pelo proprietário: **55 linhas**, **45 episódios de saída** e **43 lojas distintas por nome e país**.
- Um episódio agrupa nome + país + dia. Sem Store ID e horário, isso é uma aproximação: duas saídas reais da mesma loja no mesmo dia podem ter sido agrupadas.
- Duplicatas idênticas e linhas sem motivo não adicionam uma saída quando já existe outra linha da mesma loja no mesmo dia. Motivos diferentes são preservados.
- MSE_sports aparece em 23 e 30/09; Lavie Lite em 02 e 03/10. São episódios distintos, sem assumir que houve reinstalação sem confirmar os eventos.
- Datas de commits são as de gravação no Git, convertidas para America/Sao_Paulo (UTC−03). O horário da desinstalação não foi informado.
- Para cada episódio, o CSV mostra o último commit anterior ao início do dia e os commits feitos naquele dia. Nenhum deles prova qual versão estava publicada.
- A workflow deploy.yml publica imagens em pushes para deploy-*. Commit em main, tag ou imagem publicada não comprova quando o container de produção foi atualizado. Há ainda versões repetidas no histórico; usar SHA junto à versão.
- Não se calcula taxa de desinstalação por período: faltam instalações e exposição em cada período. Redução/aumento na quantidade de saídas não comprova efeito das melhorias.
- A lista não equivale às 53 desinstalações citadas no painel: são 45 episódios aproximados, não 55 lojas nem necessariamente o histórico completo.

## Motivos declarados

| Motivo | Episódios com esse motivo | % dos 45 episódios |
|---|---:|---:|
| Outros | 14 | 31.1% |
| Estava testando o aplicativo | 11 | 24.4% |
| Não tem as funcionalidades que eu preciso | 8 | 17.8% |
| Achei outro aplicativo que me atende melhor | 4 | 8.9% |
| Difícil de configurar e/ou utilizar | 4 | 8.9% |
| Preços e Planos não condiz com meu momento | 3 | 6.7% |
| Instabilidade, lentidão e erros no aplicativo | 2 | 4.4% |
| Mudei as necessidades do meu negócio: Esse aplicativo não me atende mais | 1 | 2.2% |

Há 47 associações de motivo para 45 episódios: Emerc4dor Artesanatos (24/09) e Manillas de identificación A tu ladoo (29/09) têm duas respostas diferentes. Percentuais não somam 100%. Nenhum episódio ficou inteiramente sem motivo após agrupar as linhas.

## Distribuição por fase do código

| Fase | Episódios |
|---|---:|
| Até 22/09: experiência anterior aos novos gateways e à revisão visual | 16 |
| 23–24/09: introdução de Mercado Pago/Efí e correções de checkout | 7 |
| 25–28/09: revisão visual, primeiro uso, Paddle e Patagonia | 5 |
| 29/09–01/10: métricas, vendas personalizadas e histórico de saídas | 9 |
| 02–05/10: Ultra, cupons, revisão dos planos e GA4 | 8 |

Por país: Brasil: 28; Argentina: 9; México: 6; Chile: 1; Colômbia: 1.

## Marcos verificados no código

| Data e horário (São Paulo) | Commit | Mudança e interpretação |
|---|---|---|
| 01/09 23:59 | `66a8a8d` | Documentação de homologação. A comparação anterior tinha Free, Essencial e Pro; Ultra não existia. Havia billing nativo configurável, sem afirmar sua ativação no servidor. |
| 23/09 10:40–11:21 | `b419d8e`, `b09ace0`, `2790383` | Ajustes do botão de upgrade e cortesia temporária. Mercado Pago entrou às 21:31 (`437bb19`), seguido de correções de renderização/e-mail/checkout até 23:12. |
| 24/09 23:00–25/09 02:35 | `47fffa5`, `811980a`, `880c16f`, `7c9662f`, `6134b83` | Entrada da Efí e várias correções de webhook/conciliação de pagamento. Um relato em 24/09 pode anteceder a Efí, adicionada tarde no dia. |
| 25/09 00:30–01:30 | `448beb7`, `78fbef9`, `7c9662f` | Revisão visual, versionamento de CSS e orientação de primeiro uso. Não atribuir as saídas de 13/09 e 18/09 a esta interface posterior. |
| 25/09 10:13–17:18 | `d912b72`, `8173675`, `d6b48bb`, `e270cfc` | Notificações de pagamento, expiração de tentativas pendentes, site institucional/visão operacional e canal de suporte. |
| 26/09 02:18–27/09 23:27 | `3401a2a`, `bda70e5`, `460a175`, `0fe6a80` | Paddle e ajustes de catálogo, retentativa e mensagens. Relatos em 26/09 e 27/09 coincidem com essa evolução, sem provar problema de gateway. |
| 28/09 21:11–21:15 | `a4a6968`, `83cf5ce` | Adaptador para Patagonia. A investigação posterior `1e6d335` descreve uma loja sem entrega do script legado pela plataforma. Tema das lojas desta lista não foi confirmado. |
| 29/09 00:51–11:56 | `28a475c`, `09807fc`, `c67cb48`, `7892efa`, `c70b5f7` | Métricas de vitrine, lista de desinstaladas, priorização de produtos configurados e medição de vendas personalizadas. Visibilidade interna maior não é correção automática da experiência. |
| 01/10 00:19 e 21:28–21:36 | `add0e0f`, `8526960`, `1e6d294` | Histórico de saídas/reconquista e correções de migrações/esquema. O relato de instabilidade de Kubik nesse dia merece confronto com horários de deploy/logs; o motivo não comprova que a loja foi afetada por uma migração. |
| 02/10 21:23–23:12 | `9e90ffb`, `180c54b`, `b4a6261` | Ultra, fluxo de upgrade, cupons e notificações. Saídas de 02/10 sem horário não comprovam contato com essas funcionalidades. |
| 03/10 13:02–14:18 | `cb6a7f5`, `d2007fb`, `7271d9c` | Catálogo versionado, fuso do navegador e nova comparação dos planos. Lavie Lite saiu nesse dia, mas não sabemos se antes ou depois das mudanças. |
| 05/10 10:04 | `5d443ef` | Instrumentação de funil GA4. Precisa de configuração e publicação; não recupera a navegação anterior. |

## Conclusões acionáveis

1. **Preço não é o motivo mais frequente declarado:** 3 de 45 episódios (6,7%), contra 8 de falta de funcionalidades e 4 de dificuldade de configuração. A categoria Outros (14) é vaga; não reinterpretar esses casos como preço ou bug.
2. **Não atribuir as queixas antigas ao Ultra:** La Nuestra (14/09) e Nexora 3d Studio (19/09) antecedem o commit que introduz o Ultra em 02/10. Festarola (05/10) é posterior ao commit, mas não informa qual plano ou preço foi visto.
3. **Configuração merece revisão de experiência:** Ateliê Algodão Doce (13/09) e Loja Debuteen (18/09) antecedem a orientação/revisão visual de 25/09. Manillas (29/09) e Lavie Lite (03/10) são posteriores ao commit; isso justifica investigar seus fluxos, sem afirmar que usaram a versão nova.
4. **Separar funcionalidades ausentes de funcionalidades difíceis de descobrir:** Papel e Propósito (16/09) pediu escolha de estampa/capa. SELECT já existia no código de 20/08, mas escolha visual por imagens não aparece entre os tipos de campo. A justificativa não diz se opções textuais resolveriam sua necessidade.
5. **Upload de imagem é uma lacuna concreta a avaliar:** Láser Mataderos (01/10) pediu envio de imagem pelo comprador. FieldType tem TEXT, NUMBER, SELECT e TEXTAREA, sem upload; as mudanças posteriores examinadas não adicionaram esse tipo. Registrar como oportunidade, sem compromisso de implementação.
6. **Instabilidade precisa de evidência técnica:** María Candela Joyas (23/09) e Kubik (01/10) coincidem com dias de mudanças relevantes. Faltam hora da saída, versão publicada, erros da loja e tema para confirmar a causa.
7. **Teste não equivale a rejeição definitiva:** 11 episódios declaram que estavam testando. Verificar ativação/configuração antes da saída e expectativa do lojista; não excluir esses episódios do denominador apenas para elevar a conversão.

## Episódios e contexto temporal

| Data | Loja | País | Motivo(s) | Último commit antes do dia | Commits no dia (ordem temporal desconhecida em relação à saída) |
|---|---|---|---|---|---|
| 2026-08-21 | LucascheckoutAR | Argentina | Estava testando o aplicativo | 7ab2285 |  |
| 2026-08-25 | Loja da Lu | Brasil | Estava testando o aplicativo | 7ab2285 |  |
| 2026-09-10 | Golden Marval Accesorios | México | Outros | 66a8a8d |  |
| 2026-09-13 | Ateliê Algodão Doce | Brasil | Difícil de configurar e/ou utilizar | 66a8a8d |  |
| 2026-09-13 | RIHEN | Argentina | Estava testando o aplicativo | 66a8a8d |  |
| 2026-09-14 | La Nuestra | Argentina | Preços e Planos não condiz com meu momento | 66a8a8d |  |
| 2026-09-14 | Leela Co. | Brasil | Achei outro aplicativo que me atende melhor | 66a8a8d |  |
| 2026-09-16 | ESCORPIANNY | Brasil | Outros | 66a8a8d |  |
| 2026-09-16 | GOAT CLUB | Brasil | Estava testando o aplicativo | 66a8a8d |  |
| 2026-09-16 | Papel e Propósito | Brasil | Não tem as funcionalidades que eu preciso | 66a8a8d |  |
| 2026-09-18 | Loja Debuteen \| A Loja da Debutante | Brasil | Difícil de configurar e/ou utilizar | 66a8a8d |  |
| 2026-09-18 | MiCake - Cake Shop and Dessert | México | Estava testando o aplicativo | 66a8a8d |  |
| 2026-09-18 | Mimo Studio | México | Outros | 66a8a8d |  |
| 2026-09-19 | Dos Puntos | Chile | Não tem as funcionalidades que eu preciso | 66a8a8d |  |
| 2026-09-19 | Nexora 3d Studio | Brasil | Preços e Planos não condiz com meu momento | 66a8a8d |  |
| 2026-09-21 | Estúdio Kakareco | Brasil | Outros | 66a8a8d |  |
| 2026-09-23 | Cactus | Brasil | Achei outro aplicativo que me atende melhor | 66a8a8d | b419d8e 10:40 fix: removendo botao de upgrade de plano / b09ace0 10:41 feat: add premium temporario via backoffice / ed2025a 10:49 fix: junit erros / 2790383 11:21 fix: ajustando o botao de upgrade plano / fddd66a 11:27 feat: gerador de versao / fe17393 11:42 feat: RequestFilter UUID / d744674 17:18 feat: RequestFilter UUID / 437bb19 21:31 feat: Pagamento Via Mercado Pago / d6b7b2e 22:04 fix: render payment subscription without error / 23066e8 22:14 fix: use merchant Mercado Pago email for checkout / 9a15903 22:48 fix: clarify Mercado Pago payer email / f3d2612 22:56 feat: create Mercado Pago checkout without payer email / 3db0e60 23:12 fix: cancel pending Mercado Pago checkout |
| 2026-09-23 | María Candela Joyas | Argentina | Instabilidade, lentidão e erros no aplicativo | 66a8a8d | b419d8e 10:40 fix: removendo botao de upgrade de plano / b09ace0 10:41 feat: add premium temporario via backoffice / ed2025a 10:49 fix: junit erros / 2790383 11:21 fix: ajustando o botao de upgrade plano / fddd66a 11:27 feat: gerador de versao / fe17393 11:42 feat: RequestFilter UUID / d744674 17:18 feat: RequestFilter UUID / 437bb19 21:31 feat: Pagamento Via Mercado Pago / d6b7b2e 22:04 fix: render payment subscription without error / 23066e8 22:14 fix: use merchant Mercado Pago email for checkout / 9a15903 22:48 fix: clarify Mercado Pago payer email / f3d2612 22:56 feat: create Mercado Pago checkout without payer email / 3db0e60 23:12 fix: cancel pending Mercado Pago checkout |
| 2026-09-23 | MSE_sports | Brasil | Outros | 66a8a8d | b419d8e 10:40 fix: removendo botao de upgrade de plano / b09ace0 10:41 feat: add premium temporario via backoffice / ed2025a 10:49 fix: junit erros / 2790383 11:21 fix: ajustando o botao de upgrade plano / fddd66a 11:27 feat: gerador de versao / fe17393 11:42 feat: RequestFilter UUID / d744674 17:18 feat: RequestFilter UUID / 437bb19 21:31 feat: Pagamento Via Mercado Pago / d6b7b2e 22:04 fix: render payment subscription without error / 23066e8 22:14 fix: use merchant Mercado Pago email for checkout / 9a15903 22:48 fix: clarify Mercado Pago payer email / f3d2612 22:56 feat: create Mercado Pago checkout without payer email / 3db0e60 23:12 fix: cancel pending Mercado Pago checkout |
| 2026-09-23 | TIENDA DOMESTICA | Argentina | Não tem as funcionalidades que eu preciso | 66a8a8d | b419d8e 10:40 fix: removendo botao de upgrade de plano / b09ace0 10:41 feat: add premium temporario via backoffice / ed2025a 10:49 fix: junit erros / 2790383 11:21 fix: ajustando o botao de upgrade plano / fddd66a 11:27 feat: gerador de versao / fe17393 11:42 feat: RequestFilter UUID / d744674 17:18 feat: RequestFilter UUID / 437bb19 21:31 feat: Pagamento Via Mercado Pago / d6b7b2e 22:04 fix: render payment subscription without error / 23066e8 22:14 fix: use merchant Mercado Pago email for checkout / 9a15903 22:48 fix: clarify Mercado Pago payer email / f3d2612 22:56 feat: create Mercado Pago checkout without payer email / 3db0e60 23:12 fix: cancel pending Mercado Pago checkout |
| 2026-09-24 | Emerc4dor Artesanatos | Brasil | Estava testando o aplicativo / Outros | 5d2c8d7 | 47fffa5 23:00 feat: Pagamento Via Efi / b1973f8 23:25 fix: Pagamento Via Efi / 48623c3 23:44 fix: Pagamento Via Efi layout / e2e609b 23:57 fix: Pagamento Via Efi layout |
| 2026-09-24 | EQV Tienda | Argentina | Achei outro aplicativo que me atende melhor | 5d2c8d7 | 47fffa5 23:00 feat: Pagamento Via Efi / b1973f8 23:25 fix: Pagamento Via Efi / 48623c3 23:44 fix: Pagamento Via Efi layout / e2e609b 23:57 fix: Pagamento Via Efi layout |
| 2026-09-24 | Set & Go | México | Não tem as funcionalidades que eu preciso | 5d2c8d7 | 47fffa5 23:00 feat: Pagamento Via Efi / b1973f8 23:25 fix: Pagamento Via Efi / 48623c3 23:44 fix: Pagamento Via Efi layout / e2e609b 23:57 fix: Pagamento Via Efi layout |
| 2026-09-25 | Maifon Apple Store | Argentina | Estava testando o aplicativo | dea28e9 | 811980a 00:20 fix: Pagamento Via Efi webhook / 448beb7 00:30 feat: Melhoria visual 2.0 / 78fbef9 00:52 feat: versionar CSS e refinar painel e checkout / 880c16f 01:03 fix: conciliar resposta de pagamento da assinatura Efi / 7c9662f 01:30 fix: corrige conciliação Efí e orienta primeiro uso / 6134b83 02:35 fix: reconcile pending payments safely and format BRL prices / d912b72 10:13 feat: notify confirmed payments and confirm subscription cancellation / 8173675 10:47 fix: expira tentativas pendentes da Efi / d6b48bb 16:04 feat: adiciona site institucional e visão operacional / 7c46462 16:41 chore: Removendo tag de pagamento efi do texto / e270cfc 17:18 fix: canal de suporte / 6b89a99 22:32 chore: DOCS |
| 2026-09-26 | morihousestore | Brasil | Achei outro aplicativo que me atende melhor | 6b89a99 | 3401a2a 02:18 feat: add Paddle multigateway routing / 80c3191 02:24 chore: adicionando passo passo |
| 2026-09-27 | Fanficaria Store | Brasil | Estava testando o aplicativo | 80c3191 | 095e80b 18:18 chore: paddle / bda70e5 20:10 fix: preserve Paddle catalog validation feedback / ccd4d59 20:13 test: mark Paddle catalog fixture as validated / f0e3d07 20:31 feat: save payment catalog prices without page reload / 460a175 20:36 fix: allow retry after Paddle checkout setup error / c11f549 22:26 fix: enable Paddle catalog setup for Brazil / 676a025 22:54 feat: manage Efi plan IDs from payment catalog / 0fe6a80 23:20 fix: hide payment integration errors from merchants / 5e14554 23:27 fix: preserve localized billing availability messages |
| 2026-09-27 | Temaki Studio | Brasil | Outros | 80c3191 | 095e80b 18:18 chore: paddle / bda70e5 20:10 fix: preserve Paddle catalog validation feedback / ccd4d59 20:13 test: mark Paddle catalog fixture as validated / f0e3d07 20:31 feat: save payment catalog prices without page reload / 460a175 20:36 fix: allow retry after Paddle checkout setup error / c11f549 22:26 fix: enable Paddle catalog setup for Brazil / 676a025 22:54 feat: manage Efi plan IDs from payment catalog / 0fe6a80 23:20 fix: hide payment integration errors from merchants / 5e14554 23:27 fix: preserve localized billing availability messages |
| 2026-09-28 | Ateliê Artes Pop | Brasil | Não tem as funcionalidades que eu preciso | 4a6d7fc | a4a6968 21:11 fix: sandbox patagonia / 83cf5ce 21:15 fix: sandbox patagonia / 1e6d335 23:06 doc: investigação patagonia |
| 2026-09-29 | boneca3d | Brasil | Outros | 1e6d335 | 28a475c 00:51 feat: add storefront Grafana traffic metrics / 09807fc 02:03 feat: rank storefront traffic by store and list uninstalled stores / c67cb48 09:39 feat: prioritize configured products and track sold items / 3f9e6f5 09:59 fix: assign unique Flyway version to sales migration / c431cdc 10:23 fix: treat empty order ranges as no sales / 7892efa 11:27 feat: show sales values and sort configured products / c70b5f7 11:43 fix: limit sales report to personalized products / ac50d27 11:56 fix: align backoffice sales count with personalized orders |
| 2026-09-29 | Manillas de identificación A tu ladoo | Colômbia | Difícil de configurar e/ou utilizar / Mudei as necessidades do meu negócio: Esse aplicativo não me atende mais | 1e6d335 | 28a475c 00:51 feat: add storefront Grafana traffic metrics / 09807fc 02:03 feat: rank storefront traffic by store and list uninstalled stores / c67cb48 09:39 feat: prioritize configured products and track sold items / 3f9e6f5 09:59 fix: assign unique Flyway version to sales migration / c431cdc 10:23 fix: treat empty order ranges as no sales / 7892efa 11:27 feat: show sales values and sort configured products / c70b5f7 11:43 fix: limit sales report to personalized products / ac50d27 11:56 fix: align backoffice sales count with personalized orders |
| 2026-09-30 | Cuia Sul | Brasil | Estava testando o aplicativo | eeb7ef8 |  |
| 2026-09-30 | Eldaro | Brasil | Outros | eeb7ef8 |  |
| 2026-09-30 | Memorial Calice | Brasil | Não tem as funcionalidades que eu preciso | eeb7ef8 |  |
| 2026-09-30 | MSE_sports | Brasil | Outros | eeb7ef8 |  |
| 2026-10-01 | Kubik 3D Sta Catarina | México | Instabilidade, lentidão e erros no aplicativo | eeb7ef8 | add0e0f 00:19 feat: preserve uninstall history and add winback flow / 6251864 19:07 docs: document SQS SES and Patagonia release steps / b3fcff2 19:13 feat: restrict winback flow during testing / 07b9256 19:31 fix: enforce winback allow list before publishing / bed3bca 19:41 fix: keep winback properties bindable / 8526960 21:28 fix: preserve applied Flyway migrations / 1e6d294 21:35 fix: restore sales schema expected by application |
| 2026-10-01 | Láser Mataderos | Argentina | Não tem as funcionalidades que eu preciso | eeb7ef8 | add0e0f 00:19 feat: preserve uninstall history and add winback flow / 6251864 19:07 docs: document SQS SES and Patagonia release steps / b3fcff2 19:13 feat: restrict winback flow during testing / 07b9256 19:31 fix: enforce winback allow list before publishing / bed3bca 19:41 fix: keep winback properties bindable / 8526960 21:28 fix: preserve applied Flyway migrations / 1e6d294 21:35 fix: restore sales schema expected by application |
| 2026-10-01 | Vivencea | Brasil | Outros | eeb7ef8 | add0e0f 00:19 feat: preserve uninstall history and add winback flow / 6251864 19:07 docs: document SQS SES and Patagonia release steps / b3fcff2 19:13 feat: restrict winback flow during testing / 07b9256 19:31 fix: enforce winback allow list before publishing / bed3bca 19:41 fix: keep winback properties bindable / 8526960 21:28 fix: preserve applied Flyway migrations / 1e6d294 21:35 fix: restore sales schema expected by application |
| 2026-10-02 | Azul Clarito | México | Não tem as funcionalidades que eu preciso | 75a64eb | 9e90ffb 21:23 feat: add Ultra upgrades, local homologation and merchant lifecycle tools / 180c54b 22:24 feat: manage upgrade coupons and improve merchant upgrade layout / b4a6261 23:12 feat: notify Discord when EFI plan upgrades complete |
| 2026-10-02 | Ferronnier - Cortes e Formas | Brasil | Estava testando o aplicativo | 75a64eb | 9e90ffb 21:23 feat: add Ultra upgrades, local homologation and merchant lifecycle tools / 180c54b 22:24 feat: manage upgrade coupons and improve merchant upgrade layout / b4a6261 23:12 feat: notify Discord when EFI plan upgrades complete |
| 2026-10-02 | Lavie Lite | Brasil | Outros | 75a64eb | 9e90ffb 21:23 feat: add Ultra upgrades, local homologation and merchant lifecycle tools / 180c54b 22:24 feat: manage upgrade coupons and improve merchant upgrade layout / b4a6261 23:12 feat: notify Discord when EFI plan upgrades complete |
| 2026-10-02 | qmatecito | Argentina | Estava testando o aplicativo | 75a64eb | 9e90ffb 21:23 feat: add Ultra upgrades, local homologation and merchant lifecycle tools / 180c54b 22:24 feat: manage upgrade coupons and improve merchant upgrade layout / b4a6261 23:12 feat: notify Discord when EFI plan upgrades complete |
| 2026-10-02 | Roupas de Bebê e Recém-Nascido \| Gigi Bambini | Brasil | Outros | 75a64eb | 9e90ffb 21:23 feat: add Ultra upgrades, local homologation and merchant lifecycle tools / 180c54b 22:24 feat: manage upgrade coupons and improve merchant upgrade layout / b4a6261 23:12 feat: notify Discord when EFI plan upgrades complete |
| 2026-10-03 | Lavie Lite | Brasil | Difícil de configurar e/ou utilizar | 1a94474 | cb6a7f5 13:02 feat: manage versioned plan limits and improve revenue reports / d2007fb 13:30 fix: display timestamps in the user's browser timezone / 7271d9c 14:18 feat: redesign merchant billing plan comparison |
| 2026-10-05 | Festarola | Brasil | Preços e Planos não condiz com meu momento | 60facd8 | 5d443ef 10:04 feat: track purchase and upgrade funnels in GA4 |
| 2026-10-05 | Thiemi Artes em Papel - Papelaria Personalizada | Brasil | Outros | 60facd8 | 5d443ef 10:04 feat: track purchase and upgrade funnels in GA4 |

## Próxima validação

Confirmar Store ID, horário de saída e primeira instalação por loja; associar os motivos aos cadastros corretos. Consultar histórico de deploy/container e logs, com atenção a 23/09, 01/10 e à saída de Lavie Lite em 03/10. A análise não alterou cadastros no banco nem preencheu motivos automaticamente por nome.

O relatório `/backoffice/reports/adoption` complementa esta análise com configuração salva, vendas personalizadas sincronizadas, situação de assinatura e motivos já cadastrados. Dados de saída apagados permanecem apenas nos totais agregados; o aplicativo não restaura cadastros a partir deste CSV.

Artefatos: lista original em `desinstalacoes-nuvemshop-2026-10-05.csv`; agrupamento e cruzamento em `desinstalacoes-cruzadas-com-commits-2026-10-05.csv`. Regeneração: `python3 scripts/analyze-uninstall-history.py`.
