# dLocal Go — assinaturas da Argentina

Implementação da API **dLocal Go**, separada da API empresarial dLocal. Restrita a
lojas AR e cobrança mensal em ARS. Desabilitada por padrão. Não altera a rota
existente da Argentina, assinaturas anteriores ou credenciais de outros gateways.

## Configuração

Variáveis (valores secretos devem ficar somente no ambiente):

| Variável | Padrão | Uso |
| --- | --- | --- |
| `DLOCAL_GO_ENABLED` | `false` | Aceitar novas contratações |
| `DLOCAL_GO_SANDBOX` | `true` | Seleciona API e credenciais de teste |
| `DLOCAL_GO_API_KEY` | vazio | API Key do ambiente selecionado |
| `DLOCAL_GO_SECRET_KEY` | vazio | Secret Key do mesmo ambiente |
| `DLOCAL_GO_ALLOWED_STORE_IDS` | vazio | IDs separados por vírgula; obrigatório no sandbox |
| `DLOCAL_GO_TIMEOUT_SECONDS` | `20` | Timeout, limitado a 60 segundos |

Sandbox: `https://api-sbx.dlocalgo.com`. Produção: `https://api.dlocalgo.com`.
Desabilitar novas contratações mantém a conciliação e o cancelamento dos contratos
existentes enquanto suas credenciais continuarem presentes. Não mude o ambiente
com contratos pendentes/ativos: uma instalação opera apenas um ambiente por vez.

1. Crie sua conta de teste em <https://dashboard-sbx.dlocalgo.com/signup> e obtenha
   API Key e Secret Key em Integrações → Integração API.
2. Configure as variáveis, incluindo uma loja de teste AR autorizada.
3. No painel dLocal Go, crie planos de referência AR/ARS, mensais, sem teste grátis,
   limite de períodos ou dia fixo. Eles não devem ser compartilhados com clientes.
4. Em `/backoffice/payments`, filtre `DLOCAL_GO` e o ambiente. A migração V61
   cadastra Essencial, Pro e Ultra desabilitados, com os preços ARS já existentes
   no catálogo Stripe (5599 / 8399 / 16799). Confirme esses valores antes de usar.
   Informe o ID numérico de cada plano remoto e clique em validar e salvar.
5. Habilite o gateway e configure a rota AR como `DLOCAL_GO`, no mesmo ambiente.
   A rota exige Essencial e Pro validados; Ultra é opcional.
6. Valide o roteiro abaixo antes de usar credenciais de produção.

## Checkout e identificação

Cada tentativa tem reserva local persistida antes da chamada HTTP. A criação
remota usa um **plano exclusivo**, com a referência da tentativa no nome e no URL
de notificação. O plano de referência do catálogo só valida valor/recorrência;
seu link não é usado no checkout de clientes.

Isso é necessário porque `external_id` está documentado como parâmetro devolvido
no redirecionamento, não como atributo consultável da assinatura. Email ou retorno
do navegador não comprovam pagamento nem são usados para vincular a loja.

O link exclusivo continua sendo um link de assinatura: **não o compartilhe**.
A API documenta vários assinantes por plano. Se um link acabar com mais de um
assinante, a conciliação para e exige intervenção; não escolhe um pagador
arbitrariamente. O operador deve cancelar contratos extras e avaliar reembolso
no provedor antes de retomar a conciliação. Validar a prevenção de reutilização
com o provedor é requisito da homologação.

O ID de assinatura interno é `planId:subscriptionId`; o de execução é
`planId:subscriptionId:executionId`. A consulta lista todas as páginas, com limite
de 100 páginas para evitar varredura infinita. Respostas incompletas falham.

Se a criação remota tiver resposta indeterminada, a tentativa fica `UNKNOWN` e
bloqueia uma nova cobrança. Não existe chave de idempotência documentada nesse
endpoint. Localize o plano no painel usando o nome `ncf_<loja>_<referência>` e
concilie com suporte antes de liberar outra tentativa; não repita o POST.

## Notificações e acesso

Endpoint gerado automaticamente para cada tentativa:
`/webhooks/dlocal-go?reference=<referência>`. O alias
`/prod/webhooks/dlocal-go` também é aceito. Garanta encaminhamento do cabeçalho
`Authorization`, corpo original e query string pelo proxy.

