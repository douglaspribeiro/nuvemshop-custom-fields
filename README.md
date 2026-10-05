# Nuvemshop Custom Fields

Aplicacao Spring Boot para lojistas Nuvemshop/Tiendanube criarem campos personalizados por produto e coletarem esses dados diretamente no carrinho/pedido. O caso principal e atender lojas de produtos personalizados, como camisetas com nome e numero, canecas com mensagem, convites com data, brindes corporativos e itens gravados.

O produto resolve uma lacuna da plataforma: a Nuvemshop nao oferece um fluxo nativo robusto de campos personalizados por produto. O app instala via OAuth, registra um script na vitrine, injeta os campos na pagina do produto e envia os valores usando `properties[...]`, para que a informacao acompanhe o item no carrinho e no pedido.

## Fontes do Produto

As definicoes de produto e arquitetura estao mantidas no roadmap do portfolio:

- `/home/dribeiro/meudev/work-p/roadmap/produtos/nuvem-custom-fields/overview.md`
- `/home/dribeiro/meudev/work-p/roadmap/produtos/nuvem-custom-fields/arquitetura.md`
- `/home/dribeiro/meudev/work-p/roadmap/produtos/nuvem-custom-fields/roadmap.md`
- `docs/nuvemshop-homologacao/` contem os artefatos separados para homologacao e publicacao Nuvemshop: diagrama/escopos, roteiro de video, assinatura de planos pagos, FAQs/guia de instalacao e checklist do perfil do app.
- `docs/pagamentos-v1-e-roadmap.md` descreve Mercado Pago para lojas BR e a arquitetura preparada para um segundo gateway internacional, inicialmente Paddle.
- `docs/plano-paddle-multigateway.md` detalha a implementação da Paddle, o roteamento configurável por país e a convivência entre assinaturas de providers diferentes.

## Stack

- Java 25
- Spring Boot 3.5
- Maven
- Spring MVC + Thymeleaf
- Spring Data JPA
- Flyway
- MySQL 8 em runtime
- H2 para testes

## E-mail de resposta do suporte (AWS SES)

Ao responder um chamado pelo backoffice, o aplicativo envia a resposta para o
`storeEmail` da loja após confirmar a transação no banco. Configure as propriedades
`notifications.ses` por estas variáveis de ambiente:

```dotenv
AWS_SES_SMTP_HOST=email-smtp.us-east-1.amazonaws.com
AWS_SES_SMTP_PORT=587
AWS_SES_SMTP_USERNAME=
AWS_SES_SMTP_PASSWORD=
AWS_SES_FROM_EMAIL=suporte@seu-dominio.com.br
APP_BASE_URL=https://seu-app.example.com
```

