# Planejamento: Creem no catálogo de planos e nos pagamentos

Data da análise: 06/10/2026. Status: implementação local concluída; ativação externa depende de credenciais e homologação.

Guia operacional: [Configuração da Creem](configuracao-creem.md). O texto abaixo
registra o planejamento de referência; a entrega usa publicação por país, com
vínculo de produto existente para compartilhar um produto entre mercados.

## Objetivo

Adicionar a Creem como gateway de assinaturas enquanto aguardamos a autorização
da Stripe. Pela área de Planos, criar o produto recorrente pela API, persistir
seu identificador e reutilizá-lo nas contratações. Manter a Creem como adaptador
da estrutura multigateway para permitir a escolha futura da Stripe no backoffice.

Recomendação inicial, sujeita à definição comercial: mercados internacionais
em USD, preservando o gateway atual do Brasil. Países e preços exatos ainda
precisam ser definidos antes da ativação. Todas as rotas Creem começam desabilitadas.

## O que existe no projeto

- `PaymentGateway` e `PaymentGatewayRouter`: contrato e roteamento por país/ambiente.
- `PaymentCatalogPrice`: preço e ID remoto por gateway, ambiente, país e plano.
- `PaymentSubscription`, `PaymentAttempt` e `PaymentWebhookEvent`: assinatura,
  tentativas e processamento de eventos.
- `PaymentConfigurationService`: catálogo, validação e regras de roteamento.
- `PaymentSubscriptionService`: checkout, acesso, alterações e conciliação.
- `/backoffice/plans`: versões de nomes, limites e preços de referência.
- `/backoffice/payments`: preços efetivos, IDs remotos e gateway por país.

`PlanAsset.billingExternalId` é uma referência genérica, sem gateway ou ambiente.
O ID da Creem deve ficar no catálogo de pagamentos, evitando misturar códigos
de sandbox, produção ou outros provedores.

## Contrato verificado na Creem

- REST com autenticação server-side pelo header `x-api-key`.
- Produção: `https://api.creem.io/v1`; teste: `https://test-api.creem.io/v1`.
  Dados e credenciais dos dois ambientes são separados.
- Plano recorrente corresponde a um produto: `POST /v1/products` retorna `id`.
- A criação aceita USD e EUR, preço inteiro em centavos, nome, descrição,
  `billing_type=recurring` e `billing_period=every-month`.
- O header `Idempotency-Key` é documentado para criação de produtos.
- Checkout: `POST /v1/checkouts` com `product_id`, `request_id`, `success_url`
  e `metadata`. Retorna o endereço do checkout hospedado.
- `request_id` é documentado como rastreamento; não assumir que elimina
  automaticamente checkouts duplicados. `custom_price` serve apenas para
  produtos de pagamento único, portanto não resolve preço de assinatura.
- Webhooks usam `creem-signature`, HMAC-SHA256 do corpo original.
- A documentação recomenda `subscription.paid` para liberar acesso;
  `subscription.active` serve para sincronização.
- Cancelamento permite `mode=scheduled` ou `mode=immediate`.
- Upgrade entre produtos aceita política de proporcionalidade; não usar o
  comportamento padrão sem explicitar a política comercial.

## Entrega 1: criar e vincular o produto na área de Planos

### Experiência do administrador

Adicionar em `/backoffice/plans` uma seção de cobrança Creem ligada a um plano
pago existente. A criação remota é uma ação própria, após o plano local existir.

Campos: plano, ambiente, mercados de destino, nome, descrição, moeda USD/EUR,
preço mensal e impostos. Sugerir `tax_mode=inclusive`, para o preço exibido
corresponder ao total, e `tax_category=saas`. A decisão fiscal deve aparecer
explicitamente no formulário. Preço não é conversão automática do valor em BRL.

A ação **Criar na Creem** executa:

1. Validar autorização administrativa, token de ação, plano pago e credencial
   do ambiente. Validar preço positivo com duas casas, mínimo de uma unidade
   monetária e campos obrigatórios; converter com `BigDecimal` sem arredondamento silencioso.
2. Persistir uma operação de criação com chave idempotente, parâmetros imutáveis,
   plano, mercados, operador e ambiente antes de chamar a API.