A assinatura do cabeçalho `V2-HMAC-SHA256, Signature: ...` é validada com
HMAC-SHA256 de `API_KEY + corpo original`, usando Secret Key e comparação em tempo
constante. O evento persiste apenas identificadores; o corpo com dados do pagador
não é armazenado. O processamento em fila consulta novamente a API autenticada.
Como a documentação não fornece ID único de evento e o mesmo `payment_id` pode
mudar de status, cada entrega assinada gera trabalho de conciliação. Reentregas
não criam novas cobranças; a identidade do pagamento permanece `order_id` e a
outbox existente deduplica notificações comerciais.

Só uma execução `COMPLETED`, com assinatura, moeda e valor correspondentes,
libera acesso. `CONFIRMED` sozinho não libera. O período pago é um mês civil
calculado a partir de `created_at` da execução concluída, nunca da data de uma
nova tentativa. Datas sem offset nos exemplos da documentação são interpretadas
como UTC; confirme essa convenção e o calendário de cobrança no sandbox.
Uma renovação recusada suspende acesso sem tolerância adicional. Cancelamento
preserva o intervalo já pago e fecha também o plano exclusivo para impedir novas
contratações pelo link antigo. Checkout sem assinante expira após 30 minutos,
com consulta posterior ao fechamento para capturar pagamentos concorrentes.

## Upgrade proporcional

O botão de upgrade existente direciona apenas assinaturas `DLOCAL_GO` ao fluxo
`/admin/billing/upgrade/dlocal-go`. Nenhum serviço de upgrade dos outros
provedores foi alterado. As operações próprias ficam em `dlocal_go_operations`.

A cotação em ARS registra plano atual, alvo, valores, pagamento mensal confirmado
e início/fim do ciclo. O ajuste é a diferença de mensalidades multiplicada pela
fração de tempo restante, com duas casas decimais. A cotação vale até 15 minutos,
limitada pelo fim do ciclo. A confirmação cria uma cobrança **avulsa**, com
`order_id` exclusivo, e encaminha ao checkout hospedado. A intenção é persistida
antes do POST; timeout nunca repete a criação automaticamente.

A próxima mensalidade só é atualizada depois do ajuste pago e não estornado.
É usado `PATCH /v1/subscription/plan/:planId` no plano exclusivo daquela loja,
com valor absoluto, conferência do nome, único assinante e verificação posterior.
Isso evita o endpoint `change-plan`, que cobra imediatamente uma mensalidade
inteira segundo a documentação. A data do ciclo deve permanecer a mesma. A
mensalidade original + o ajuste pago comprovam o acesso ao plano superior até a
próxima renovação. Não existe uma segunda assinatura.

Reenvio de formulário reutiliza a operação; pagamento pendente/recusado não
libera o plano superior. Um POST indeterminado é recuperado por consulta ao
`order_id`, sem repetir cobrança. Mudança de ciclo ou contrato após o pagamento
mantém a operação aguardando conciliação, sem cobrar novamente. Há no máximo um
upgrade pago por ciclo. Cotação de upgrade não é oferecida enquanto houver
reembolso em processamento.

## Reembolsos e chargebacks

O backoffice possui `/backoffice/payments/dlocal-go/refunds`, com cotação e
confirmação separadas. Informe a loja, a execução `plano:assinatura:execução` ou
`upgrade:ID-da-operação`, e o valor em ARS. O servidor confere provedor, ambiente,
assinatura, pedido, moeda, valor original, reembolsos confirmados e reservas
pendentes. Nunca envia mais do que o saldo reembolsável.

A reserva é persistida antes de `POST /v1/refunds`. Reenvio da confirmação não
repete o POST. Resultado indeterminado permanece bloqueado em `CREATING`; como
o endpoint não documenta idempotência ou referência externa de reembolso, não
se associa arbitrariamente um reembolso antigo do mesmo valor. O operador deve
conferir a operação no provedor antes de nova solicitação.

Reembolsos com ID remoto são consultados até `SUCCESS`, `REJECTED` ou `CANCELLED`.
As APIs de reembolsos e chargebacks são paginadas separadamente, a partir de zero,
e conciliadas pelo `order_id` exato; a execução `COMPLETED` isolada nunca prova
que o valor não foi devolvido. Observações persistem em `dlocal_go_reversals`:
um reembolso confirmado continua válido mesmo se uma consulta posterior retornar
uma página atrasada. O corpo do webhook não altera diretamente os valores.

