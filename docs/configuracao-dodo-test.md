# Dodo de teste no servidor

`DODO_TEST` é um provedor separado, fixo em sandbox (`https://test.dodopayments.com`).
O adaptador de teste nunca seleciona produção. O adaptador live é DODO; os dois compartilham a mesma implementação e política de preços. O gateway é desabilitado por padrão.
A conexão MCP Dodo permite administrar a conta, mas não fornece as credenciais da aplicação.

## Credenciais do servidor

Configure no gerenciador de segredos usado pelo servidor:

```dotenv
DODO_TEST_ENABLED=true
DODO_TEST_API_KEY=CHAVE_DE_API_DO_MODO_TESTE
DODO_TEST_WEBHOOK_KEY=whsec_...
DODO_TEST_ALLOWED_STORE_IDS=ID_DA_LOJA_DE_TESTE
DODO_TEST_TIMEOUT_SECONDS=20
```

Não salve segredos no Git. A chave deve ser do ambiente de teste; o formato é
opaco e é autenticado pelo endpoint sandbox do Dodo. A lista de lojas
restringe o teste quando contém IDs específicos. Vazia ou com `0` autoriza todas
as lojas fora do Brasil, desde que país e plano tenham preço de teste habilitado.
Uma loja brasileira nunca é elegível, mesmo que esteja na lista. O perfil
`local-homolog` mantém o Dodo desabilitado; esta configuração é para a instância
no servidor. Não é necessário configurar uma rota Dodo para todo o país.

## Produto específico

1. No painel Dodo em **test mode**, crie um produto SaaS com preço recorrente mensal,
   com cobrança em USD para AR/CO, CLP para CL e MXN para MX, sem trial, descontos, impostos inclusos ou preço variável.
2. No app, abra **Pagamentos → Configurar Dodo de teste**
   (`/backoffice/payments/dodo-test`). Selecione o plano do app que será liberado,
   o país da loja de teste, o ID `pdt_…`, a moeda e o valor do produto.
3. **Validar e vincular** consulta o produto pela API sandbox e salva seu vínculo
   desabilitado. Uma validação que falhe preserva o vínculo anterior.
4. Em **Pagamentos**, filtre **DODO_TEST / SANDBOX** e habilite o preço vinculado.
5. Abra os planos na loja autorizada. A seleção mostra apenas planos com produto
   configurado. Outras lojas continuam nas rotas existentes; o Brasil não muda.

O teste aceita um único plano configurado; não exige publicar Essencial e Pro.
Não há upgrade neste primeiro adaptador. A criação de produtos não acontece ao
iniciar o servidor ou ao aplicar migrações. O catálogo usa as tabelas existentes.

## Webhook e confirmação

Cadastre no Dodo de teste a URL HTTPS pública do servidor com o caminho
`/prod/webhooks/dodo-test` (alias `/webhooks/dodo-test` quando o proxy remove `/prod`).
Assine os eventos `payment.succeeded`, `payment.failed`, `payment.cancelled`,
`subscription.active`, `subscription.renewed`, `subscription.updated`,
`subscription.on_hold`, `subscription.cancelled`, `subscription.failed` e
`subscription.expired`. Configure o segredo desse endpoint no servidor.

A verificação usa o SDK oficial e os cabeçalhos `webhook-id`, `webhook-signature`
e `webhook-timestamp`, sobre o corpo original. Eventos são deduplicados e gravados
na fila persistida; falhas de processamento têm nova tentativa com lease.
O processamento consulta o estado remoto e verifica provedor, ambiente, referência,
produto, moeda e valor antes de liberar o plano. O redirecionamento do navegador
não é uma confirmação de pagamento. A consulta de retorno/job também confirma o
pagamento na API do servidor, sem confiar em parâmetros do navegador.

A tentativa é persistida antes da chamada de checkout. O mesmo link é reutilizado;
resposta incerta bloqueia outra cobrança até conciliação. Checkouts abandonados
precisam de conciliação administrativa: o adaptador não presume que o tempo local
cancele o link remoto. Não apague uma tentativa para contornar esse bloqueio.

Cancelamento solicitado pelo lojista preserva o período pago; cancelamento imediato,
expiração e reembolso integral detectados na consulta retiram acesso. Eventos de
refund/dispute ainda não têm tratamento dedicado neste primeiro teste; a consulta
periódica detecta o reembolso integral do pagamento. O teste de homologação inicial
cobre checkout, ativação, renovação e cancelamento. Valores sandbox ficam fora do
MRR e dos relatórios financeiros de produção.

## Ativação

Após publicar o código no servidor, configurar segredos, produto, loja autorizada
e webhook, faça uma compra usando os dados de teste do Dodo. Confirme no banco
`provider=DODO_TEST` e `provider_environment=SANDBOX`, o ID da assinatura e o plano.
Não foram criados produtos, assinaturas ou endpoints remotos automaticamente.

Referências: [checkout](https://docs.dodopayments.com/developer-resources/checkout-session),
[webhooks](https://docs.dodopayments.com/developer-resources/webhooks),
[SDK Java](https://github.com/dodopayments/dodopayments-java).

Para a configuração completa de preços por país e os passos de live, consulte [Preços regionais no Dodo](precos-regionais-dodo.md).

## Catálogo sandbox já criado via integração

Os três produtos de assinatura mensal foram criados e conferidos na conta Dodo
de teste. A migration `V53__seed_dodo_test_catalog.sql` registra seus vínculos para
AR, CO, CL e MX. Ela preserva vínculos que já existam e não altera rotas,
contratos de clientes ou produção. Os preços novos entram **desabilitados**;
após publicar, configure as credenciais e o webhook e habilite-os em Pagamentos.

| Plano | Produto sandbox | AR/CO (USD) | CL (CLP) | MX (MXN) |
| --- | --- | ---: | ---: | ---: |
| Essencial | `pdt_0NpncfPHBuCeAoWYEMKWi` | 4,99 | 4.199 | 99 |
| Pro | `pdt_0NpncfTZsACz359krtMyz` | 7,49 | 6.299 | 149 |
| Ultra | `pdt_0NpncfWWeuij8hUmcu1vb` | 14,99 | 12.599 | 299 |

Os IDs das seis regras regionais e a resposta de preços conferida estão em
[integracoes/dodo-test-catalog.json](integracoes/dodo-test-catalog.json).
Esses produtos permitem iniciar assinaturas pelo checkout; nenhuma assinatura
foi contratada para cliente ou loja durante o cadastro do catálogo.
