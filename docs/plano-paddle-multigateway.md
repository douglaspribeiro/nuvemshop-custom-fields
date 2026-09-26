# Plano de implementação: Paddle e roteamento multigateway

## Objetivo e decisões

Implementar a Paddle para novas assinaturas de Argentina, México e Chile,
preservando a Efí no Brasil. O gateway de novas assinaturas será configurável
por país no backoffice, sem deploy e sem condicionais de país espalhadas pelo
código.

Decisões aprovadas:

- Brasil (`BR`) começa com Efí.
- Argentina (`AR`), México (`MX`) e Chile (`CL`) começam com Paddle.
- País sem regra explícita permanece sem checkout disponível.
- O checkout internacional abre em página própria, fora do iframe da
  Nuvemshop.
- Os preços internacionais incluem os impostos calculados pela Paddle.
- A primeira entrega usa Paddle em sandbox; produção depende da aprovação da
  conta.
- Nesta etapa, aceitamos a cobertura de cartões documentada pela Paddle, sem
  prometer cartões exclusivamente domésticos ou adquirência local.
- Assinaturas existentes permanecem vinculadas ao provider que as criou.
  Alterar o roteamento afeta somente novas contratações e não migra tokens,
  contratos ou identificadores remotos.
- Stripe e Nuvei poderão ser adicionadas posteriormente como novos adaptadores
  e selecionadas pelas mesmas regras do backoffice.

A Paddle documenta cobrança em ARS, MXN e CLP, checkout recorrente e cartões de
crédito/débito. Isso comprova suporte técnico a essas moedas e assinaturas, mas
não garante aprovação de todo cartão emitido localmente em cada país.

Referências oficiais:

- [Moedas suportadas](https://developer.paddle.com/concepts/sell/supported-currencies/)
- [Países suportados](https://developer.paddle.com/concepts/sell/supported-countries-locales/)
- [Cartões](https://developer.paddle.com/concepts/payment-methods/card/)
- [Checkout recorrente](https://developer.paddle.com/concepts/sell/self-serve-checkout/)

## Arquitetura do projeto

O projeto usa Java 25, Spring Boot 3.5, JPA/MySQL e Flyway. Já possui
`PaymentGateway`, `PaymentGatewayRouter`, `PaymentSubscription`, eventos de
webhook e conciliação periódica. Essas estruturas serão evoluídas, sem criar um
segundo domínio de pagamentos.

### Contrato dos gateways

O contrato comum deve representar capacidades e resultados do domínio:

- identificar o provider e verificar se está operacional;
- informar países, moedas e recursos habilitados;
- resolver preço e moeda de um plano;
- criar checkout e consultar tentativa de pagamento;
- consultar assinatura e pagamentos;
- cancelar imediatamente ou no fim do período pago;
- verificar e traduzir webhook para eventos internos.

O contrato não deve obrigar todos os providers a criar uma assinatura por uma
chamada separada. A Paddle cria automaticamente uma assinatura quando o
checkout de um item recorrente é concluído; a Efí mantém seu fluxo atual.

### Roteamento configurável

Criar no banco regras de roteamento com país, provider, estado habilitado,
ambiente e datas de auditoria. O backoffice terá uma área **Pagamentos** para:

- selecionar o gateway de novas assinaturas por país;
- visualizar integrações e ambientes disponíveis;
- configurar preços por plano e mercado e associá-los aos IDs remotos;
- impedir habilitação sem credenciais, moeda, catálogo e recorrência válidos;
- consultar o histórico de mudanças, com operador, data e valores anterior e
  novo.

Separar as capacidades **aceitar novas assinaturas** e **operar assinaturas
existentes**. Retirar a Paddle de um país não pode desativar webhooks, consultas
ou cancelamentos das assinaturas Paddle já criadas.

Credenciais ficam em variáveis de ambiente ou secret manager. O painel não
recebe nem exibe segredos.

## Catálogo e preços

Mover a tabela internacional para um catálogo independente do billing nativo
da Nuvemshop. Preservar os valores brasileiros usados pela Efí.

| Mercado | Moeda | Essencial/mês | Pro/mês |
| --- | --- | ---: | ---: |
| Brasil | BRL | 19,99 | 29,99 |
| Argentina | ARS | 5.599,00 | 8.399,00 |
| México | MXN | 99,00 | 149,00 |
| Chile | CLP | 4.199 | 6.299 |

Na Paddle, criar produtos/preços mensais com preços específicos por país e
`tax_mode=internal`. Validar o catálogo remoto antes de habilitar o mercado.
Valores enviados à API usam a menor unidade monetária: ARS e MXN têm duas casas;
CLP não tem casas decimais.

Referências:

- [Criação de produtos e preços](https://developer.paddle.com/build/products/create-products-prices/)
- [Preços localizados](https://developer.paddle.com/build/products/offer-localized-pricing/)

## Checkout e ciclo da assinatura

Implementar `PaddleGateway` usando Paddle Billing e Paddle.js, com credenciais e
URLs separadas entre sandbox e produção.

Fluxo inicial:

1. O backend carrega a loja autenticada e resolve país, preço, moeda e provider.
2. Grava uma tentativa interna idempotente.
3. Cria uma transação automática com `POST /transactions`, item recorrente,
   quantidade um e referência interna em `custom_data`.
4. Abre uma página externa do app com Paddle Checkout para a transação.
5. A Paddle coleta e armazena os dados do cartão, executa 3DS quando necessário,
   conclui o pagamento e cria a assinatura.
6. O webhook confirmado atualiza a assinatura e o acesso; o retorno do navegador
   apenas mostra o andamento.

O checkout Paddle não pede CPF brasileiro. A página externa usa um token opaco,
temporário e vinculado à tentativa, sem conceder uma sessão administrativa fora
do iframe. Recarregar reutiliza a tentativa em aberto. Um resultado remoto
incerto não cria outra transação automaticamente.

Referência: [criação de transação](https://developer.paddle.com/api-reference/transactions/create-transaction/).

### Persistência

Reutilizar `PaymentSubscription` e acrescentar, quando ainda ausentes:

- provider customer ID;
- provider price ID;
- país e ambiente;
- início e fim do período corrente;
- próxima cobrança;
- cancelamento solicitado e efetivo.

Criar histórico de tentativas/pagamentos. IDs de cliente, transação, assinatura
e pagamento serão consultados junto com provider e ambiente, evitando colisão
ou associação de evento antigo à assinatura atual.

Guardar somente identificadores e metadados operacionais. Número completo do
cartão e CVV nunca passam pelo backend nem são persistidos.

### Renovação, falha e cancelamento

- A Paddle executa as renovações e a recuperação de pagamentos.
- O aplicativo mantém seu estado de acesso e usa conciliação como recuperação.
- `GET /subscriptions/{subscription_id}` consulta o contrato remoto.
- O cancelamento normal usa `POST /subscriptions/{id}/cancel` com
  `effective_from=next_billing_period` e mantém o acesso até o período pago.
- A desinstalação solicita cancelamento imediato de cobranças futuras.
- A atualização do cartão usa a transação oficial de atualização de método.
- Adotar inicialmente três dias de tolerância após falha de renovação,
  configuráveis por provider; depois disso, o acesso volta ao Free enquanto a
  Paddle pode continuar sua recuperação financeira.

Referências:

- [Cancelar assinatura](https://developer.paddle.com/build/subscriptions/cancel-subscriptions/)
- [Atualizar pagamento](https://developer.paddle.com/build/subscriptions/update-payment-details/)
- [Recuperação de pagamentos](https://developer.paddle.com/concepts/retain/payment-recovery-dunning/)

## Webhooks e consistência

Adicionar `POST /prod/webhooks/paddle7`, com alias `/webhooks/paddle7`.

Na recepção:

1. Ler o corpo HTTP original.
2. Validar `Paddle-Signature` usando timestamp, HMAC-SHA256 e segredo do destino.
3. Persistir o evento autenticado de forma idempotente.
4. Responder HTTP 200 dentro dos cinco segundos pedidos pela Paddle.
5. Processar o evento posteriormente por um worker durável no banco.

Armazenar `event_id`, `notification_id`, `occurred_at`, tipo, provider,
ambiente, payload necessário, tentativas, status e erro. O worker deve usar
bloqueio entre instâncias e retentativas. A ordem de entrega não é garantida;
um evento antigo não pode desfazer cancelamento, regredir um pagamento aprovado
ou conceder acesso duas vezes.

Eventos iniciais:

- `transaction.completed`;
- `transaction.payment_failed`;
- `transaction.past_due`;
- `transaction.canceled`;
- `subscription.created`;
- `subscription.updated`;
- `subscription.activated`;
- `subscription.past_due`;
- `subscription.paused`;
- `subscription.resumed`;
- `subscription.canceled`.

A ativação exige `transaction.completed` compatível com a contratação. Um
evento de assinatura isolado não comprova pagamento. Webhooks e conciliação não
dependem de chamadas síncronas para responder à Paddle. A outbox atual do
Discord será reutilizada com provider, pagamento, moeda e valor efetivamente
confirmados.

Referências:

- [Validação de assinatura](https://developer.paddle.com/webhooks/about/signature-verification/)
- [Entrega, prazo e ordenação](https://developer.paddle.com/webhooks/about/respond-to-webhooks/)

## Validação e lançamento

Cobrir com testes:

- Efí no Brasil sem regressão;
- países sem rota bloqueados;
- troca do gateway no backoffice afetando apenas novas contratações;
- assinaturas antigas operadas pelo provider original;
- conversão de unidades ARS, MXN e CLP e impostos inclusos;
- aprovação, recusa, abandono, 3DS e retorno do checkout;
- webhooks adulterados, duplicados, simultâneos e fora de ordem;
- timeout de criação sem duplicação de cobrança;
- renovação, inadimplência, recuperação e fim da tolerância;
- cancelamento no fim do período, imediato e por desinstalação;
- migrações sobre banco que já contém assinaturas Efí.

Configuração mínima futura: ambiente Paddle, API key, client-side token,
segredo do webhook, catálogo e domínio de checkout aprovado. Documentar a
criação do catálogo, os eventos habilitados e o reprocessamento de falhas.

Liberar primeiro no sandbox. Produção depende da aprovação da conta Paddle e de
testes por mercado. Cartões de teste comprovam o fluxo técnico, não a cobertura
real de cartões domésticos.

## Fora desta implementação

- integração Nuvei;
- integração Stripe;
- migração automática de contratos entre providers;
- garantia de adquirência local ou aceitação de todo cartão doméstico;
- campanha de reconquista após desinstalação, documentada separadamente no
  README.
