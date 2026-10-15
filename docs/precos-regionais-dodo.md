# Preços regionais no Dodo: teste e live

A política aprovada é a mesma nos dois ambientes:

| País da loja | Exibição | Moeda cobrada |
| --- | --- | --- |
| Argentina (AR) | USD + estimativa em ARS | USD |
| Colômbia (CO) | USD + estimativa em COP | USD |
| Chile (CL) | Preço fixo em CLP | CLP |
| México (MX) | Preço fixo em MXN | MXN |
| Outros, fora do Brasil | USD | USD |

O Brasil mantém seu roteamento. Os valores de referência existentes para o
Essencial são USD 4,99, CLP 4.199 e MXN 99 por mês. Pro e Ultra mantêm os valores
já cadastrados por país. Referências não criam nem habilitam produtos remotos.

## Uma implementação nos dois ambientes

`DodoGateway`, `DodoCheckoutService` e `DodoWebhookService` implementam validação,
reserva de tentativas, checkout e conciliação. Os adaptadores selecionam apenas as
credenciais, o provedor, a URL e o ambiente:

| Configuração | Teste | Live |
| --- | --- | --- |
| Provedor no app | DODO_TEST | DODO |
| Ambiente | SANDBOX | PRODUCTION |
| API | test.dodopayments.com | live.dodopayments.com |
| Habilitar | DODO_TEST_ENABLED | DODO_ENABLED |
| API key | DODO_TEST_API_KEY | DODO_API_KEY |
| Segredo webhook | DODO_TEST_WEBHOOK_KEY | DODO_WEBHOOK_KEY |
| Catálogo | /backoffice/payments/dodo-test | /backoffice/payments/dodo |
| Webhook | /prod/webhooks/dodo-test | /prod/webhooks/dodo |

Ambos ficam desabilitados por padrão. Teste permite restringir IDs em
`DODO_TEST_ALLOWED_STORE_IDS`; vazio ou `0` autoriza todas as lojas fora do Brasil
com país/plano de teste habilitado. Live exige rota por país habilitada em
**Pagamentos**, com gateway DODO e ambiente PRODUCTION. Habilitar o teste nunca
ativa produção, e o catálogo público não consulta preços sandbox.

Chaves, IDs de produtos, vínculos locais, endpoints e segredos de webhooks são
separados por ambiente. Não existe promoção automática de um ID sandbox para
live. As operações de assinaturas existentes usam seu provedor original.

## Produtos e preços fixos

Use um produto SaaS mensal com preço-base em USD. No Dodo, escolha
**Localized Pricing → By Country** e cadastre os preços fixos de CL em CLP e de
MX em MXN. AR e CO mantêm o preço-base em USD. Também é possível vincular um
produto com preço-base diretamente na moeda esperada para o país. O adaptador
valida tanto produtos base-only quanto regras by_country/by_currency.

Vincule no backoffice o produto, país, plano, moeda e mensalidade exatos. O app
consulta o produto e as regras regionais pela API; um preço divergente ou uma
regra ausente bloqueiam o checkout. Conversão automática não é aceita como
substituto de preço regional fixo. Não são aceitos trial, desconto embutido,
impostos inclusos, PPP ou cobrança por uso nesta implementação.

CLP é moeda sem casas decimais: CLP 4.199 corresponde a `amount=4199` na API.
MXN usa duas casas: MXN 99 corresponde a `amount=9900`. A mesma precisão é usada
para pagamentos e assinaturas consultados; nunca se divide CLP por 100.

O checkout fixa `billing_currency` e o país da loja e desabilita troca de moeda,
cupons e alteração do país. A mensalidade retornada pelo Dodo deve corresponder
à tentativa persistida antes de liberar acesso. Impostos são apresentados no
checkout e o pagamento pode incluir impostos além da mensalidade base.

A página pública só muda para preços locais quando a rota live DODO está
habilitada e o preço do plano está validado/habilitado. Planos sem preço elegível
mostram “Consulte”, preservando a moeda e o valor realmente oferecidos. O catálogo
sandbox não é usado como anúncio de preços de produção.

## Estimativas para Argentina e Colômbia

`CurrencyEstimateService` usa o endpoint público diário de ExchangeRate-API,
com cache de 24 horas e consulta com timeout curto. As telas mostram o valor
aproximado, a data da cotação, a atribuição da fonte e que a cobrança é em USD.
As estimativas não entram no catálogo, checkout ou conciliação da assinatura.
Se a fonte falhar, uma cotação recente pode ser usada por até 48 horas; sem
cotação válida, apenas o preço em USD é mostrado. Não se inventa uma cotação.
`FX_ESTIMATES_ENABLED=false` desliga essa exibição. Nos testes não há consulta
externa real; respostas e relógio são simulados.

## Validação e ativação no servidor

1. Publicar o código com os gateways inicialmente desabilitados.
2. Configurar as credenciais sandbox e autorizar a loja de teste.
3. Criar/vincular o produto e os preços regionais sandbox e cadastrar seu webhook.
4. Testar CLP, MXN e USD, incluindo confirmação de pagamento, renovação,
   cancelamento e persistência de moeda/valor. Testar ARS/COP apenas como estimativa.
5. Depois de homologar, criar os produtos e endpoints equivalentes no live,
   configurar as credenciais live e validar os vínculos de produção.
6. Habilitar os preços e as rotas live desejadas. Confirmar checkout e valor
   público antes de ampliar a ativação.

O código suporta compra, renovação e cancelamento. Upgrade ainda não está
habilitado para Dodo; refunds/disputes ainda não têm evento dedicado. A consulta
periódica detecta reembolso integral do pagamento. Uma tentativa com resultado
indeterminado permanece bloqueada até conciliação; não há repetição cega de cobrança.
Os produtos e preços sandbox foram posteriormente criados via integração e
registrados pela migration V53; consulte [o catálogo criado](configuracao-dodo-test.md#catálogo-sandbox-já-criado-via-integração).
Nenhum produto live, assinatura de cliente, webhook ou rota live foi criado ou ativado.

Fontes: [Localized Pricing](https://docs.dodopayments.com/features/localized-pricing),
[Adaptive Currency](https://docs.dodopayments.com/features/adaptive-currency),
[precisão de moedas](https://docs.dodopayments.com/changelog/v1.105.0),
[cotações](https://www.exchangerate-api.com/docs/free).
