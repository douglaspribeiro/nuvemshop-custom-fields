# Stripe para lojas da Argentina

A conta recebedora é brasileira. O país da loja continua AR; a cobrança usa o
preço mensal configurado na Stripe. A implementação usa Checkout hospedado,
Billing e API REST versionada em `2025-06-30.basil`, sem dados de cartão no app.
A Stripe precisa habilitar a moeda e pagamentos internacionais para a conta.
O cadastro validado da conta não comprova autorização de uma compra específica.

## Configuração do servidor

```dotenv
STRIPE_ENABLED=false
STRIPE_SANDBOX=true
STRIPE_SECRET_KEY=
STRIPE_WEBHOOK_SECRET=
STRIPE_ALLOWED_STORE_IDS=
STRIPE_GRACE_DAYS=0
STRIPE_TIMEOUT_SECONDS=20
```

Em teste, usar chave `sk_test_...`, segredo `whsec_...` do endpoint de teste e
IDs explícitos de lojas argentinas em `STRIPE_ALLOWED_STORE_IDS`, separados por
vírgula. Vazio e `0` não autorizam lojas. Em produção, usar `STRIPE_SANDBOX=false`
com chave `sk_live_...` e o segredo do endpoint de produção. Não colocar chaves
no navegador, no banco de catálogo ou no Git. Recarregar a aplicação depois de
alterar as variáveis.

`STRIPE_ENABLED` bloqueia novas compras quando falso. Assinaturas existentes,
webhooks e conciliação continuam funcionando enquanto as credenciais do ambiente
original estiverem presentes. A instância opera um ambiente Stripe por vez;
não trocar de produção para teste com contratos reais nessa mesma instância.
O perfil de atalho de login `local-homolog` continua bloqueando Stripe.

## Catálogo e rota

1. Aplicar as migrações na sequência normal. A V56 adiciona seis preços
   desabilitados para AR: três planos em SANDBOX e três em PRODUCTION.
   A migração preserva a rota atual da Argentina e todos os contratos existentes.
2. Em **Backoffice → Planos → Stripe · Argentina** (`/backoffice/plans#stripe`),
   selecionar Essencial, Pro ou Ultra e informar nome, descrição, moeda e
   mensalidade. **Criar produto e preço na Stripe** cria o produto com preço
   padrão mensal fixo, sem trial ou cobrança por uso, com `tax_behavior=inclusive`.
   Repetir para os demais planos. O ID `price_...` é validado e salvo automaticamente.
   Para publicar, basta `STRIPE_SECRET_KEY` válida no ambiente selecionado;
   `STRIPE_ENABLED` e webhook não são requisitos para criar o catálogo.
3. Em **Backoffice → Pagamentos**, filtrar STRIPE e AR, revisar os valores e
   habilitar os preços. O servidor consulta a Stripe e valida produto ativo,
   preço, ambiente, recorrência e imposto antes de habilitar cada preço.
4. Os valores iniciais ARS 5.599 / 8.399 / 16.799 seguem o catálogo regional do
   aplicativo e são sugestões editáveis. Confirmar os valores e a moeda aceita
   pela conta brasileira antes de ativar. Também é possível configurar USD ou
   BRL; todos os planos ativos do país devem usar a mesma moeda.
5. Habilitar `STRIPE_ENABLED=true`. Selecionar STRIPE, AR e o ambiente correto na
   rota do país. A rota exige pelo menos Essencial e Pro validados; Ultra pode
   ser habilitado depois. Somente novas contratações usam essa rota.

A V57 cria o histórico das publicações Stripe. Uma reserva persistente é
confirmada antes da chamada externa, com chave de idempotência estável por
operação e exclusão mútua por plano/ambiente. Produto e preço padrão são criados
em uma única chamada. Ambos os IDs sobrevivem a falhas posteriores de vínculo.
Se a resposta for incerta, usar **Retomar publicação Stripe**. Após 20 horas sem
resposta confirmada, consultar a Stripe e usar **Vincular preço existente** para
evitar reenvio fora da janela de idempotência. Essa opção também permite vincular
preços que já existam no Dashboard, sem criar produtos novos.

