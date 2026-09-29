# Changelog

Gerado por `scripts/changelog.sh` a partir das mensagens de commit.
A versão vem do `pom.xml` e é a mesma da tag git e da imagem Docker.

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
