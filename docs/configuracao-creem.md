# Configurar a Creem

A integração usa o catálogo e o roteamento existentes. Nenhum país é ativado por
migração e nenhum produto é publicado automaticamente ao subir a aplicação.

## Configuração do servidor

```dotenv
CREEM_ENABLED=false
CREEM_SANDBOX=true
CREEM_API_KEY=chave_do_ambiente_de_teste
CREEM_WEBHOOK_SECRET=segredo_do_webhook_de_teste
CREEM_GRACE_DAYS=3
CREEM_TIMEOUT_SECONDS=20
```

`CREEM_ENABLED` controla novas vendas. Chave e segredo continuam necessários
para operar contratos existentes mesmo com novas vendas desabilitadas. Para
apenas criar/vincular produtos pela área de Planos, basta a chave de API.
A URL da API é escolhida pelo ambiente; não há URL arbitrária configurável.
O perfil `local-homolog` continua exclusivo para pagamentos Efí sandbox.

Com `CREEM_SANDBOX=true`, apenas lojas cujo ID conste explicitamente em
`WINBACK_ALLOWED_STORE_IDS` podem iniciar compras ou upgrades Creem. A mesma
lista é usada mesmo quando a reconquista está desabilitada. Lista vazia ou `0`
bloqueia todas as compras Creem em sandbox. Em produção essa restrição não se
aplica. Consultas, webhooks e cancelamentos de contratos existentes continuam
funcionando para permitir a conciliação dos pagamentos.

## Publicar os produtos pela área de Planos

1. Entre no backoffice e abra **Planos → Creem → Cobrança dos planos**.
2. Escolha um plano pago, país, nome e descrição que aparecerão no checkout.
3. Informe o preço próprio em USD ou EUR e o modo de impostos. Não converta
   automaticamente o preço em BRL. O primeiro lançamento usa recorrência mensal.
4. Clique em **Criar na Creem e salvar código**. O código retornado (`prod_...`)
   fica em **Códigos salvos** e no catálogo de **Pagamentos**.
5. Se houver falha, use **Retomar publicação**. A solicitação repete a mesma
   chave idempotente. Uma publicação pendente bloqueia outra com parâmetros
   diferentes para o mesmo plano/país/ambiente.
6. Para compartilhar o mesmo produto com outro país, use **Vincular produto
   existente**. Valor, moeda, imposto, ambiente e assinatura mensal são validados
   pela API. Produtos com trial, cobrança por uso ou pagamento único são rejeitados.

Após resultado indeterminado com mais de 20 horas, não há repetição automática:
consulte os produtos no painel Creem e vincule o produto existente. Esse limite
local é conservador; não representa uma promessa de validade da chave pela Creem.

A publicação salva o ID e mantém o preço desabilitado. Publicar outro preço
substitui o vínculo de novas contratações e requer habilitação novamente;
contratos antigos preservam seu produto, moeda e valor. Limites e versões locais
não são publicados automaticamente.

## Habilitar checkout em sandbox

1. Cadastre o webhook para a URL pública HTTPS `/prod/webhooks/creem`.
   O alias `/webhooks/creem` atende proxies que removem `/prod`.
2. Inscreva `checkout.completed`, `subscription.active`, `subscription.paid`,
   `subscription.update`, `subscription.scheduled_cancel`, `subscription.canceled`,
   `subscription.past_due`, `subscription.unpaid`, `subscription.expired`,
   `subscription.trialing`, `subscription.paused`, `refund.created` e `dispute.created`.
3. Configure o segredo do webhook no servidor e `CREEM_ENABLED=true`.
4. Em **Pagamentos**, valide/habilite os produtos Essencial e Pro do país. Ultra
   é opcional. Todos os planos de um país devem usar a mesma moeda.
5. Habilite a rota desse país com gateway **CREEM** e ambiente **SANDBOX**.
6. Faça uma compra de teste, uma renovação e um cancelamento. Confirme o vínculo
   da loja, o período pago e o ID da transação no banco e no painel Creem.

