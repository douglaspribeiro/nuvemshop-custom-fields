# Especificacao de pagamentos V1 e evolucao futura

## Status da decisao

- **V1:** Stripe sera o unico provedor de pagamentos dos planos pagos em todos os paises.
- **Futuro:** avaliar Mercado Pago e Efí para novas assinaturas cobradas em BRL.
- **Fora do escopo:** migracao automatica ou cobranca retroativa de lojas existentes.

Esta decisao substitui o uso da Billing API da Nuvemshop na V1. O aplicativo
permanece gratuito para instalacao e cobra, por conta propria, apenas o lojista
que solicitar um upgrade depois que a integracao estiver publicada.

## Escopo da V1

### Provedor e mercados

A Stripe processara `PREMIUM` e `PREMIUM_PLUS` em todos os mercados suportados
pelo aplicativo. O backend selecionara o preco pela combinacao de plano, pais e
moeda da loja. A configuracao inicial deve contemplar:

| Pais | Moeda |
| --- | --- |
| Brasil | `BRL` |
| Argentina | `ARS` |
| Chile | `CLP` |
| Colombia | `COP` |
| Mexico | `MXN` |

Somente cartao sera habilitado na V1. Cada preco recorrente da Stripe deve ter
um identificador configurado no ambiente; IDs e valores nao devem ficar fixos no
codigo. Alteracoes de preco criam novos Prices na Stripe e nao modificam de forma
silenciosa assinaturas existentes.

### Jornada do upgrade

1. O lojista escolhe `PREMIUM` ou `PREMIUM_PLUS` dentro do aplicativo.
2. O backend valida loja ativa, plano, pais, moeda e ausencia de outra tentativa
   ou assinatura incompatível.
3. O backend cria ou reutiliza o Customer da Stripe e cria uma Checkout Session
   em `mode=subscription`.
4. O checkout hospedado pela Stripe abre fora do iframe do admin da Nuvemshop.
5. Depois da conclusao, a Stripe retorna o navegador para uma pagina do app.
6. A pagina de retorno mostra apenas o estado de processamento. Ela nao concede
   acesso por conta propria.
7. O webhook assinado e a consulta da assinatura na Stripe determinam o plano
   efetivo da loja.

O `store_id`, o plano solicitado e um identificador interno da tentativa devem
ser enviados em `client_reference_id` e/ou `metadata`. Nenhum segredo ou token da
Nuvemshop pode ser enviado nesses campos.

### Fonte de verdade e eventos

A Stripe e a fonte de verdade para o estado financeiro. A aplicacao deve validar
a assinatura de todo webhook, processar eventos de forma idempotente e consultar
o objeto remoto quando o evento nao trouxer informacao suficiente.

Eventos minimos da V1:

- `checkout.session.completed`: vincular Customer e Subscription a loja, sem
  substituir as validacoes de status e pagamento.
- `invoice.paid`: ativar ou manter o acesso ao plano contratado.
- `invoice.payment_failed`: registrar a falha e aplicar a politica de tolerancia.
- `customer.subscription.updated`: sincronizar plano e status.
- `customer.subscription.deleted`: encerrar o acesso pago e voltar ao plano base.

Eventos repetidos ou fora de ordem nao podem duplicar auditoria, trocar um plano
mais novo por um estado antigo ou conceder acesso indevido.

### Estados locais

Persistir separadamente:

- provedor (`STRIPE` na V1);
- ID do Customer;
- ID da Subscription;
- ID do Price;
- plano contratado;
- moeda e valor;
- status remoto;
- periodo corrente e proxima renovacao;
- datas de cancelamento e encerramento;
- ultimo evento processado e ultima sincronizacao;
- ultimo erro de integracao.

`stores.plan` nao deve mudar ao criar a Checkout Session nem ao receber apenas o
retorno do navegador. O acesso pago muda somente depois de confirmacao confiavel
da Stripe. A cortesia de 30 dias continua independente da assinatura comercial.

### Gestao e suporte

