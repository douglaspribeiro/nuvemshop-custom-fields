# Avisos de falha de renovação no Creem

## Problema identificado

O aviso de renovação já existia na `main`, mas os eventos de estado do Creem passam por `synchronizeFromSubscriptionWithoutPayment`, sem uma fatura. A política de falha exigia uma cobrança não paga e deixava de registrar o incidente quando o contrato retornava `past_due`/`unpaid` e a última transação continuava sendo o pagamento anterior, `paid`.

## Comportamento corrigido

- Para um contrato Creem de produção anteriormente pago, o estado remoto atual `past_due`/`unpaid` também registra a falha. O estado é consultado pelo gateway existente; um webhook antigo não substitui essa consulta.
- A cobrança anterior mantém seu ID e status pago. Quando não há uma cobrança recusada identificada, o campo `renewal_failure_payment_id` usa uma chave técnica `creem-overdue:<hash>` do contrato, pagamento anterior e período. Essa chave identifica o incidente; não representa uma transação financeira inventada.
- O incidente entra na fila automática existente, com espera de uma hora e deduplicação. Repetições e conciliações com a mesma cobrança paga antiga não limpam a falha nem estendem sua carência.
- Mantida a carência existente de 24 horas para falha de renovação identificada. Cancelamento confirmado continua imediato. O aviso é descartado se o pagamento regularizar, a assinatura for cancelada, houver override manual ou outra condição de inelegibilidade antes do envio.
- O texto fica em português/espanhol conforme o mercado. Sem um código de recusa conhecido, informa falha de renovação sem atribuir um motivo específico. Nunca presume saldo insuficiente.
- Não altera o caminho de cobrança de Efí, Stripe ou Dodo. Não cria aviso para falha inicial de checkout, sandbox, cortesia, override ou upgrade pendente.
- No card de assinatura do backoffice, override é exibido como **Configuração manual**, preservando o estado real armazenado do gateway.

## Validação e publicação

Testes cobrem o fluxo de estado remoto sem fatura → incidente → fila → envio, repetições, cobrança antiga paga, pagamento recuperado, cancelamento imediato, mercado espanhol e exclusões. Os testes existentes de outros gateways continuam necessários para regressão.

Alteração local, sem envio de e-mails reais nem escrita em produção. Publicar o backend; não há alteração nos scripts da loja ou migration. Envios históricos ausentes não são reconstruídos nem reenviados automaticamente.

Referência: [eventos de webhook do Creem](https://docs.creem.io/skills/creem-api/WEBHOOKS).
