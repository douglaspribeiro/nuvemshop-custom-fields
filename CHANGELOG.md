# Changelog

Gerado por `scripts/changelog.sh` a partir das mensagens de commit.
A versão vem do `pom.xml` e é a mesma da tag git e da imagem Docker.

## [1.50.1] - 2026-10-30

### Correções

- junit teste (`2605f9e`)
- preserve personalization drafts and guard Patagonia checkout (`88292b3`)

## [1.50.0] - 2026-10-29

### Features

- show product thumbnails in merchant product list (`c720357`)

## [1.49.0] - 2026-10-28

### Features

- backoffice: add user journey audit screen (`bd300dc`)

## [1.48.1] - 2026-10-28

### Correções

- dodo: allow new checkout after terminal subscription failure (`9e9408b`)

## [1.48.0] - 2026-10-28

### Features

- audit: persist merchant user journeys (`10a2d37`)

## [1.47.0] - 2026-10-28

### Features

- reports: add subscription reconciliation V1 (`bf53c5a`)

### Correções

- junit teste (`5b018b8`)

### Manutenção

- grafana: reorganiza indicadores e compara downloads diários (`b851667`)

## [1.46.0.1] - 2026-10-27

### Outros

- Permite enviar e-mail ao administrador da loja pelo backoffice (`f37631b`)

## [1.46.0] - 2026-10-27

### Features

- add global script installation and failure metrics (`e20b277`)

## [1.45.4] - 2026-10-26

### Performance

- metrics: split store counter to cut Prometheus series (`c98ea58`)

## [1.45.3] - 2026-10-23

### Correções

- payments: honor selected plan when replacing pending checkout (`238d811`)

## [1.45.2] - 2026-10-23

### Correções

- payments: close unpaid subscriptions when switching plans (`ced15c2`)

## [1.45.1] - 2026-10-23

### Correções

- payments: allow switching plans with a pending checkout (`652ba13`)

## [1.45.0] - 2026-10-23

### Features

- payments: integrate dLocal Go subscriptions for Argentina (`0c1b86a`)

## [1.44.1] - 2026-10-21

### Performance

- reduce storefront load for stores without usable fields (`b85d68b`)

## [1.44.0] - 2026-10-21

### Features

- melhora notificações de compra com loja e MRR estimado (`dd923d4`)
- grafana: adiciona comparativos diários de tráfego (`b8cb400`)

### Correções

- simplifica retenção GHCR sem captura SSH (`1a8b7b2`)

### Documentação

- registra máscara de WhatsApp adiada em branch (`b9f70f1`)
- registra plano de modelos personalizados em massa (`11dbb4c`)

### Outros

- Reduce routine request and navigation logging (`24dbbf5`)

## [1.43.1] - 2026-10-21

### Correções

- valida formato do WhatsApp no modelo infantil (`fbccd99`)

## [1.43.0] - 2026-10-21

### Features

- adiciona modelo de personalização infantil (`1c2e397`)

## [1.42.4] - 2026-10-21

### Correções

- reconcile personalized sales history and report coverage (`65c6efa`)

## [1.42.3] - 2026-10-21

### Correções

- capture live GHCR protection automatically during publication (`f60251e`)
- reuse Docker login credentials for GHCR cleanup (`964fa93`)
- run GHCR cleanup after every Docker publication (`4575063`)
- retain the latest 20 published GHCR images by default (`346db03`)

## [1.42.2] - 2026-10-21

### Correções

- align personalized sales reports with configured fields (`8ca3eac`)

## [1.42.1] - 2026-10-20

### Correções

- show configured personalization fields in recent orders (`376756e`)

## [1.42.0] - 2026-10-20

### Features

- add optional GHCR image retention cleanup (`7cca4c4`)

### Correções

- pause sales synchronization during deployment shutdown (`2ef2f15`)

## [1.41.1] - 2026-10-20

### Correções

- Struip valitation key (`975fe86`)

## [1.41.0] - 2026-10-20

