# Pagamentos V1 e evolucao multi-gateway

## Decisao

- A V1 usa Mercado Pago Assinaturas para lojas brasileiras, em BRL e somente com cartao de credito.
- Lojas de outros paises continuam sem checkout pago nesta etapa.
- Paddle e o primeiro candidato para uma segunda integracao internacional, sujeito a aprovacao comercial e aos termos do produto.
- Nao ha migracao nem cobranca retroativa. Somente upgrades iniciados depois da ativacao do gateway criam uma assinatura.
- Stripe permanece suspensa e nao faz parte desta versao.

O Checkout Pro multi-moeda do Mercado Pago aceita cartoes internacionais e liquida em reais para uma conta brasileira. Essa divulgacao nao confirma o mesmo comportamento para a API de Assinaturas usada pelo app. Por isso, a V1 continua restrita a lojas BR ate que recorrencia, renovacao e experiencia do pagador estrangeiro sejam comprovadas em sandbox e confirmadas comercialmente.

## Fluxo implementado

1. O backend identifica pais e moeda da loja usando a Store API da Nuvemshop.
2. O roteador seleciona um adaptador configurado que suporte o mercado. Hoje, `BR/BRL` seleciona `MERCADO_PAGO`; o enum ja reserva `PADDLE`.
3. O lojista escolhe Essencial ou Pro em `/admin/billing`.
4. O backend define plano, valor e moeda, cria uma assinatura pendente em `/preapproval` e redireciona a janela principal ao checkout hospedado.
5. O retorno do navegador apenas informa que a confirmacao esta em andamento.
6. O plano pago so e ativado depois que o backend consulta a assinatura e uma fatura aprovada no Mercado Pago.
7. Webhooks assinados e a reconciliacao periodica mantem o estado local.

Cliques repetidos reutilizam o checkout pendente. A cortesia temporaria bloqueia uma nova assinatura paga. Upgrade ou downgrade de uma assinatura ja ativa fica fora do primeiro corte, evitando cobranca proporcional inesperada.

## Fonte de verdade e estados

`payment_subscriptions` armazena o contrato comercial atual da loja, sem reutilizar os campos legados `billing_*` da Nuvemshop. O registro contem gateway, IDs e status remotos, referencia externa, plano, moeda, valor, proxima cobranca, ultimo pagamento, tolerancia, cancelamento pendente, sincronizacao, erro e versao otimista.

`payment_webhook_events` e a caixa de entrada idempotente. Cada notificacao tem chave unica, tipo, recurso remoto, loja associada, numero de tentativas e estado de processamento. O corpo integral e dados de cartao nao sao persistidos.

| Estado | Acesso |
| --- | --- |
| `PENDING` | Mantem o plano anterior; nunca libera pelo redirect. |
| `ACTIVE` | Libera o plano contratado apos fatura aprovada. |
| `PAST_DUE` | Mantem durante a tolerancia configurada, inicialmente 3 dias. |
| `PAUSED` | Volta ao Free. |
| `CANCELED` | Mantem ate a proxima data ja paga, quando conhecida, e depois volta ao Free. |
| `ERROR` | Nao libera acesso e mostra o erro operacional. |

Na desinstalacao, o acesso local e revogado e o app solicita cancelamento remoto sem depender do token Nuvemshop. Falhas ficam marcadas para operacao. A exclusao LGPD remove assinatura e eventos vinculados a loja.

## Seguranca

- Access token e segredo de webhook existem somente no backend.
- O webhook valida `x-signature` por HMAC-SHA256 com `x-request-id`, `data.id` e o timestamp antes de processar o evento.
- Valores enviados pelo navegador nunca sao usados como fonte de verdade.
- A referencia externa, moeda e valor retornados pelo gateway precisam coincidir com o registro criado pelo servidor.
- A aplicacao nao recebe nem armazena numero completo do cartao ou CVC.

## Rotas

