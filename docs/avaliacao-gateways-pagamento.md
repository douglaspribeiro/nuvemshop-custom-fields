# Avaliacao de gateways para assinaturas

## Situacao atual

Mercado Pago foi escolhido e implementado para a V1 brasileira. Paddle e o
primeiro candidato para lojas nao brasileiras. A implementacao Stripe foi
interrompida antes de entrar no produto.

O app precisa cobrar mensalmente os planos Essencial e Pro. A preferencia e
cartao de credito, sem cobranca retroativa e com ativacao do plano somente depois
da confirmacao do provedor.

## O problema que divide as alternativas

Ha duas entregas diferentes:

1. cobrar em BRL de lojistas brasileiros com uma conta brasileira;
2. cobrar tambem Argentina, Chile, Colombia e Mexico, idealmente com uma unica
   conta e liquidacao para a empresa brasileira.

Mercado Pago e Efí atendem bem a primeira entrega. A documentacao publica nao
demonstra que uma conta brasileira de qualquer um deles resolva a segunda.

## Comparacao inicial

| Provedor | BRL recorrente | Outros mercados | Experiencia | Avaliacao |
| --- | --- | --- | --- | --- |
| Mercado Pago | Sim, por Assinaturas | O produto existe nos cinco paises, mas credenciais e meios dependem do pais da conta | Checkout hospedado com redirect; retentativas e gestao da assinatura | Melhor opcao entre os dois planejados para uma V1 somente no Brasil |
| Efí | Sim, por plano e assinatura | A API de Cobrancas e orientada a conta brasileira e valores em reais | Tokenizacao de cartao no frontend; exige dados do pagador e habilitacao para cartao | Boa alternativa BRL, com integracao e operacao mais trabalhosas |
| Paddle | Sim para SaaS global, sujeito a aprovacao | Merchant of Record para software, com precificacao em varias moedas | Checkout e portal hospedados; cuida de impostos, faturas e cobranca | Primeiro candidato para uma unica operacao global se o produto for aprovado |
| EBANX | Sim | Card-on-file documentado em BR, AR, CL, CO e MX | Integracao regional e negociacao comercial | Candidato forte para LATAM; confirmar volume minimo, recorrencia e contrato |
| dLocal | Sim | Processamento local e cartao salvo para recorrencia em varios mercados | API e campos seguros; cobrancas subsequentes controladas pelo app | Candidato forte; confirmar disponibilidade comercial para o porte atual |

No Mercado Pago, a propria API informa meios de pagamento de acordo com o pais
associado a conta. Portanto, considerar que uma conta brasileira cobre localmente
em todas as moedas seria uma inferencia insegura. Isso precisa de confirmacao por
escrito do comercial antes de qualquer desenvolvimento multi-pais.

Na Efí, a recorrencia por cartao existe e permite retentativa de uma cobranca
`unpaid`, mas o app precisaria obter `payment_token`, coletar os dados obrigatorios
do pagador e passar pela habilitacao da API de cartao.

## Caminhos viaveis

### Caminho A — lancar primeiro no Brasil

- Mercado Pago Assinaturas para BRL.
- Upgrade permanece indisponivel para lojas fora do Brasil.
- Lojas estrangeiras continuam no Free ou recebem cortesia pelo backoffice.
- Em paralelo, negociar EBANX/dLocal ou solicitar aprovacao na Paddle.

Esse e o caminho com menor tempo e risco. Entre Mercado Pago e Efí, o Mercado
Pago se aproxima mais do fluxo ja planejado porque hospeda o formulario, retorna
ao app e administra retentativas.

### Caminho B — aguardar um unico provedor global

- Solicitar aprovacao do app na Paddle antes de escrever a integracao.
- Abrir consulta comercial com EBANX e dLocal usando os cinco paises, moedas,
  ticket medio e volume inicial estimado.
- Comparar contrato, tarifa, prazo de liquidacao, reserva, chargeback, impostos,
  recorrencia, retentativas, checkout e webhooks.
- Implementar somente depois de obter confirmacao escrita da cobertura.

Esse caminho evita duas integracoes, mas adia a monetizacao e depende de
aprovacao comercial.

## Decisao atual

Usar Mercado Pago apenas para lojas brasileiras na V1 e manter o upgrade
indisponivel nos demais mercados. Submeter o produto a Paddle antes de iniciar o
segundo adaptador; EBANX e dLocal continuam como alternativas caso a aprovacao
ou as condicoes comerciais da Paddle nao atendam.

Mesmo no caminho BRL, o dominio deve continuar neutro em relacao ao provedor.
Assinatura, tentativa, evento e status ficam em tabelas proprias, e o adaptador
Mercado Pago implementa um contrato interno. Isso permite adicionar outro
provedor por pais sem reescrever as regras dos planos.

## Confirmacoes antes de implementar

- prioridade entre lancar BRL agora e lancar todos os paises juntos;
- limitacoes especificas da Stripe que inviabilizaram a conta;
- precos mensais de Essencial e Pro em BRL;
- empresa recebedora, conta bancaria e documentos disponiveis;
- volume mensal inicial e ticket medio para consultas comerciais;
- politica de cancelamento, tolerancia e reembolso.

## Fontes oficiais consultadas

- [Mercado Pago — visao geral de Assinaturas](https://www.mercadopago.com.br/developers/pt/docs/subscriptions/overview)
- [Mercado Pago — disponibilidade por pais](https://www.mercadopago.com.br/developers/pt/docs/getting-started)
- [Mercado Pago — meios dependentes do pais da conta](https://www.mercadopago.com.br/developers/pt/docs/sales-processing/payment-methods)
- [Efí — API de Assinaturas](https://dev.efipay.com.br/docs/api-cobrancas/assinatura/)
- [Efí — cartao e tokenizacao](https://dev.efipay.com.br/docs/api-cobrancas/cartao/)
- [Paddle para SaaS](https://developer.paddle.com/get-started/how-paddle-works/saas/)
- [Paddle — politica de uso](https://www.paddle.com/help/start/intro-to-paddle/what-am-i-not-allowed-to-sell-on-paddle)
- [EBANX — Card on File](https://docs.ebanx.com/docs/pay-in/features/cards/card-on-file/overview-card-on-file)
- [dLocal — cartao recorrente](https://docs.dlocal.com/docs/card-payments)