O checkout abre na página hospedada da Creem, fora do iframe. O retorno ao app
não concede acesso: consulta ao servidor e pagamento confirmado concedem acesso.
Uma tentativa sem resposta permanece bloqueada para evitar outra cobrança.
Ao abrir os planos ou clicar em **Assinar**, o app trata a pendência anterior
automaticamente, sem pedir que o lojista escolha entre retomar e recomeçar.
Contratações Paddle abandonadas há pelo menos 30 minutos são verificadas e
canceladas na API antes de liberar outra tentativa, inclusive em outro gateway.
Uma tentativa Paddle sandbox expirada, sem assinatura nem pagamento confirmado,
pode ser encerrada localmente se as credenciais antigas não estiverem disponíveis
ou se a Paddle tiver mudado de ambiente. A transação remota de teste é preservada
para auditoria; essa exceção nunca se aplica a compras de produção.
Na Creem, um checkout ainda aberto é reutilizado; um checkout confirmado como
expirado libera nova contratação. O tempo local sozinho não invalida um link
que ainda pode receber pagamento no gateway.
Recupere-a pelo webhook (inclusive reenvio pelo painel Creem); se o ID do checkout
já estiver salvo, a consulta no retorno/job também concilia. Checkouts com
resultado desconhecido não são recriados automaticamente.

O cancelamento do lojista agenda o término para o final do período pago. Uma
assinatura cancelada imediatamente perde acesso. Inadimplência usa os dias de
tolerância configurados; estados `unpaid`, `expired` e `paused` retiram acesso.
Estorno integral ou contestação confirmada na última transação retiram acesso;
estorno parcial preserva acesso. Eventos financeiros ficam registrados na fila
persistida. Notificações e GA4 usam o valor efetivamente pago, por moeda.

O upgrade solicita cobrança proporcional imediata na Creem. A tela informa esse
comportamento; o plano superior é liberado após uma nova transação paga. A
solicitação é persistida antes da chamada e não é repetida quando o resultado
for desconhecido. Webhook/job retomam a confirmação; o suporte revisa alterações
que permaneçam pendentes. A API calcula o valor proporcional.

## Notificações no Discord

A Creem usa o mesmo `DISCORD_PAYMENT_WEBHOOK_URL` já utilizado pela Efí. Se as
notificações da Efí estão configuradas, não é necessário cadastrar outro webhook
do Discord. Pagamentos confirmados de novas assinaturas e renovações informam
loja, plano, valor efetivamente pago, moeda, gateway e código da transação.

Upgrades confirmados informam também plano anterior, novo plano, valor do ajuste
proporcional, nova mensalidade e assinatura mantida. Eventos repetidos são
deduplicados por gateway/transação. A fila persistida tenta novamente quando o
Discord falha. Criar um produto ou receber apenas `subscription.active` não
gera notificação de pagamento.

## Pendências

- Homologar criação de produtos, compra, renovação, upgrade e cancelamento com
  credenciais reais do sandbox; depois configurar e ativar produção.
- Criar ações administrativas para recuperar manualmente checkouts de resultado
  desconhecido e upgrades pendentes. A recuperação automática por webhook e,
  quando os identificadores estão disponíveis, por consulta/job já existe.
- Expor o cancelamento imediato no backoffice; o adaptador e o tratamento da
  perda de acesso estão implementados, mas não há uma ação administrativa.
- Mostrar uma estimativa prévia do ajuste proporcional na tela de upgrade;
  atualmente a tela explica a cobrança e a Creem calcula o valor.

## Produção

Use `CREEM_SANDBOX=false`, chave e segredo de produção. Cadastre produtos novos
nesse ambiente e valide/habilite as rotas de produção. IDs e credenciais do teste
não funcionam na produção. A conta Creem precisa estar operacional para pagamentos
reais; não foi realizada compra real ou publicação remota durante a implementação.

Uma instância opera apenas um ambiente Creem por vez. Para manter uma homologação
ativa junto da produção, use instâncias e bancos separados. Trocar o gateway do
país altera novas contratações; assinaturas antigas continuam na Creem enquanto
suas credenciais estiverem configuradas.

Referências: [API](https://docs.creem.io/api-reference/introduction),
[produtos](https://docs.creem.io/api-reference/endpoint/create-product),
[webhooks](https://docs.creem.io/code/webhooks),
[cancelamento](https://docs.creem.io/api-reference/endpoint/cancel-subscription),
[upgrade](https://docs.creem.io/api-reference/endpoint/upgrade-subscription).
