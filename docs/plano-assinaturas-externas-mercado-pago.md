# Gestão de assinaturas externas do Mercado Pago

Data: 06/11/2026.
Status: planejado; implementação e validação em produção pendentes.

## Contexto confirmado

Um cliente não conseguiu contratar pela Efí. Foi enviado um link de assinatura
do Mercado Pago, o cliente pagou com sucesso e seu plano foi alterado manualmente
de Free para Pro no backoffice. O usuário confirmou que o link era de assinatura,
com recorrência, e não de pagamento avulso.

A confirmação do pagamento foi relatada pelo usuário. A loja, o ID da assinatura,
o valor, o período pago e o estado nos provedores ainda precisam ser identificados
e consultados; não houve verificação desses dados em produção nesta análise.

## Decisão proposta

Manter o contrato existente no Mercado Pago e vinculá-lo à loja, preservando o
pagamento e o acesso Pro. Não exigir nova contratação para regularizar o vínculo.

O Mercado Pago gerencia a cobrança recorrente. O sistema deve acompanhar essa
assinatura e administrar o acesso conforme pagamentos e vigência confirmados.

## Situação atual do código

- `BackofficeService.overridePlan` altera o plano e registra um evento `OVERRIDE`,
  mas não associa uma assinatura ou pagamento externo à loja.
- `MercadoPagoGateway` já oferece consultas de assinatura e cobranças,
  criação de checkout e cancelamento.
- `PaymentWebhookService` trata eventos de assinatura e cobrança do Mercado Pago.
- `PaymentReconciliationJob` consulta periodicamente assinaturas locais elegíveis.
- `PaymentSubscriptionService.locate` exige um vínculo local por identificador
  de assinatura, checkout ou referência externa para localizar a loja.

Essa base pode ser aproveitada. Sua existência no código não comprova que o
contrato criado pelo link externo está acessível às credenciais da integração,
nem que suas notificações chegam e são processadas em produção.

## Etapa 1: vincular a assinatura existente

Adicionar a ação **Vincular assinatura do Mercado Pago** no backoffice.

1. Selecionar a loja e informar o ID da assinatura.
2. Consultar o Mercado Pago e apresentar situação, valor, moeda, periodicidade,
   último pagamento confirmado e próxima cobrança, quando disponíveis.
3. Conferir a correspondência entre cliente, contrato e plano Pro. Não associar
   automaticamente apenas por e-mail ou por valor.
4. Verificar a tentativa da Efí antes de concluir: identificar eventual contrato
   ou cobrança ainda capaz de cobrar e definir seu encerramento, quando necessário.
5. Apresentar uma prévia do vínculo e das pendências antes da confirmação.
6. Registrar a assinatura existente na gestão de pagamentos, preservando os
   identificadores e o histórico da tentativa anterior e o período já pago.
7. Registrar operador, data, origem externa, loja, plano e identificadores do
   provedor no histórico da operação, sem armazenar credenciais.
8. Sincronizar a assinatura e verificar que o Pro permanece ativo pelo período
   comprovado, sem gerar checkout ou cobrança adicional.

Se a assinatura externa não puder ser consultada pela integração, registrar a
pendência e manter conferência operacional documentada até resolver o acesso.
Não presumir sincronização automática nem pedir nova assinatura como consequência
automática dessa limitação.

### Proteções necessárias

- Impedir que a mesma assinatura seja vinculada a duas lojas.
- Tornar a repetição da operação segura, sem duplicar registros ou pagamentos.
- Validar provedor, conta recebedora e ambiente de produção.
- Tratar explicitamente eventual assinatura local já existente, sem sobrescrever
  seu histórico silenciosamente.
- Impedir que notificações tardias da Efí ou de tentativas substituídas alterem o
  contrato atual, removam seu acesso ou sejam atribuídas ao Mercado Pago.
- Encaminhar eventual pagamento duplicado para revisão; não fazer estorno automático
  como parte do vínculo.

