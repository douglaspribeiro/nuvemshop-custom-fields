# Configuração das filas da reconquista

O SMTP e o remetente continuam sendo os mesmos já usados pelo suporte. Não é
necessário criar outro remetente ou outra credencial SMTP no SES.

## Fila de desinstalações

```text
WINBACK_SQS_QUEUE_URL=https://sqs.us-east-2.amazonaws.com/265105089924/eventosDesistalacao
WINBACK_AWS_REGION=us-east-2
WINBACK_ENABLED=false
```

A role da aplicação precisa de `sqs:SendMessage` somente no ARN
`arn:aws:sqs:us-east-2:265105089924:eventosDesistalacao`. A outbox grava o evento
no banco e o publicador envia depois, com atraso de 15 minutos. O corpo não contém
Store ID, e-mail, token ou nome:

```json
{"version":1,"eventId":"uuid"}
```

Não use credenciais SMTP para acessar SQS. Use role AWS ou credenciais de API com
a permissão acima e região `us-east-2`.

## Eventos do SES

O suporte já usa SES. Reaproveite as mesmas variáveis SMTP e `AWS_SES_FROM_EMAIL`.
Para registrar entrega, abertura, clique, bounce e reclamação, configure um
Configuration Set do SES com destino para a fila SQS de eventos:

```text
AWS_SES_EVENTS_ENABLED=false
AWS_SES_EVENTS_QUEUE_URL=<URL da fila SQS de eventos SES>
AWS_SES_EVENTS_REGION=<região da fila de eventos>
AWS_SES_CONFIGURATION_SET=<nome do Configuration Set>
```

Habilite `Send`, `Delivery`, `Open`, `Click`, `Bounce` e `Complaint`. A role da
aplicação precisa de `sqs:ReceiveMessage`, `sqs:DeleteMessage` e
`sqs:ChangeMessageVisibility` nessa fila. Se o destino usar SNS, restrinja a
política de publicação ao tópico SES correspondente. Configure DLQ e redrive.

Não coloque outro consumidor concorrente na mesma fila se ele precisar de todas as
mensagens: SQS distribui cada mensagem para um consumidor.

## Ordem de ativação

1. Aplicar V30–V33 no MySQL de homologação.
2. Configurar role e testar envio/recebimento nas duas filas.
3. Confirmar que o SMTP do suporte continua funcionando.
4. Enviar e-mail de teste com as tags da reconquista e conferir `Send`, `Delivery`,
   `Open` e `Click` no backoffice.
5. Ativar `WINBACK_ENABLED=true`, depois `WINBACK_MAIL_ENABLED=true` somente após
   o teste controlado. `AWS_SES_EVENTS_ENABLED=true` ativa o consumidor SES.

Mantenha `WINBACK_DISCOUNT_ENABLED=false` e
`WINBACK_DISCOUNT_ALLOW_PRODUCTION=false` até a aprovação operacional.

## Diagnóstico do envio manual

O e-mail inicial de feedback exige `WINBACK_MAIL_ENABLED=true`, SMTP completo e
`AWS_SES_CONFIGURATION_SET` preenchido com um Configuration Set existente no SES.
As variáveis `WINBACK_DISCOUNT_ENABLED` e `WINBACK_DISCOUNT_ALLOW_PRODUCTION`
controlam os descontos e não bloqueiam esse e-mail.

O backoffice informa qual requisito falta antes de preparar a tentativa. Lojas
com exclusão pendente exigem a confirmação de autorização do contato manual.
Uma falha durante o SMTP mostra o ID da tentativa; consulte Reconquistas antes de
novo envio. O registro `winback.manual.failed` contém o ID da loja, da tentativa,
a etapa e o tipo de exceção, sem credenciais ou endereço do destinatário.
