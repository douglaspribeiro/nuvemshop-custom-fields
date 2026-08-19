# Nuvemshop Custom Fields

Aplicacao Spring Boot para lojistas Nuvemshop/Tiendanube criarem campos personalizados por produto e coletarem esses dados diretamente no carrinho/pedido. O caso principal e atender lojas de produtos personalizados, como camisetas com nome e numero, canecas com mensagem, convites com data, brindes corporativos e itens gravados.

O produto resolve uma lacuna da plataforma: a Nuvemshop nao oferece um fluxo nativo robusto de campos personalizados por produto. O app instala via OAuth, registra um script na vitrine, injeta os campos na pagina do produto e envia os valores usando `properties[...]`, para que a informacao acompanhe o item no carrinho e no pedido.

## Fontes do Produto

As definicoes de produto e arquitetura estao mantidas no roadmap do portfolio:

- `/home/dribeiro/meudev/work-p/roadmap/produtos/nuvem-custom-fields/overview.md`
- `/home/dribeiro/meudev/work-p/roadmap/produtos/nuvem-custom-fields/arquitetura.md`
- `/home/dribeiro/meudev/work-p/roadmap/produtos/nuvem-custom-fields/roadmap.md`
- `docs/nuvemshop-homologacao/` contem os artefatos separados para homologacao e publicacao Nuvemshop: diagrama/escopos, roteiro de video, assinatura de planos pagos, FAQs/guia de instalacao e checklist do perfil do app.

## Stack

- Java 25
- Spring Boot 3.5
- Maven
- Spring MVC + Thymeleaf
- Spring Data JPA
- Flyway
- MySQL 8 em runtime
- H2 para testes

## Funcionalidades Implementadas

- Instalacao OAuth multi-tenant com isolamento por `store_id`.
- Persistencia de lojas, tokens, regras de personalizacao e campos.
- Painel do lojista em `/admin`.
- Editor de campos por produto em `/admin/products` e `/admin/products/{productId}/fields`.
- Tipos de campo: `TEXT`, `NUMBER`, `SELECT` e `TEXTAREA`.
- Validacoes por campo: obrigatorio, tamanho maximo, placeholder, regex/mascara e opcoes para select.
- Templates por nicho em `/admin/onboarding`.
- Registro dos scripts de vitrine e checkout via Scripts API.
- Asset publico `/assets/nuvemshop-personalizer.js` (script legado de vitrine, sem SDK).
- Scripts NubeSDK compilados em `src/main/frontend` (TypeScript + tsup).
- Endpoint publico `/public/stores/{storeId}/personalization`.
- Captura de valores no pedido via `properties[...]`.
- Planos e limites internos: `FREE`, `PREMIUM` e `PREMIUM_PLUS`.
- Dashboard do lojista em `/admin/dashboard`.
- Logs operacionais e pagina de ajuda em `/admin/help`.
- Webhook `/webhooks/nuvemshop` com validacao HMAC para `app/uninstalled` e `product/deleted`.
- Backoffice interno em `/backoffice`, com login proprio, lojas, flags, override de plano e relatorios.

Ainda dependem de definicao externa ou homologacao: Billing recorrente via Billing API, preco adicional por campo/opcao e publicacao na App Store da Nuvemshop.

## Arquitetura

Fluxo principal:

```text
Nuvemshop
  -> OAuth de instalacao
  -> App Spring Boot
  -> MySQL 8
  -> Scripts API registra JS na vitrine
  -> JS injeta campos na pagina de produto
  -> Cliente envia item com properties[label]
  -> Pedido recebe os dados de personalizacao
```

Principais camadas do codigo:

- `controller`: rotas web, endpoints publicos, OAuth, webhooks e backoffice.
- `service`: regras de negocio, integracao com API Nuvemshop, limites de plano, templates, logs e relatorios.
- `entity`: modelo JPA multi-tenant.
- `repository`: persistencia Spring Data.
- `dto`: formularios e respostas usadas pelo admin, storefront e relatorios.
- `resources/templates`: telas Thymeleaf do admin e backoffice.
- `resources/static/assets`: script de storefront.
- `resources/db/migration`: migrations Flyway para MySQL.

## Rotas Principais