3. Enviar o produto mensal com a mesma `Idempotency-Key` nas retomadas da operação.
4. Salvar imediatamente o `id` retornado, validar o produto com consulta à API
   e vinculá-lo às linhas correspondentes de `PaymentCatalogPrice`.
5. Mostrar código `prod_...`, ambiente, preço e resultado. Permitir copiar o ID
   e consultar/revalidar o produto. Manter a rota do país desabilitada até ativação explícita.

O campo existente `providerPriceId` armazena o `product_id` da Creem. Na interface,
rotulá-lo **Código do produto Creem**. Não é chave de licença ou cupom.
Um produto pode atender vários países com o mesmo preço, moeda e condições;
produtos distintos são usados quando essas condições diferirem.

### Persistência e recuperação

Criar uma tabela de operações de publicação do catálogo com chave única,
hash/parâmetros da solicitação, status (`CREATING`, `UNKNOWN`, `CREATED`,
`LINKED`, `FAILED`), ID remoto, erro sanitizado e datas. Persistência da operação
deve sobreviver ao rollback da etapa de vínculo, com transações curtas.
Bloqueio e restrição única impedem duas publicações simultâneas da mesma seleção.

Se houver timeout, conservar a operação e repetir a solicitação original com a
mesma chave. Se o produto existir e o vínculo local falhar, retomar o vínculo
usando o ID persistido. Consultar as regras de validade da chave na documentação
de erros durante a implementação; não recriar automaticamente após resultado
indeterminado fora dessa validade.

Oferecer também **Vincular produto existente**, consultando a API e validando
ambiente, moeda, valor, recorrência, periodicidade, impostos e status.
O vínculo falho mantém o ID disponível para recuperação, com cobrança bloqueada.

Alterar limites ou criar uma versão local não publica produto automaticamente.
Para mudar o preço de novas contratações, criar outro produto e preservar o
snapshot e ID das assinaturas antigas. A API permite editar produtos, mas o
efeito sobre contratos existentes precisa ser testado antes de oferecer edição financeira.

## Entrega 2: assinatura completa

### Adaptador e configuração

Adicionar `CREEM` a `PaymentProviderType`, `CreemProperties`, cliente HTTP e
`CreemGateway`. Configuração inclui habilitação de novas vendas, ambiente,
API key, webhook secret, timeouts e dias de tolerância. Resolver URL pelo ambiente
e conferir o `mode` das respostas. Credenciais ficam nas configurações do servidor.
Consulta de catálogo pode funcionar antes da ativação de vendas; checkout exige
configuração completa, produto validado e rota habilitada.

Generalizar os pontos do serviço compartilhado que hoje privilegiam Paddle:
`anyGatewayEnabled`, preço por loja, validação de catálogo, identificação do
produto remoto, tentativas pendentes, reutilização de checkout e tolerância.
Preservar regras particulares da Efí e o checkout próprio do Paddle.
Rever guardas de homologação, callbacks, interceptadores, proxy e textos de gateway.

### Checkout e vínculo da assinatura

Resolver país, preço e moeda exclusivamente pelo catálogo do backend. Gravar
tentativa e referência interna antes da chamada. Enviar `product_id`, cliente,
`request_id` e metadados com loja, plano e referência da tentativa.
Guardar ID/URL do checkout e snapshot do preço; abrir o checkout hospedado fora
do iframe da Nuvemshop. O retorno mostra processamento até confirmação server-side.

Registrar cliente e assinatura a partir de consulta/evento verificado, validando
provedor, ambiente, produto e pertencimento à tentativa/loja. Nenhum parâmetro
da URL de retorno libera acesso. Uma tentativa com resultado indeterminado
impede novo checkout até conciliação; não depender de `request_id` como garantia
de idempotência que a documentação não promete.

Assinaturas existentes continuam operadas por seu gateway original.
Mudar a rota do país afeta apenas novas contratações. Desabilitar novas vendas
na Creem deve manter consultas, cancelamentos e webhooks de contratos existentes.

### Eventos, acesso e conciliação