O app deve oferecer acesso ao Stripe Customer Portal para atualizar cartao,
consultar faturas e cancelar a assinatura. O backoffice deve mostrar o provedor,
IDs remotos, status, periodo atual e erros recentes, sem expor credenciais ou
dados completos do cartao.

Devem existir reconciliacao manual por loja e rotina periodica para corrigir
webhooks perdidos. Logs de checkout, webhook e sincronizacao devem carregar o
`requestId`, `store_id` e os IDs remotos aplicaveis.

### Seguranca e configuracao

- Chave secreta da Stripe somente no backend e em secret do ambiente.
- Chave publicavel pode ser exposta ao frontend quando necessaria.
- Segredo de webhook separado por ambiente.
- HTTPS obrigatorio em checkout, retorno, portal e webhook.
- Ambientes de teste e producao nao compartilham Products, Prices ou credenciais.
- O app nunca recebe nem persiste numero completo do cartao ou CVC.

### Criterios de aceite da V1

- Upgrade funciona nos cinco mercados e cobra na moeda configurada para a loja.
- Apenas cartao aparece como forma de pagamento.
- Cancelar ou abandonar o checkout mantem o plano anterior.
- Retorno forjado nao concede Premium.
- Webhook invalido e rejeitado sem alterar a loja.
- Reenvio do mesmo evento e idempotente.
- Pagamento aprovado, falha, recuperacao e cancelamento refletem o acesso correto.
- Uma loja nao cria assinaturas duplicadas por clique duplo ou repeticao de request.
- Lojas instaladas antes da V1 nao sao cobradas nem migradas automaticamente.

## Abstracao para provedores futuros

A V1 deve evitar nomes e regras da Stripe no dominio central. O servico de planos
deve depender de um contrato interno com operacoes equivalentes a:

- iniciar checkout;
- consultar assinatura;
- criar sessao de gerenciamento;
- cancelar assinatura;
- validar e normalizar webhook;
- reconciliar estado.

Controllers e regras de limite trabalham com estados internos normalizados. SDKs,
payloads e status especificos ficam no adaptador da Stripe. Essa separacao permite
incluir outro provedor sem alterar as regras dos planos.

## Avaliacao futura para BRL

Mercado Pago e Efí serao avaliados somente para **novas assinaturas em BRL**. As
demais moedas permanecem na Stripe. A avaliacao nao autoriza troca automatica do
provedor nem faz parte da entrega da V1.

Fontes iniciais para a avaliacao:

- Mercado Pago: API de Assinaturas, checkout hospedado e checkout transparente.
- Efí: [pagina publica de tarifas](https://sejaefi.com.br/tarifas) e
  documentacao oficial da API vigente na data da prova de conceito.

A pagina da Efí consultada em 23/09/2026 anuncia pagamento recorrente, checkout
transparente e integracao por API sem tarifa adicional de uso, alem da tarifa da
transacao de cartao. Ela tambem informa que valores podem variar conforme data de
contratacao ou negociacao. Portanto, numeros atuais nao devem ser codificados nem
usados como compromisso comercial futuro.

### Criterios de comparacao

- tarifa efetiva e prazo de recebimento;
- taxa de aprovacao e qualidade do antifraude;
- recorrencia, retentativas e tratamento de inadimplencia;
- tokenizacao, 3DS e experiencia dentro/fora do iframe;
- webhooks assinados, idempotencia e consulta para reconciliacao;
- cancelamento, estorno, chargeback e portal do assinante;
- ambiente de teste, observabilidade e suporte operacional;
- conciliacao financeira, exportacao e emissao fiscal;
- estabilidade contratual e requisitos de homologacao da Nuvemshop.

### Regra de migracao futura

Uma eventual escolha de Mercado Pago ou Efí afetara primeiro apenas upgrades BRL
feitos depois da ativacao do novo roteamento. Assinaturas Stripe existentes
continuarao na Stripe ate cancelamento ou migracao explicitamente consentida pelo
lojista. Dados de cartao nao sao portados pelo aplicativo entre provedores.