Criar ou vincular mantém o preço local desabilitado e preserva as rotas e
assinaturas existentes. Nome e descrição são dados públicos do produto: não
incluir credenciais ou informações de clientes nesses campos.

O Dodo de teste tem prioridade para lojas explicitamente autorizadas em sua
configuração. Para testar Stripe nessas lojas, desativar DODO_TEST nessa
instância de teste ou retirar a loja da autorização Dodo antes do teste.

## Webhook

Cadastrar o endpoint público `https://campos-personalizados.wzhub.pro/prod/webhooks/stripe`.
Selecionar eventos snapshot e a versão
`2026-03-25.dahlia`, disponível no Dashboard. A versão anterior
`2025-06-30.basil` também é aceita para compatibilidade. Eventos thin não são
aceitos por este endpoint.

A versão do webhook é independente da versão das chamadas REST. O consumidor
usa somente os identificadores do evento e do recurso; os dados financeiros são
consultados na API com `Stripe-Version: 2025-06-30.basil`. Não é necessário
alterar a versão padrão da conta nem adicionar uma variável de ambiente.

Eventos tratados:

- `checkout.session.completed`, `checkout.session.expired`,
  `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed`;
- `customer.subscription.created`, `customer.subscription.updated`,
  `customer.subscription.deleted`, `customer.subscription.paused`, `customer.subscription.resumed`;
- `invoice.paid`, `invoice.payment_succeeded`, `invoice.payment_failed`,
  `invoice.voided`, `invoice.marked_uncollectible`;
- `charge.refunded`, `charge.dispute.created`, `charge.dispute.updated`,
  `charge.dispute.closed`, `charge.dispute.funds_withdrawn`, `charge.dispute.funds_reinstated`.

A assinatura usa o corpo original e `Stripe-Signature`, com tolerância de cinco
minutos. Após verificar assinatura, ambiente e versão, o app persiste somente
identificadores, deduplica pelo ID do evento e retorna 200. Um consumidor usa
lease e retentativas persistentes. O processamento consulta o estado remoto
atual para que eventos antigos não restaurem pagamentos ultrapassados.

## Comportamento e validação

A reserva do checkout é confirmada no banco antes da chamada externa. Uma
resposta incerta mantém a tentativa bloqueada para evitar cobranças duplicadas;
o webhook pode recuperar a tentativa pela referência. A chave de idempotência
é estável por tentativa. O retorno do navegador não libera acesso: assinatura,
preço, moeda, referência e fatura paga precisam ser confirmados no servidor.
Nenhuma assinatura existente é migrada automaticamente.

Cancelamento normal ocorre ao final do período. Renovação recusada usa
`STRIPE_GRACE_DAYS` (padrão zero). Cancelamento imediato, falta de pagamento,
reembolso integral da fatura atual e disputa aberta/perdida retiram o acesso.
Reembolso parcial preserva o acesso. Faturas pagas usando saldo/crédito também
são reconhecidas. O checkout expira após aproximadamente 30 minutos.

Upgrade Stripe dentro do aplicativo ainda não está disponível; a interface
oculta essa opção e o servidor bloqueia alterações para impedir liberação de
plano sem confirmação de uma cobrança proporcional. Não alterar manualmente
o preço de um contrato no Dashboard, pois o vínculo local precisa permanecer
consistente. A responsabilidade fiscal e os impostos da venda continuam com
o titular da conta; Checkout/Billing comuns não são Merchant of Record.

Antes de produção, testar com uma loja AR autorizada: compra aprovada e recusada,
3DS, reenvio de webhook, evento fora de ordem, resposta de checkout perdida,
renovação, falha de renovação, cancelamento agendado/imediato, expiração,
reembolso integral/parcial e disputa. Conferir Stripe, banco e acesso da loja.
Testes locais com API simulada não comprovam entrega de webhook ou compra real.

Referências oficiais: [Checkout Sessions](https://docs.stripe.com/api/checkout/sessions/create),
[API versioning](https://docs.stripe.com/api/versioning),
[webhooks](https://docs.stripe.com/events/manage-webhook-endpoints),
[invoice payments na versão Basil](https://docs.stripe.com/changelog/basil/2025-03-31/add-support-for-multiple-partial-payments-on-invoices),
[moedas](https://docs.stripe.com/currencies).