Use o endpoint e as credenciais **SMTP** da mesma região do SES (não as chaves de
acesso da API AWS). O remetente precisa estar verificado no SES; enquanto a conta
estiver no sandbox, os destinatários também precisam estar verificados.
O transporte exige STARTTLS; a porta padrão é 587.
Consulte a [documentação do SES](https://docs.aws.amazon.com/ses/latest/dg/send-email-smtp.html).

Sem host, usuário, senha ou remetente, nenhum e-mail é enviado e cada resposta gera
o log `support.email_provider_not_configured` com “Provedor AWS SES não configurado”.
Loja sem e-mail gera `support.email_recipient_missing`; falha de envio gera
`support.email_failed`, sem desfazer a resposta salva. Não há retentativa automática.
O e-mail contém a resposta e o link do chamado, que exige acesso pelo aplicativo.

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
- Assinaturas Mercado Pago para lojas brasileiras, com checkout hospedado, webhook assinado e reconciliacao.

Ainda dependem de homologacao: credenciais e prova real do Mercado Pago, segundo gateway para lojas internacionais, preco adicional por campo/opcao e publicacao na App Store da Nuvemshop.

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
| `POST /prod/webhooks/mercado-pago2` | Webhooks assinados de assinaturas Mercado Pago. |
| `POST /hook/store/redact` | Valida HMAC, registra recebimento e marca exclusao pendente, bloqueando acesso e contatos automaticos. Contato manual exige confirmacao por loja; exclusao definitiva exige revisao manual no backoffice. |
| `POST /hook/customer/redact` | Registra a solicitacao sem copiar dados pessoais do payload; o app nao persiste dados de compradores. |
| `POST /hook/customer/data` | Registra a solicitacao sem copiar dados pessoais do payload; o app nao persiste dados de compradores. |
| `GET /backoffice` | Painel interno do operador. |

## Planos e Limites

| Plano | Produtos personalizados | Campos por produto |
| --- | ---: | ---: |
| `FREE` | 1 | 1 |
| `PREMIUM` | 10 | 3 |
| `PREMIUM_PLUS` | 50 | ilimitado |
| `PREMIUM_ULTRA` | ilimitado | ilimitado |

O enforcement fica em `PlanLimitService` e e aplicado no editor do admin e no endpoint publico do storefront.

O Ultra custa R$ 59,90/mês no Brasil e inclui atendimento prioritário. Os chamados
registram o plano efetivo no momento da abertura; chamados Ultra recebem destaque
no Discord e na fila do backoffice, com prioridade entre os chamados abertos.

O upgrade mantém a assinatura no mesmo gateway e ambiente. Na Efí, a tela calcula
o valor do novo plano pelo restante do ciclo pago, desconta o crédito do plano
atual e aceita cupons cadastrados no backoffice (desconto somente no restante deste ciclo).
Uma cobrança avulsa paga o ajuste, sem criar outra assinatura. Só após confirmar
pagamento e alteração na Efí os recursos superiores são liberados. A próxima
mensalidade integral continua na data existente. Paddle e Mercado Pago mantêm
o fluxo anterior sem ajuste imediato. A Efí brasileira usa
`updateSubscription` (`PUT /v1/subscription/:id`), não o endpoint `change-plan/direct`
do Efipay colombiano. Alterações com resposta incerta ficam pendentes e são
conciliadas pela consulta da assinatura e pelos webhooks.

As migrations V38, V39 e V40 criam o catálogo Ultra, o plano dos chamados, o estado
de upgrade e o histórico de ajustes financeiros. Os preços Ultra do catálogo começam desabilitados: informe os IDs remotos
e valide no Backoffice > Pagamentos antes de vender o plano. Preços internacionais
Ultra são valores iniciais configuráveis; não alteram contratos existentes.

## Reconquista apos desinstalacao

**Estado atual:** a publicacao SQS esta implementada com outbox transacional,
atraso de 15 minutos e novas tentativas. A lista de campanhas, os eventos SES,
o consumidor do primeiro e-mail e o formulario de motivos estao implementados.
Os e-mails de oferta por motivo, o cupom da primeira mensalidade na Efi,
a restauracao do preco integral e a conversao por pagamento confirmado estao
implementados localmente, com validacao real de homologacao ainda pendente.
`WINBACK_ENABLED`, `WINBACK_MAIL_ENABLED` e `AWS_SES_EVENTS_ENABLED` permanecem `false`
ate a configuracao de acesso e a validacao dos disparos. `WINBACK_DISCOUNT_ENABLED`
e `WINBACK_DISCOUNT_ALLOW_PRODUCTION` tambem permanecem `false`.

A fila configurada e `eventosDesistalacao`, em `us-east-2`:
`https://sqs.us-east-2.amazonaws.com/265105089924/eventosDesistalacao`.
O acesso usa a cadeia padrao de credenciais do SDK AWS (role ou
`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`, com `AWS_SESSION_TOKEN` quando aplicavel).
As credenciais SMTP do SES nao autenticam chamadas SQS.
Veja [configuracao da fila e historico de saidas](docs/desinstalacoes-e-sqs.md).

O evento `app/uninstalled` deve permanecer curto e confiavel: apos validar o HMAC, o
aplicativo registra a desinstalacao e publica uma mensagem idempotente no Amazon SQS. A
mensagem usa o maior atraso nativo permitido pelo SQS, **15 minutos** (`DelaySeconds=900`).
O webhook nao gera cupom nem envia e-mail diretamente.

Depois do atraso, o consumidor verifica se a mesma `store_id` continua desinstalada. Se a
loja tiver reinstalado, a mensagem e descartada. Caso contrario, o consumidor registra a
campanha no banco e envia o primeiro formulario de motivos pelo Amazon SES.
A resposta prepara o seguimento e gera ou recupera o cupom elegivel quando o desconto
esta habilitado. SMTP sem confirmacao fica para conciliacao pelos eventos SES e
conferencia operacional; nao ha reenvio automatico de mensagens de aceite incerto.

O backoffice deve expor uma lista de desinstalacoes e o funil completo: desinstalacao,
motivo, resposta, cupom, tentativas e entrega de e-mail, abertura detectada, clique,
reinstalacao, abertura de checkout e conversao em pagamento. Entrega, abertura e clique
serao correlacionados pela fila de eventos SES; abertura nao comprova leitura humana,
e clique nao equivale a uso de cupom. O cupom so sera considerado usado apos pagamento
confirmado. Veja o fluxo e a ativacao em [historico e eventos](docs/desinstalacoes-e-sqs.md).
Os motivos e regras
iniciais sao:

| Motivo informado | Acao apos 15 minutos |
| --- | --- |
| Preco | E-mail de reconquista com cupom de 50% no primeiro mes. |
| Dificuldade de configuracao | Pergunta qual etapa foi dificil; o cupom de 50% e enviado apos a resposta. |
| Nao preciso mais | E-mail de retorno com cupom de 50% no primeiro mes. |
| Outro motivo | Pergunta breve e cupom de 50% no primeiro mes. |
| Nao encontrei a funcionalidade que preciso | E-mail com formulario curto, de um campo de texto. Depois da resposta, cupom de 50% valido por 30 dias e mensagem de que a necessidade sera avaliada. |

As solicitacoes de funcionalidade devem ser acompanhadas no backoffice com os status `NOVA`,
`EM_ANALISE`, `PLANEJADA`, `ENTREGUE` e `NAO_PREVISTA`. Quando uma funcionalidade for
entregue, as lojas que a solicitaram poderao receber uma comunicacao especifica.

Para evitar abuso, o cupom automatico de 50% pertence a uma unica `store_id`, so e aplicavel
apos a reinstalacao da mesma loja, expira conforme a campanha e e marcado como usado somente
apos a confirmacao da primeira cobranca pela Efí. Reinstalacoes posteriores nao geram novo
cupom. Lojas que ja tiveram uma assinatura paga seguem uma campanha de reconquista separada,
sem cupom automatico; qualquer incentivo adicional depende de resposta ou aprovacao manual
no backoffice.

Os e-mails devem usar templates HTML responsivos e especificos por motivo, com marca,
beneficio em destaque, um unico CTA, suporte/privacidade e opcao de nao receber novas
comunicacoes. A condicao material da oferta deve aparecer claramente: o desconto vale apenas
para o primeiro mes.

### Precos internacionais

A tabela abaixo e a referencia comercial inicial dos planos pagos. Os valores sao
precos locais fixos, e nao conversoes exibidas em tempo real. A Argentina deve ser revisada
periodicamente devido a volatilidade do ARS.

| Pais | Moeda | Essencial / mes | Pro / mes |
| --- | --- | ---: | ---: |
| Brasil | BRL | R$ 19,99 | R$ 29,99 |
| Estados Unidos | USD | US$ 4,99 | US$ 7,49 |
| Mexico | MXN | MX$ 99 | MX$ 149 |
| Chile | CLP | CLP$ 4.199 | CLP$ 6.299 |
| Argentina | ARS | ARS$ 5.599 | ARS$ 8.399 |

Esses valores foram registrados em `nuvemshop.billing.prices`. A cobranca por Efí permanece
restrita ao Brasil; antes de habilitar cobranca recorrente nos demais paises, e necessario
integrar um provedor que aceite a moeda e os meios de pagamento locais.

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
| `GA4_MEASUREMENT_ID` | `G-RM8VWNPDT8` | ID de medição do Google Analytics 4; deixe vazio para desativar. Mede as telas administrativas, sem parâmetros da URL nem dados do formulário de pagamento. |
| `GA4_API_SECRET` | vazio | Segredo do Measurement Protocol, somente no backend, para compras e upgrades confirmados. Configuração e relatórios em [GA4_COMPRA_UPGRADE.md](docs/GA4_COMPRA_UPGRADE.md). |
| `LEGAL_OPERATOR_NAME` | vazio | Nome completo da pessoa responsável pelo serviço, exibido nas páginas públicas. |
| `LEGAL_DOCUMENT` | vazio | Documento opcional do responsável; não é necessário exibir CPF publicamente. |
| `SUPPORT_EMAIL` | `contato@wzhub.com.br` | E-mail público de suporte e contato. |
| `NUVEMSHOP_SCOPES` | `read_products,read_orders,write_scripts,read_scripts,billing,read_store` | Scopes OAuth solicitados. |
| `NUVEMSHOP_USER_AGENT` | `NuvemCustomFields suporte@example.com` | User-Agent exigido pela API. |
| `NUVEMSHOP_BILLING_ENABLED` | `false` | Ativa a assinatura automatica quando toda a configuracao de billing estiver pronta. |
| `NUVEMSHOP_BILLING_CONCEPT_CODE` | `app-cost` | Codigo do conceito usado pela assinatura recorrente de aplicativos. |
| `MERCADO_PAGO_ENABLED` | `false` | Habilita checkout para lojas BR quando todas as credenciais estiverem presentes. |
| `MERCADO_PAGO_ACCESS_TOKEN` | vazio | Access token privado da aplicacao Mercado Pago. |
| `MERCADO_PAGO_WEBHOOK_SECRET` | vazio | Segredo usado para validar notificacoes Mercado Pago. |
| `MERCADO_PAGO_PREMIUM_AMOUNT` | `19.99` | Mensalidade Essencial em BRL. |
| `MERCADO_PAGO_PREMIUM_PLUS_AMOUNT` | `29.99` | Mensalidade Pro em BRL. |
| `DISCORD_PAYMENT_WEBHOOK_URL` | vazio | URL privada do webhook que recebe avisos de pagamentos aprovados; nunca versionar o token. |
| `DISCORD_SUPPORT_WEBHOOK_URL` | usa o webhook de pagamentos | URL opcional para avisos de novos chamados e respostas de lojas. |
| `BACKOFFICE_USERNAME` | `admin` | Usuario do backoffice. |
| `BACKOFFICE_PASSWORD` | `admin` | Senha do backoffice. |

Para testar o OAuth localmente, `APP_BASE_URL` e `NUVEMSHOP_REDIRECT_URI` precisam apontar para uma URL acessivel pela Nuvemshop, normalmente via tunnel HTTPS.

Quando `DISCORD_PAYMENT_WEBHOOK_URL` estiver configurada, cada cobranca aprovada gera um aviso no Discord. O envio ocorre em segundo plano, com retentativas; atualizacoes posteriores da mesma cobranca nao geram outro aviso. Cobranças pendentes ou recusadas nao sao anunciadas.

`DISCORD_SUPPORT_WEBHOOK_URL` é opcional. Sem ela, os avisos de novo chamado e de resposta da loja usam o mesmo webhook de pagamentos. Os avisos não incluem o conteúdo da mensagem do cliente.

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

### Docker local e homologação sem ngrok

Para testar sem OAuth/painel da Nuvemshop, consulte
[Homologação local: loja fictícia, Efí sandbox e sugestões](docs/homologacao-local.md).
O launcher aceita `--homolog-local`, com banco isolado e entrada protegida em
`/local/homologacao`, inexistente no perfil normal de produção.
Com `--homolog-local --infisical`, o launcher carrega as variáveis do Infisical
(`stage`, pasta `/app_custom-fields`) antes de preparar o MySQL. Requer CLI
instalado/autenticado. A homologação usa `http://localhost:8080` por padrão, sem ngrok. Veja o guia
acima para a chave de entrada e as credenciais sandbox.

Para abrir o painel com loja fictícia local, use:

```bash
./scripts/start-local-docker-and-spring.sh --homolog-local --infisical -- -DskipTests
```

O túnel HTTPS é opcional para receber callbacks externos ou testar OAuth real.
No modo normal (sem `--homolog-local`), o launcher configura automaticamente:

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

## Idiomas

O app fala pt-BR e espanhol neutro. O idioma **nao** vem do navegador do lojista: vem do
pais da loja (`country` de `GET /store`, persistido em `store_country_code`). `BR`/`PT` ->
pt-BR, qualquer outro pais Latam -> es.

| Camada | Onde | Como resolve |
| --- | --- | --- |
| Telas do lojista | `messages.properties` / `messages_es.properties` | `AppLocaleResolver` le `appLocale` da sessao, semeado pelo `AdminSessionInterceptor` |
| Flash e excecoes | `i18n/Messages` | mesmo locale, via `LocaleContextHolder` |
| Paginas publicas | idem | sem loja em sessao: `Accept-Language`; idioma sem traducao cai em pt-BR |
| Vitrine e checkout (comprador) | `frontend/src/shared/i18n.ts` e `nuvemshop-checkout.js` | `locale` vem no payload de `/personalization` e `/style` — o script roda em Web Worker e nao tem DOM para inspecionar |

O backoffice interno fica em pt-BR de proposito: nao e acessivel ao lojista.

### Rastro do idioma

Para responder "por que a loja X viu portugues?" depois do fato:

| Evento no log | Quando | Diz |
| --- | --- | --- |
| `nuvemshop.api.get_store.done` | toda leitura de `GET /store` | `country` e `currency` crus da API |
| `nuvemshop.oauth.store_locale` | instalacao/reconexao | pais, moeda, idioma escolhido, `profile_loaded`, `source=get_store\|fallback` |
| `nuvemshop.billing.store_locale.updated` | pais preenchido depois da instalacao | idioma novo (pode ter mudado) |
| `admin.session.locale.applied` | 1a request admin da sessao | idioma aplicado e a URI |
| `public.personalization.enabled` / `public.style.enabled` | request do script do comprador | idioma entregue a vitrine/checkout |

Log de servidor rotaciona, entao a decisao tambem vai para `integration_logs` como
`store.locale.resolved` — consultavel por loja em `/admin/help` e no backoffice, com o texto
explicito de qual pais gerou qual idioma, ou de que o idioma veio do padrao por falta de pais.

**Armadilha:** chave ausente em `messages_es.properties` **nao** renderiza `??chave??`. O
Spring cai no bundle padrao e entrega portugues em silencio. Por isso existem dois testes:
`MessageBundleParityTest` (paridade de chaves) e `LocalizedPagesRenderTest`, que renderiza
cada tela em `AR/MX/CL/CO` e falha se qualquer texto pt traduzido aparecer no HTML.

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

O painel soma unidades de pedidos pagos das lojas ativas: todas as unidades e as que possuem
`products[].properties` de personalizacao. A sincronizacao historica consulta a API de pedidos
em segundo plano, uma loja por vez, e armazena apenas IDs de pedido e contagens, sem dados do
comprador. O painel mostra a cobertura da sincronizacao e marca os totais como parciais enquanto
faltarem lojas; lojas sem `read_orders` nao entram ate recuperarem esse escopo. Por padrao, a
primeira sincronizacao comeca apos 2 minutos e os ciclos seguintes ocorrem a cada 2 minutos;
`analytics.sales-sync-initial-delay-ms` e `analytics.sales-sync-delay-ms` permitem ajustar isso.

Webhooks registrados:

- `app/uninstalled`: marca a loja como desinstalada, apaga token e escopos, revoga o acesso local e volta o plano para `FREE`, sem solicitar cancelamento da assinatura no gateway. Scripts e webhooks do app sao removidos automaticamente pela Nuvemshop.
- `store/redact`: registra `erasure_requested_at`, preservando a primeira data em retentativas, e bloqueia acesso/contatos automaticos. O backoffice permite solicitar feedback manual mediante confirmacao explicita para a loja. Nesta versao, a exclusao definitiva ocorre manualmente, sem agendamento automatico. Consulte [exclusoes pendentes](docs/exclusoes-pendentes.md) para limites operacionais e a revisao de conformidade necessaria.
- `product/deleted`: remove as regras de personalizacao do produto removido.

## Status do Produto

O MVP funcional de campos personalizados esta implementado. Os principais pontos em aberto no roadmap sao:

- Assinaturas via Stripe em todos os mercados, conforme a especificacao da V1; implementacao pendente.
- Preco adicional por campo/opcao, pendente de decisao tecnica/comercial sobre como refletir valor no total do pedido.
- Homologacao e publicacao na App Store da Nuvemshop.

### Cortesia Premium por 30 dias

Enquanto a modalidade de cobrança é regularizada com a Nuvemshop, os botões
de upgrade exibem “Em breve” (ou “Próximamente” em espanhol) ao redirecionar
para o painel. As rotas de contratação continuam bloqueadas, sem chamar a Billing API.

Em **Backoffice > Lojas > Detalhe > Plano gratuito por 30 dias**, escolha
**Conceder Essencial por 30 dias** ou **Conceder Pro por 30 dias**. A concessão exige loja ativa no plano gratuito,
sem assinatura e sem cortesia ativa. O início e o término aparecem no horário de
Brasília e a concessão é registrada na auditoria de planos.

O benefício libera os limites do plano escolhido por 30 dias corridos, sem cobrança nem
renovação automática. O plano base permanece gratuito e volta a determinar os
limites assim que o prazo termina, sem depender de um job ou de acesso ao painel.
Os dados cadastrados permanecem salvos. Salvar um override de plano encerra o
bônus ativo. A funcionalidade não envia e-mail automaticamente.

As migrations V15 e V16 adicionam as datas e o plano do benefício; devem ser
aplicadas pelo Flyway durante a atualização da aplicação. Ao entrar no painel, o
lojista vê o plano temporário, os dias restantes e a data de término.
Enquanto o benefício estiver ativo, o backoffice permite trocar entre Essencial
e Pro sem reiniciar o prazo; a mudança mantém o término original e gera um evento
de auditoria.

### Publicação e versão automática

O fluxo de release segue o wzrank: testes antes do versionamento, atualização
do `pom.xml` e `CHANGELOG.md`, commit `chore(release)`, tag Git `vX.Y.Z` e
imagem GHCR com a mesma versão, além de `latest`. Requer Java 25, Maven,
Python 3, Git e Docker Buildx; o push local exige login prévio no GHCR.

```bash
./scripts/release-version.sh --dry-run       # simula sem alterar arquivos
./scripts/push-docker.sh                     # testa, versiona e publica
./scripts/push-docker.sh --release minor      # força incremento minor
./scripts/push-docker.sh --set-version 1.2.0  # define versão exata
./scripts/push-docker.sh --no-bump            # publica a versão atual do pom
./scripts/push-docker.sh --tag teste          # tag explícita, sem alterar o pom
```

Desde a última tag, vence a mudança de maior nível: `BREAKING CHANGE` ou
`tipo!:` incrementa major; `feat:`/`add:` incrementa minor; outros prefixos
incrementam patch; commits sem prefixo incrementam um quarto componente
(`1.0.0.1`). Sem commits novos, a versão permanece igual. O changelog agrupa
as mudanças por tipo. As tags antigas `v<N>` servem como base da primeira release.

Antes do push local, commite as alterações. `--allow-dirty` permite publicar
arquivos não commitados, comprometendo a reprodução da imagem pela tag.
`--skip-precheck` pula os testes prévios. Se o build ou push Docker falhar após
o bump, o commit e a tag permanecem locais; retome o fluxo sem novos commits
para reutilizar a versão. O script só envia o branch e a tag após publicar a imagem.

O GitHub Actions compila e testa os pushes. Branches `deploy-*` também executam
o workflow de publicação ARM64: versão/changelog, imagem, tag, merge no branch
de origem e remoção do branch de deploy após o merge. A variável de repositório
`RELEASE_BASE_BRANCH` permite definir explicitamente o destino; sem ela, ele é
inferido como no wzrank. O token do workflow precisa poder escrever no repositório
e no GHCR; proteções de branch devem permitir o merge efetuado pelo workflow.