### Features

- integra Stripe para assinaturas na Argentina (`5d7ed58`)

### Correções

- corrige regressao nos testes de pagamentos (`5caed82`)

## [1.40.7] - 2026-10-20

### Correções

- fall back from disabled Dodo test gateway (`2d6ab00`)

## [1.40.6] - 2026-10-20

### Correções

- show live plans after sandbox subscription (`697d821`)

## [1.40.5] - 2026-10-20

### Correções

- trust validated Dodo catalog at checkout (`f8f37a1`)

## [1.40.4] - 2026-10-20

### Correções

- restart checkout for canceled subscriptions (`789d8da`)

## [1.40.3] - 2026-10-20

### Correções

- allow resubscribe after cancellation (`1570616`)

## [1.40.2] - 2026-10-20

### Correções

- retain validated Dodo purchase after catalog refresh (`07f8625`)

## [1.40.1] - 2026-10-20

### Correções

- reconcile localized Dodo subscriptions (`f832725`)

## [1.40.0.2] - 2026-10-20

### Outros

- Accept opaque Dodo API keys (`db4f648`)
- Permite contratar plano durante cortesia (`256eb1f`)

## [1.40.0.1] - 2026-10-19

### Outros

- Remove integração Paddle (`bb946af`)

## [1.40.0] - 2026-10-16

### Features

- support multiple checkbox selections per field (`fa10090`)

## [1.39.0] - 2026-10-16

### Features

- add checkbox personalization fields (`e43ecf8`)

### Correções

- reflect image settings in field editor preview (`b1887cf`)

## [1.38.0] - 2026-10-16

### Features

- configure mobile image columns and verify MySQL migrations (`02f0ff5`)
- allow hiding image enlargement links per field (`1ef0fec`)

### Outros

- planos (`25eaf83`)

## [1.37.0] - 2026-10-15

### Features

- integra Dodo com preços regionais e catálogo sandbox (`84d930f`)
- adiciona página pública de status com UptimeRobot (`8187032`)

### Correções

- preserva fluxo de upgrade ao verificar provedor Dodo (`82ed076`)

### Outros

- Ajusta granularidade do Grafana e cache de planos para 24h (`51419f8`)

## [1.36.0] - 2026-10-15

### Features

- aprimora relatórios de uso gratuito e pedidos por plano (`4b725c5`)

## [1.35.1] - 2026-10-14

### Correções

- invalidate caches without holding database transactions on reader locks (`bcbac8b`)

## [1.35.0] - 2026-10-14

### Features

- measure average storefront response time by request type (`2ef6207`)

### Testes

- cover product and custom field cache invalidation (`fd7f0b8`)

## [1.34.0] - 2026-10-14

### Features

- cache plan catalog for 30 minutes and storefront for one minute (`3e99063`)

## [1.33.0] - 2026-10-14

### Features

- cache storefront responses with Caffeine for 25 seconds (`cd5d22a`)

## [1.32.0] - 2026-10-08

### Features

- customize select prompts and require product option selection (`bd9685f`)

### Outros

- fix test (`04a40be`)

## [1.31.0] - 2026-10-08

### Features

- support validated deployment and coordinated shutdown (`dd1d121`)

### Correções

- show a selection prompt in product option fields (`9aad089`)

### Outros

- docs (`a2f619b`)

## [1.30.0] - 2026-10-06

### Features

- update international USD pricing and Creem marketing assets (`889989c`)

## [1.29.1] - 2026-10-06

### Correções

- show pending confirmation for accepted Creem upgrades (`2f2de78`)
- expire abandoned checkouts before new subscriptions (`9d20d5b`)

## [1.29.0] - 2026-10-06

### Features

- integrate Creem payments with plan publishing and sandbox restrictions (`c9015dd`)

## [1.28.1] - 2026-10-06

### Outros

- melhorias de infra (`fb4bcf3`)

## [1.28.0] - 2026-10-06

### Features

