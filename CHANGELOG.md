# Changelog

Gerado por `scripts/changelog.sh` a partir das mensagens de commit.
A versão vem do `pom.xml` e é a mesma da tag git e da imagem Docker.

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