- Reembolso total da mensalidade vigente suspende o acesso.
- Reembolso parcial preserva o período pago.
- Reembolso de ciclo antigo não suspende uma mensalidade posterior paga.
- Chargeback pendente ou confirmado suspende acesso; resolução explícita
  `REJECTED`/`CANCELLED` permite restauração com pagamento e período ainda válidos.
- Estorno total/chargeback do ajuste no ciclo atual restaura o plano e a
  mensalidade anteriores; não altera um ciclo posterior já pago. Resolução
  favorável de chargeback permite reaplicar o upgrade no mesmo ciclo.

Os fluxos novos exigem dLocal Go/AR/ARS e ambiente correspondente. Eles não
oferecem reembolso de cobranças de Efí, Mercado Pago, Creem, Stripe ou Dodo e não
mudam suas regras de valor, moeda, imposto ou prorrateio.

## Homologação obrigatória antes da ativação real

- Confirmar que a conta de pessoa física brasileira pode cobrar assinaturas
  internacionais de software AR/ARS e sacar em conta bancária do próprio CPF.
- Confirmar tarifas, câmbio, prazos e meios argentinos elegíveis à recorrência.
- Concluir checkout, abandonar outro e testar recusa. Verificar que apenas o
  pagamento confirmado libera o plano e encerra eventual Premium Cortesia.
- Receber webhook assinado, reentregar e entregar atrasado; conferir a mesma
  identidade de pagamento, sem reativação com execução antiga vencida.
- Validar renovação aprovada/recusada, paginação, calendário e horário das datas.
- Cancelar e confirmar no provedor que a assinatura e seu plano exclusivo foram
  desativados, preservando localmente apenas o período pago.
- Testar reutilização do link e conciliação de assinatura duplicada com suporte.
- Confirmar que o PATCH de valor do plano exclusivo altera a próxima mensalidade
  sem cobrança imediata e sem deslocar o ciclo; validar o resumo do upgrade,
  checkout do ajuste, renovação, recusa e reentrega de eventos.
- Validar notificações específicas de assinaturas, reembolsos e chargebacks,
  estorno total/parcial/antigo e do ajuste, com recuperação de disputas resolvidas.

Testes locais com HTTP simulado não confirmam habilitação comercial nem operação
real da conta. Não houve deploy, criação remota de planos ou cobranças reais.

## Documentação oficial consultada

- <https://docs.dlocalgo.com/integration-api/welcome-to-dlocal-go-api>
- <https://docs.dlocalgo.com/integration-api/welcome-to-dlocal-go-api/authentication>
- <https://docs.dlocalgo.com/integration-api/welcome-to-dlocal-go-api/subscriptions>
- <https://docs.dlocalgo.com/integration-api/welcome-to-dlocal-go-api/subscriptions/create-a-plan>
- <https://docs.dlocalgo.com/integration-api/welcome-to-dlocal-go-api/subscriptions/retrieve-all-subscriptions-by-plan>
- <https://docs.dlocalgo.com/integration-api/welcome-to-dlocal-go-api/subscriptions/retrieve-all-the-executions-for-a-subscription>
- <https://docs.dlocalgo.com/integration-api/welcome-to-dlocal-go-api/subscriptions/cancel-subscription>
- <https://docs.dlocalgo.com/integration-api/welcome-to-dlocal-go-api/subscriptions/cancel-plan>
- <https://docs.dlocalgo.com/integration-api/welcome-to-dlocal-go-api/payments/notifications>

- <https://docs.dlocalgo.com/integration-api/welcome-to-dlocal-go-api/subscriptions/update-a-plan>
- <https://docs.dlocalgo.com/integration-api/welcome-to-dlocal-go-api/subscriptions/modify-a-subscribers-plan>
- <https://docs.dlocalgo.com/integration-api/welcome-to-dlocal-go-api/payments/create-a-refund>
- <https://docs.dlocalgo.com/integration-api/welcome-to-dlocal-go-api/payments/retrieve-a-refund>
- <https://docs.dlocalgo.com/integration-api/welcome-to-dlocal-go-api/payments/retrieve-refunds>
- <https://docs.dlocalgo.com/integration-api/welcome-to-dlocal-go-api/payments/retrieve-chargebacks>