- melhore a comparação dos planos na página de preços (`62da937`)
- preserve configurações de lojas desinstaladas para diagnóstico (`a331a8c`)

### Correções

- explique os bloqueios no envio de reconquista (`f975526`)

## [1.27.0] - 2026-10-06

### Features

- simplifique a configuração de produtos e atualize a loja de aplicativos (`315709f`)
- show country-specific public prices and support contact (`a412b66`)
- track and display merchant last access (`d4ee88e`)

### Correções

- disable Mercado Pago grace period by default (`597ad28`)

### Documentação

- add app store gallery and buyer image upload roadmap (`5e46d2c`)

### Outros

- melhorias de infra (`3ea7ed2`)

## [1.26.0] - 2026-10-05

### Features

- allow seven-day courtesy plans (`679f37d`)

## [1.25.0] - 2026-10-05

### Features

- show personalization images in cart and checkout (`6e203ff`)
- enforce image option limits by subscription plan (`013548a`)

## [1.24.0] - 2026-10-05

### Features

- add S3 image options and adoption reports (`8eb6fbb`)

## [1.23.0] - 2026-10-03

### Features
- track purchase and upgrade funnels in GA4 (`5d443ef`)
- redesign merchant billing plan comparison (`7271d9c`)

## [1.22.0] - 2026-10-03

### Features

- manage versioned plan limits and improve revenue reports (`cb6a7f5`)

### Correções

- display timestamps in the user's browser timezone (`d2007fb`)

## [1.21.0] - 2026-10-02

### Features

- notify Discord when EFI plan upgrades complete (`b4a6261`)

## [1.20.0] - 2026-10-02

### Features

- manage upgrade coupons and improve merchant upgrade layout (`180c54b`)

## [1.19.0] - 2026-10-02

### Features

- add Ultra upgrades, local homologation and merchant lifecycle tools (`9e90ffb`)

## [1.18.0] - 2026-10-01

### Features

- show sales values and sort configured products (`7892efa`)

### Correções

- align backoffice sales count with personalized orders (`ac50d27`)
- limit sales report to personalized products (`c70b5f7`)

## [1.17.2] - 2026-10-01

### Correções

- restore sales schema expected by application (`1e6d294`)

## [1.17.1] - 2026-10-01

### Correções

- preserve applied Flyway migrations (`8526960`)

## [1.17.0] - 2026-10-01

### Features

- restrict winback flow during testing (`b3fcff2`)
- preserve uninstall history and add winback flow (`add0e0f`)

### Correções

- keep winback properties bindable (`bed3bca`)
- enforce winback allow list before publishing (`07b9256`)

### Documentação

- document SQS SES and Patagonia release steps (`6251864`)

## [1.16.0] - 2026-10-01

### Features

- restrict winback flow during testing (`b3fcff2`)
- preserve uninstall history and add winback flow (`add0e0f`)

### Correções

- keep winback properties bindable (`bed3bca`)
- enforce winback allow list before publishing (`07b9256`)
- treat empty order ranges as no sales (`c431cdc`)

### Documentação

- document SQS SES and Patagonia release steps (`6251864`)

## [1.15.1] - 2026-09-29

### Correções

- assign unique Flyway version to sales migration (`3f9e6f5`)

## [1.15.0] - 2026-09-29

### Features

- prioritize configured products and track sold items (`c67cb48`)

## [1.14.0] - 2026-09-29

### Features

- rank storefront traffic by store and list uninstalled stores (`09807fc`)

## [1.13.0] - 2026-09-29

### Features

- add storefront Grafana traffic metrics (`28a475c`)

### Outros

- investigação patagonia (`1e6d335`)

## [1.12.3] - 2026-09-28

### Correções

- sandbox patagonia (`83cf5ce`)

## [1.12.2] - 2026-09-28

### Correções

- sandbox patagonia (`a4a6968`)

## [1.12.1] - 2026-09-27

### Correções

- preserve localized billing availability messages (`5e14554`)
- hide payment integration errors from merchants (`0fe6a80`)

