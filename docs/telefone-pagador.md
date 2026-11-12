# Telefone do pagador

A migração `V74__payment_payer_phone.sql` acrescenta `payment_subscriptions.payer_phone`, opcional.

## Captura

- Efí: formulário de pagamento, reativação e upgrade com cobrança. O telefone brasileiro validado recebe `+55`.
- Dodo (produção e teste): `customer.phone_number` da consulta de assinatura, já realizada na conciliação.
- Stripe: consulta de assinatura com `expand[]=customer`, lendo `customer.phone`.
- Mercado Pago: consulta opcional do pagamento associado à cobrança em `/v1/payments/{id}`, lendo `payer.phone` ou `additional_info.payer.phone`. Uma falha nesta consulta não interrompe a conciliação financeira.
- Creem: a API padrão de cliente não documenta telefone; a integração continua funcionando, sem captura de telefone nesta implementação.

A conciliação valida a vinculação da assinatura antes de capturar o telefone remoto. A formatação é removida, preservando o `+` e o código de país quando fornecidos. Números sem código internacional não recebem um país presumido. Valores ausentes ou malformados não apagam um telefone já capturado. O campo representa o último telefone capturado para a loja nesta assinatura; não é um histórico de pagadores nem comprova WhatsApp ou titularidade.

## Entrada em produção

É necessário publicar a aplicação e aplicar a migração para começar a persistir. Não há preenchimento retroativo via SQL nem reprocessamento de webhooks antigos. Assinaturas Dodo, Stripe e Mercado Pago existentes podem receber o telefone na próxima conciliação que devolver o dado; na Efí, a captura depende de novo envio do formulário. Nenhum novo campo obrigatório foi adicionado aos checkouts hospedados.

## Referências

- Dodo: `CustomerLimitedDetails.phone_number` no SDK oficial Java 1.121.0 e [consulta de assinatura](https://docs.dodopayments.com/api-reference/subscriptions/get-subscriptions).
- Stripe: [campo phone do cliente](https://docs.stripe.com/api/customers/object) e expansão do cliente na consulta de assinatura.
- Creem: [campos documentados do cliente](https://docs.creem.io/api-reference/endpoint/get-customer).
- Mercado Pago: [consulta de pagamento](https://www.mercadopago.com.br/developers/en/reference/online-payments/checkout-api-payments/get-payment/get).