| Metodo e rota | Responsabilidade |
| --- | --- |
| `GET /admin/billing` | Exibe planos, disponibilidade e assinatura corrente. |
| `POST /admin/billing/checkout` | Cria ou reutiliza o checkout pendente. |
| `GET /admin/billing/return` | Reconcilia e informa que a confirmacao esta em andamento. |
| `POST /admin/billing/cancel` | Solicita cancelamento da assinatura. |
| `POST /prod/webhooks/mercado-pago2` | Valida e processa notificacao idempotente. |
| `POST /backoffice/stores/{storeId}/payment/reconcile` | Forca reconciliacao operacional. |

## Configuracao

| Variavel | Uso |
| --- | --- |
| `MERCADO_PAGO_ENABLED` | Libera o roteamento para Mercado Pago. |
| `MERCADO_PAGO_ACCESS_TOKEN` | Credencial privada da aplicacao. |
| `MERCADO_PAGO_WEBHOOK_SECRET` | Segredo da assinatura das notificacoes. |
| `MERCADO_PAGO_PREMIUM_AMOUNT` | Mensalidade Essencial em BRL. |
| `MERCADO_PAGO_PREMIUM_PLUS_AMOUNT` | Mensalidade Pro em BRL. |
| `MERCADO_PAGO_GRACE_DAYS` | Tolerancia apos falha de renovacao. |
| `PAYMENTS_RECONCILIATION_DELAY_MS` | Intervalo do job de reconciliacao. |

O endpoint cadastrado no Mercado Pago deve ser `https://campos-personalizados.wzhub.pro/prod/webhooks/mercado-pago2`, com os topicos de Planos e assinaturas e Pagamentos. O controller tambem aceita `/webhooks/mercado-pago2` internamente caso o proxy remova o prefixo `/prod`.

## Liberacao

Antes de habilitar em producao:

1. Confirmar que a conta e o produto de assinaturas foram aprovados.
2. Validar no sandbox que `/preapproval` aceita a restricao `payment_methods_allowed=credit_card` no fluxo sem plano associado.
3. Executar assinatura, primeira aprovacao, renovacao recusada, recuperacao, cancelamento, webhook repetido e desinstalacao.
4. Fazer uma assinatura real de baixo valor e conferir Mercado Pago, banco, logs, backoffice e limites do plano.
5. Ativar por ambiente com `MERCADO_PAGO_ENABLED=true`.

Se a restricao de meio de pagamento nao for aceita pela API de Assinaturas, a V1 nao deve ser publicada ate escolher entre um plano de assinatura compativel ou outro fluxo oficial que preserve recorrencia somente em cartao.

## Proximo gateway

O contrato `PaymentGateway` concentra criacao de checkout, consulta de assinatura e fatura, cancelamento e validacao de webhook. Controllers e regras de plano usam apenas estados internos. Para adicionar Paddle, sera necessario criar o adaptador, suas credenciais e seu endpoint de webhook, e registrar no roteador os paises e moedas aprovados.

Uma eventual ampliacao do Mercado Pago para cartoes internacionais sera um experimento separado. Aceitar um cartao emitido fora do Brasil com liquidacao em BRL nao equivale a oferecer preco local, meios locais ou uma operacao recorrente local em Argentina, Chile, Colombia e Mexico.

## Referencias

- [Mercado Pago — API de Assinaturas](https://www.mercadopago.com.br/developers/pt/reference/online-payments/subscriptions/overview)
- [Mercado Pago — assinaturas com pagamento pendente](https://www.mercadopago.com.br/developers/pt/docs/subscriptions/integration-configuration/subscription-no-associated-plan/pending-payments)
- [Mercado Pago — webhooks](https://www.mercadopago.com.br/developers/pt/docs/links-and-debts/additional-content/your-integrations/notifications/webhooks?scope=prod)
- [Mercado Pago — checkout multi-moeda](https://www.mercadopago.com.br/blog/checkout-multi-moeda-sem-risco-cambial)
- [Paddle — SaaS](https://developer.paddle.com/get-started/how-paddle-works/saas/)