## Etapa 2: acompanhar o ciclo da assinatura

Exibir no backoffice e, no que for relevante, no painel do lojista:

- Gateway responsável e situação da assinatura.
- Plano, valor, moeda e periodicidade contratados.
- Último pagamento, período pago e próxima cobrança, quando confirmados.
- Situação da renovação automática.
- Última sincronização e pendências de conferência.

Reutilizar e validar notificações e reconciliação periódica para acompanhar
renovações, falhas, cancelamentos, estornos e contestações. Validar especificamente
o comportamento das assinaturas originadas pelo link externo.

Regras propostas:

- Separar a situação da renovação do direito de acesso ao período já pago.
- Desativar a renovação não deve retirar imediatamente o acesso já contratado.
- Falha de cobrança deve seguir uma política explícita de aviso, tolerância e
  suspensão. Conferir a política existente antes de definir ou alterar prazos.
- Falha de comunicação com o gateway, isoladamente, não deve retirar acesso.
- Usar pagamentos confirmados para registrar recebimentos; previsão de renovação
  e assinatura autorizada não comprovam recebimento de uma nova parcela.
- Identificar a origem externa nos relatórios sem classificar o pagamento como
  cortesia ou contar a mudança manual de plano como um segundo recebimento.

## Etapa 3: gerar links alternativos pelo sistema

Adicionar a ação **Gerar link alternativo** no backoffice para os próximos casos:

1. Selecionar loja, plano e gateway alternativo.
2. Conferir tentativas anteriores e impedir cobranças recorrentes duplicadas.
3. Criar uma tentativa com referência própria, associada à loja e ao plano.
4. Gerar o link de contratação e registrar seu histórico.
5. Ativar o plano após confirmação do pagamento, aproveitando o fluxo de cobrança.

Assim, os próximos contratos já nascem vinculados à loja e dispensam a alteração
manual de Free para Pro.

## Casos distintos

Preservar operações com significado próprio:

| Operação | Controle esperado |
| --- | --- |
| Vincular assinatura externa | Contrato recorrente existente, com acompanhamento do provedor. |
| Gerar link alternativo | Nova contratação identificada previamente com loja e plano. |
| Registrar pagamento avulso | Período definido, vencimento e renovação operacional; evolução futura. |
| Conceder cortesia | Benefício com motivo e vigência próprios, separado de receita paga. |

## Critérios de aceite

- [ ] Loja e assinatura do caso inicial identificadas e pagamento consultado no provedor.
- [ ] Tentativa da Efí conferida e risco de cobrança duplicada resolvido.
- [ ] Vínculo registra o contrato existente sem exigir novo pagamento.
- [ ] Pro e período pago preservados após o vínculo e após reconciliação.
- [ ] Vínculo duplicado, associação a outra loja e ambiente incorreto bloqueados.
- [ ] Histórico anterior e auditoria da vinculação preservados.
- [ ] Renovação confirmada atualiza a gestão sem duplicar recebimentos.
- [ ] Eventos repetidos ou antigos não substituem o estado válido do contrato atual.
- [ ] Falha de cobrança e cancelamento respeitam a política de acesso aprovada.
- [ ] Indisponibilidade do gateway não causa retirada indevida de acesso.
- [ ] Notificações e consulta periódica validadas para o contrato externo.
- [ ] Relatórios distinguem pagamento recebido, recorrência prevista e cortesia.
- [ ] Fluxo de link alternativo validado antes de ser considerado concluído.

## Ordem de execução

Primeiro, identificar o contrato e implementar o vínculo da assinatura existente
com conferência e auditoria. Em seguida, validar seu ciclo de cobrança e acesso.
Depois, gerar links alternativos já associados à loja para os próximos clientes.

## Referência

[Notificações de assinaturas do Mercado Pago](https://www.mercadopago.com.br/developers/pt/docs/subscriptions/additional-content/your-integrations/notifications/introduction?scope=prod).