Adicionar `/prod/webhooks/creem` e `/webhooks/creem`, retornando HTTP 200 após
recepção/processamento durável. Verificar assinatura sobre o corpo bruto antes
de persistir. Deduplicar por gateway, ambiente e ID do evento, inclusive sob concorrência.
Tratar eventos fora de ordem usando versão/data do estado e consulta remota,
com reprocessamento das falhas pelo mecanismo existente.

- `checkout.completed`: vincular checkout, cliente e assinatura.
- `subscription.active` e `subscription.update`: sincronizar estado, sem conceder
  acesso apenas pela indicação de assinatura ativa.
- `subscription.paid`: consultar transação paga, validar vínculo, conceder/renovar
  acesso e guardar período, valor efetivo e ID do pagamento.
- `subscription.scheduled_cancel`: preservar acesso até o fim do período pago.
- `subscription.canceled`, `past_due`, `unpaid`, `expired`, `paused` e `trialing`:
  mapear explicitamente para estados locais e política de acesso. Primeiro lançamento
  sem criar trials; ainda assim tratar o estado caso o produto vinculado o tenha.
- `refund.created` e `dispute.created`: registrar efeito financeiro e aplicar
  política definida para estorno total, parcial e contestação, sem renovar acesso.

Consultar assinatura e última transação no job de conciliação, incluindo tentativas
pendentes. Usar período informado pela Creem. Separar preço contratual de
`amount_paid`, impostos, descontos e estornos nos relatórios; não somar USD/EUR a
BRL como se fossem a mesma moeda. GA4 e Discord recebem apenas pagamentos confirmados,
com deduplicação por ID, moeda e valor efetivo.

Cancelamento do lojista agenda o término no fim do período pago, explicitando
`mode=scheduled` e `onExecute=cancel`. Cancelamento imediato tem fluxo administrativo
próprio. Upgrade deve mostrar custo proporcional e só liberar o plano superior
após confirmar a cobrança exigida; homologar comportamento de falha antes de ativá-lo.

## Sequência de implementação e aceite

1. Configuração, cliente, migração de operações e linhas do catálogo Creem,
   inicialmente desabilitadas e sem preços comerciais inventados.
2. Seção na tela de Planos, criação idempotente, vínculo, recuperação e validação.
3. Gateway, checkout hospedado e generalização dos pontos compartilhados.
4. Webhooks, ciclo de acesso, cancelamento, conciliação e relatórios.
5. Homologar em sandbox e então cadastrar produtos próprios de produção e
   ativar os países escolhidos pelo roteamento existente.

Testes necessários: criação correta e retomada após timeout; produto criado com
falha de vínculo; clique concorrente; erro remoto; ID/moeda/ambiente divergentes;
pagamento confirmado antes/depois do retorno; webhook duplicado e fora de ordem;
assinatura inválida; renovação; inadimplência; cancelamento agendado; estorno;
recuperação pelo job; checkout desconhecido; manutenção dos fluxos Efí/Paddle/Mercado Pago.

Aceite principal: criar pela tela de Planos, visualizar o código salvo após
reabrir a página e contratar usando esse mesmo produto. Criar um produto não
habilita sozinho o checkout nem altera contratos existentes.

Pendências comerciais para ativação: países, valores Premium/Premium Plus/Ultra,
USD ou EUR, impostos inclusivos, tolerância de inadimplência, política de
estornos/contestações e proporcionalidade do upgrade. Conta e credenciais de
produção precisam estar operacionais; a integração não garante aprovação do gateway.

## Referências oficiais

- [Introdução e ambientes](https://docs.creem.io/api-reference/introduction)
- [Criar produto e idempotência](https://docs.creem.io/api-reference/endpoint/create-product)
- [Editar produto](https://docs.creem.io/api-reference/endpoint/update-product)
- [Criar checkout](https://docs.creem.io/api-reference/endpoint/create-checkout)
- [Webhooks e liberação de acesso](https://docs.creem.io/code/webhooks)
- [Consultar transação](https://docs.creem.io/api-reference/endpoint/get-transaction)
- [Cancelar assinatura](https://docs.creem.io/api-reference/endpoint/cancel-subscription)
- [Upgrade entre produtos](https://docs.creem.io/api-reference/endpoint/upgrade-subscription)