| Rota | Uso |
| --- | --- |
| `GET /install` | Inicia instalacao OAuth na Nuvemshop. |
| `GET /oauth/callback` | Troca `code` por token, salva a loja e registra scripts/webhooks. |
| `GET /admin` | Home do painel do lojista. |
| `GET /admin/products` | Lista produtos e regras configuradas. |
| `GET /admin/products/{productId}/fields` | Editor de campos do produto. |
| `GET /admin/onboarding` | Templates de nicho para ativacao rapida. |
| `GET /admin/dashboard` | Uso e pedidos recentes com personalizacao. |
| `GET /admin/help` | Logs recentes e apoio operacional. |
| `GET /public/stores/{storeId}/personalization` | Configuracao consumida pelo script da vitrine. |
| `POST /webhooks/nuvemshop` | Webhooks oficiais da Nuvemshop. |
| `POST /hook/store/redact` | Exclui definitivamente os dados da loja apos validar o HMAC. |
| `POST /hook/customer/redact` | Registra a solicitacao sem copiar dados pessoais do payload; o app nao persiste dados de compradores. |
| `POST /hook/customer/data` | Registra a solicitacao sem copiar dados pessoais do payload; o app nao persiste dados de compradores. |
| `GET /backoffice` | Painel interno do operador. |

## Planos e Limites

| Plano | Produtos personalizados | Campos por produto |
| --- | ---: | ---: |
| `FREE` | 1 | 1 |
| `PREMIUM` | 10 | 3 |
| `PREMIUM_PLUS` | ilimitado | ilimitado |

O enforcement fica em `PlanLimitService` e e aplicado no editor do admin e no endpoint publico do storefront.

## Configuracao

A configuracao padrao fica em `src/main/resources/application.yml`. As principais variaveis de ambiente sao:

| Variavel | Padrao | Descricao |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/nuvem_custom_fields?...` | URL JDBC do MySQL. |
| `DB_USERNAME` | `root` | Usuario do banco. |
| `DB_PASSWORD` | vazio | Senha do banco. |
| `NUVEMSHOP_CLIENT_ID` | `change-me` | Client ID do app no portal Nuvemshop. |
| `NUVEMSHOP_CLIENT_SECRET` | `change-me` | Client secret do app. |
| `NUVEMSHOP_REDIRECT_URI` | `http://localhost:8080/oauth/callback` | Callback OAuth cadastrado. |
| `NUVEMSHOP_AUTH_URL` | URL oficial Tiendanube | Endpoint de autorizacao. |
| `NUVEMSHOP_TOKEN_URL` | URL oficial Tiendanube | Endpoint de token. |
| `NUVEMSHOP_API_BASE_URL` | `https://api.tiendanube.com` | Base URL da API. |
| `APP_BASE_URL` | `http://localhost:8080` | URL publica usada em scripts e webhooks. |
| `NUVEMSHOP_SCOPES` | `read_products,read_orders,write_scripts,read_scripts,billing,read_store` | Scopes OAuth solicitados. |
| `NUVEMSHOP_USER_AGENT` | `NuvemCustomFields suporte@example.com` | User-Agent exigido pela API. |
| `NUVEMSHOP_BILLING_ENABLED` | `false` | Ativa a assinatura automatica quando toda a configuracao de billing estiver pronta. |
| `NUVEMSHOP_BILLING_CONCEPT_CODE` | `app-cost` | Codigo do conceito usado pela assinatura recorrente de aplicativos. |
| `BACKOFFICE_USERNAME` | `admin` | Usuario do backoffice. |
| `BACKOFFICE_PASSWORD` | `admin` | Senha do backoffice. |

Para testar o OAuth localmente, `APP_BASE_URL` e `NUVEMSHOP_REDIRECT_URI` precisam apontar para uma URL acessivel pela Nuvemshop, normalmente via tunnel HTTPS.

## Rodando Localmente

Requisitos:

- Java 25 instalado.
- Maven disponivel.
- MySQL 8 rodando e acessivel.
- Credenciais de app Nuvemshop para testar OAuth e API real.

Com MySQL local e variaveis configuradas:

```bash
mvn spring-boot:run
```

Com variaveis inline:

```bash
DB_URL='jdbc:mysql://localhost:3306/nuvem_custom_fields?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
DB_USERNAME=root \
DB_PASSWORD='' \
NUVEMSHOP_CLIENT_ID='seu-client-id' \
NUVEMSHOP_CLIENT_SECRET='seu-client-secret' \
APP_BASE_URL='https://sua-url-publica' \
NUVEMSHOP_REDIRECT_URI='https://sua-url-publica/oauth/callback' \
mvn spring-boot:run
```

Depois de subir a aplicacao:

- `http://localhost:8080/install` inicia o fluxo de instalacao.
- `http://localhost:8080/backoffice/login` abre o backoffice interno.

### Docker local com ngrok

O launcher local configura automaticamente:

- `APP_BASE_URL=https://chlorine-mutate-preface.ngrok-free.dev`
- `NUVEMSHOP_REDIRECT_URI=https://chlorine-mutate-preface.ngrok-free.dev/oauth/callback`

Assim, scripts, webhooks e o callback OAuth usam o DNS publico do tunnel:

```bash
./scripts/start-local-docker-and-spring.sh
```

Quando o dominio do ngrok mudar, informe a nova origem HTTPS:

```bash
./scripts/start-local-docker-and-spring.sh \
  --app-base-url https://novo-dominio.ngrok-free.dev
```

## Testes

Os testes usam H2 em memoria com configuracao em `src/test/resources/application.yml`.

```bash
mvn test
```

Testes existentes cobrem contexto Spring, limites de plano, OAuth e seguranca de webhook.

## Banco de Dados

As migrations Flyway ficam em `src/main/resources/db/migration` e criam as tabelas principais:

- `stores`
- `personalization_rules`
- `personalization_fields`
- `integration_logs`
- `plan_events`
- `feature_flags`

O Hibernate roda com `ddl-auto: validate`, entao o schema deve ser criado/atualizado pelas migrations.

## Storefront

O app e **hibrido**, por exigencia da homologacao Nuvemshop: o script legado de DOM e o
script NubeSDK coexistem, para nao quebrar lojas com temas nao migrados.

**Script legado** `nuvemshop-personalizer.js`: le o `store` na query string do proprio script,
detecta o formulario de produto, busca os campos em `/public/stores/{storeId}/personalization`
e injeta inputs com `name="properties[...]"` no form nativo.

**Script NubeSDK**, compilado de `src/main/frontend/src/storefront/main.tsx`:

1. Le `store.id` e o produto do state do SDK.
2. Busca os campos em `/public/stores/{storeId}/personalization`.
3. Renderiza os campos no slot `before_product_detail_add_to_cart`.
4. Intercepta a adicao nativa ao carrinho (`cart:before_update`), cancela e reemite via
   `cart:add` com `properties`, que e o unico caminho para a personalizacao chegar ao pedido.

O script e registrado na loja apos a instalacao OAuth, usando a Scripts API. Detalhes,
pre-requisitos e armadilhas em `src/main/frontend/README.md` — em especial: **a loja precisa
estar liberada pela Nuvemshop para receber o runtime do NubeSDK**, e a falha e silenciosa.

Diagnostico: `/backoffice/stores/{storeId}/scripts` mostra o que esta associado na loja, com
status, versao e o que falta; e `scripts/check-nubesdk-storefront.mjs <url>` inspeciona a loja
ao vivo.

## Operacao

O backoffice interno permite acompanhar lojas instaladas, status, eventos de plano, logs recentes, feature flags e relatorios gerenciais. As credenciais sao configuradas por `BACKOFFICE_USERNAME` e `BACKOFFICE_PASSWORD`.

Webhooks registrados:

- `app/uninstalled`: marca a loja como desinstalada, apaga token e escopos, limpa a assinatura local e volta o plano para `FREE`. Scripts e webhooks do app sao removidos automaticamente pela Nuvemshop.
- `store/redact`: exclui de forma idempotente loja, configuracoes, campos, logs, eventos de plano e chamados vinculados.
- `product/deleted`: remove as regras de personalizacao do produto removido.

## Status do Produto

O MVP funcional de campos personalizados esta implementado. Os principais pontos em aberto no roadmap sao:

- Billing recorrente oficial via Nuvemshop, pendente de IDs/conceitos oficiais dos planos.
- Preco adicional por campo/opcao, pendente de decisao tecnica/comercial sobre como refletir valor no total do pedido.
- Homologacao e publicacao na App Store da Nuvemshop.
