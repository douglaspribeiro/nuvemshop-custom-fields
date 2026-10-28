# Dodo: nova contratação após falha terminal

## Diagnóstico em 28/10/2026

Inspeção somente leitura da loja 5656805 em produção e da Dodo live confirmou: assinatura remota `failed`, pagamento `failed`, código `INSUFFICIENT_FUNDS`; pagamento em MXN 99,00. Localmente, a assinatura estava `PAUSED`, `provider_status=paused`, sem acesso, com a tentativa de checkout ainda `OPEN`.

O adaptador convertia `failed` e `expired` em `paused`. O checkout protege contratos existentes exigindo cancelamento quando ainda há um ID remoto e o status local não é `CANCELED`. Isso bloqueava a nova contratação de uma criação que já havia falhado.

A [documentação oficial Dodo](https://docs.dodopayments.com/features/subscription) distingue `failed` (falha terminal na criação inicial) de `paused` (pausa deliberada) e `on_hold`/`past_due` (recuperáveis). A ausência de saldo em uma renovação não é, sozinha, evidência de falha terminal: é necessário confirmar o status da assinatura.

## Correção

- Preservar `failed` e `expired` recebidos pela API, sem convertê-los em `paused`.
- Encerrar localmente esses contratos terminais, sem solicitar cancelamento remoto ou conceder acesso.
- Ao iniciar nova contratação, consultar e validar novamente contratos Dodo antigos sem acesso em `PAUSED`, `ERROR` ou `PAST_DUE`; só o estado remoto validado permite encerrar a tentativa. Continuam valendo as verificações de provedor/ambiente, referência, produto e preço/moeda ou catálogo regional validado.
- Preservar referência e checkout da tentativa anterior, identificando-a como encerrada. Eventos tardios de falha não alteram a nova compra nem tentam cancelar novamente uma assinatura terminal. Pagamentos tardios confirmados continuam sinalizados para investigação.
- Manter bloqueio de contratos ativos/recuperáveis, tentativas de criação indeterminadas e falha de comunicação. Não liberar uma nova compra apenas porque `last_payment_status=failed` ou o status local diz `PAUSED`.

A mudança vale para DODO e DODO_TEST. Não altera preços, não refaz cobranças, não cancela uma assinatura live e não modifica diretamente o cadastro de uma loja em produção.

## Validação local

Regressões cobrem preservação de status no adaptador live/sandbox, reconciliação de legado pausado, criação de novo checkout após `failed`/`expired`, isolamento de eventos tardios, ambiente divergente, falha de comunicação e preservação de contratos recuperáveis/ativos.

Comando: `mvn -o -q -Dskip.frontend=true test`.

Commit, push e implantação são etapas separadas. Até esta validação, produção foi apenas consultada; a loja não está liberada pelo código novo enquanto a correção não for implantada.

Resultado local: **791 testes, zero falhas, zero erros, 1 ignorado**; Maven terminou com código 0. `git diff --check` passou.