## [1.12.0] - 2026-09-27

### Features

- manage Efi plan IDs from payment catalog (`676a025`)

## [1.11.1] - 2026-09-27

### Correções

- enable Paddle catalog setup for Brazil (`c11f549`)

## [1.11.0] - 2026-09-27

### Features

- save payment catalog prices without page reload (`f0e3d07`)

### Correções

- allow retry after Paddle checkout setup error (`460a175`)

## [1.10.3] - 2026-09-27

### Correções

- preserve Paddle catalog validation feedback (`bda70e5`)

### Testes

- mark Paddle catalog fixture as validated (`ccd4d59`)

## [1.10.2] - 2026-09-27

_Sem commits com mudança de produto neste range._

## [1.10.1] - 2026-09-27

### Manutenção

- sandbox (`eafc284`)

## [1.10.0] - 2026-09-27

### Features

- add Paddle multigateway routing (`3401a2a`)

### Manutenção

- snadbox (`7f9d45b`)
- paddle (`095e80b`)
- adicionando passo passo (`80c3191`)
- DOCS (`6b89a99`)

## [1.9.2] - 2026-09-25

### Correções

- canal de suporte (`e270cfc`)

## [1.9.1] - 2026-09-25

### Manutenção

- Removendo tag de pagamento efi do texto (`7c46462`)

## [1.9.0] - 2026-09-25

### Features

- adiciona site institucional e visão operacional (`d6b48bb`)

## [1.8.2] - 2026-09-25

### Correções

- expira tentativas pendentes da Efi (`8173675`)

## [1.8.1] - 2026-09-25

_Sem commits com mudança de produto neste range._

## [1.8.0] - 2026-09-25

### Features

- notify confirmed payments and confirm subscription cancellation (`d912b72`)

### Correções

- reconcile pending payments safely and format BRL prices (`6134b83`)

## [1.7.4] - 2026-09-25

### Correções

- reconcile pending payments safely and format BRL prices (`6134b83`)

## [1.7.3] - 2026-09-25

### Correções

- corrige conciliação Efí e orienta primeiro uso (`7c9662f`)

## [1.7.1] - 2026-09-25

### Correções

- conciliar resposta de pagamento da assinatura Efi (`880c16f`)

## [1.7.0] - 2026-09-25

### Features

- versionar CSS e refinar painel e checkout (`78fbef9`)

## [1.6.0] - 2026-09-25

### Features

- Melhoria visual 2.0 (`448beb7`)

### Correções

- Pagamento Via Efi webhook (`811980a`)

## [1.5.3] - 2026-09-24

### Correções

- Pagamento Via Efi layout (`e2e609b`)

## [1.5.2] - 2026-09-24

### Correções

- Pagamento Via Efi layout (`48623c3`)

## [1.5.1] - 2026-09-24

### Correções

- Pagamento Via Efi (`b1973f8`)

## [1.5.0] - 2026-09-24

### Features

- Pagamento Via Efi (`47fffa5`)

## [1.4.1] - 2026-09-23

### Correções

- cancel pending Mercado Pago checkout (`3db0e60`)

## [1.4.0] - 2026-09-23

### Features

- create Mercado Pago checkout without payer email (`f3d2612`)

## [1.3.3] - 2026-09-23

### Correções

- clarify Mercado Pago payer email (`9a15903`)

## [1.3.2] - 2026-09-23

### Correções

- use merchant Mercado Pago email for checkout (`23066e8`)

## [1.3.1] - 2026-09-23

### Correções

- render payment subscription without error (`d6b7b2e`)

## [1.3.0] - 2026-09-23

### Features

- Pagamento Via Mercado Pago (`437bb19`)

## [1.2.0] - 2026-09-23

### Features

- RequestFilter UUID (`d744674`)

## [1.1.0] - 2026-09-23

### Features

- gerador de versao (`fddd66a`)

### Correções

- ajustando o botao de upgrade plano (`2790383`)
