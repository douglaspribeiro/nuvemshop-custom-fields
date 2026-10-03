# Configuração da cobrança recorrente Efí

O formulário de pagamento coleta nome, CPF, e-mail, telefone, nascimento e
cartão. O número e o CVV não têm `name` no formulário e são tokenizados no
navegador pela biblioteca oficial da Efí. O backend recebe apenas o token e os
dados do pagador. Endereço de cobrança não é solicitado.

## Preparação da conta

1. Habilitar a API de Emissão de Cobranças e o recebimento por cartão na conta
   Efí. A Efí informa que a análise de habilitação para cartão pode levar até
   cinco dias úteis.
2. Criar uma aplicação e obter `client_id` e `client_secret` do ambiente correto.
   Para a API de Cobranças, a SDK usa essas credenciais; o certificado é
   necessário para outras APIs, como Pix.
3. Obter o Identificador de conta (`payee_code`) em **API > Introdução**.
4. Criar dois planos mensais (`POST /v1/plan`, `interval: 1`, sem limite de
   repetições) e guardar seus `plan_id`. O valor é informado na assinatura,
   portanto os planos não precisam ter preço cadastrado.
5. Configurar o ambiente:

| Variável | Uso |
| --- | --- |
| `EFI_ENABLED` | `true` para oferecer a Efí; padrão `false` |
| `EFI_SANDBOX` | `true` em homologação, `false` em produção |
| `EFI_CLIENT_ID`, `EFI_CLIENT_SECRET` | credenciais da aplicação Efí |
| `EFI_PAYEE_CODE` | identificador de conta usado na tokenização |
| `EFI_PREMIUM_PLAN_ID`, `EFI_PREMIUM_PLUS_PLAN_ID` | IDs dos planos mensais |
| `EFI_PREMIUM_AMOUNT`, `EFI_PREMIUM_PLUS_AMOUNT` | preços mensais em BRL |
| `EFI_PREMIUM_ULTRA_PLAN_ID` | ID do plano mensal Ultra (também configurável no catálogo do backoffice) |
| `EFI_PREMIUM_ULTRA_AMOUNT` | mensalidade Ultra; padrão R$ 59,90 |

O upgrade altera a mesma assinatura por `updateSubscription`, enviando o novo
`plan_id` e os itens com a nova mensalidade. O app consulta a assinatura para
confirmar ID, plano, valor e moeda antes de liberar os limites superiores.
O endpoint `/subscriptions/subscription/{id}/change-plan`, com `mode: direct`,
pertence ao Efipay colombiano; não é a API da Efí brasileira utilizada aqui.

### Ajuste proporcional no upgrade

O sistema calcula o restante do ciclo mensal usando a data de criação da mensalidade
confirmada como paga e a próxima execução na Efí (dias corridos no fuso São Paulo).
Não usa um mês fixo de 30 dias. O valor pago precisa corresponder ao contrato local;
assinaturas atrasadas, em cancelamento ou com desconto de reconquista pendente não
são elegíveis. Um segundo upgrade no mesmo ciclo é bloqueado para não duplicar créditos.

O cupom cadastrado aplica seu percentual ao valor mensal do destino antes do cálculo
proporcional, somente no período restante. O crédito do plano atual é abatido depois.
Arredondamento em centavos, ajuste mínimo zero; não há transferência de saldo excedente.

Em **Backoffice → Pagamentos → Gerenciar cupons de upgrade**, crie, edite, desative ou
exclua códigos. Configure percentual (0,01 a 100%), plano de destino, ambiente, início/fim
em horário de São Paulo, limite total opcional e limite por loja. Cupons novos começam
inativos. `BRINDE` não é fixo nem criado pela migration V43: só funciona se cadastrado
e ativado, como qualquer outro código. A variável `UPGRADE_BRINDE_ENABLED` não é mais usada.

A prévia não consome utilizações. Ao confirmar o pagamento, o app revalida o cupom e
reserva uma vaga com bloqueio transacional para não ultrapassar limites entre lojas.
O percentual fica registrado no ajuste; mudanças no cupom antes do pagamento exigem
uma nova prévia. Pagamento incerto ou em revisão mantém a vaga reservada; recusa
confirmada antes de qualquer pagamento aprovado libera a vaga. Upgrade concluído
confirma a utilização. Cupons com histórico são apenas desativados ao excluir.
A exclusão definitiva da loja elimina suas utilizações identificáveis, mantendo o
total agregado do cupom para não reabrir vagas de descontos já concedidos.

O ajuste cria uma transação avulsa (`createCharge` + `definePayMethod`), não uma
assinatura. O cartão é tokenizado novamente apenas para esse ajuste e não substitui
o cartão recorrente. Não são salvos números, CVV nem token. Depois de confirmar o
status `paid` e validar referência/valor/ID, o sistema altera a assinatura existente.
A intenção financeira é persistida antes da API: reenvios do formulário, callbacks
e retentativas não criam outra cobrança. Respostas incertas são consultadas novamente;
a criação incerta é procurada pelo `custom_id`. Nunca se repete o pagamento incerto.

A migration V40 é automática. O proxy deve encaminhar **POST
`/prod/webhooks/efi-upgrades`** (ou `/webhooks/efi-upgrades` se remover `/prod`) para
a aplicação. Esse callback confirma a cobrança avulsa; o callback antigo das
assinaturas continua ativo. A conciliação periódica roda a cada 60 segundos e pode
ser ajustada com `UPGRADE_RECONCILE_DELAY_MS`.

O lojista acompanha o andamento e histórico em Planos e cobrança; o backoffice tem
**Pagamentos → Acompanhar ajustes de upgrade**. Se um ajuste pago perder elegibilidade
(por exemplo, após encerrar o ciclo) ou houver divergência de dados, requer conferência
humana, sem nova cobrança automática. Não há estorno automático nesta versão.

As variáveis são independentes por ambiente. Nunca versionar credenciais.
`APP_BASE_URL` deve ser a origem HTTPS pública, sem barra final nem `/prod`.
Na homologação atual, use `https://campos-personalizados.wzhub.pro`. O app
registra a notificação Efí em `https://campos-personalizados.wzhub.pro/prod/webhooks/efi3`.
O proxy pode encaminhar esse caminho com ou sem o prefixo `/prod`; ambas as
rotas são aceitas. As rotas antigas `/prod/webhooks/efi` e `/webhooks/efi`
continuam disponíveis para assinaturas já criadas.

## Fluxo e ativação

O backend cria uma assinatura Efí vinculada ao plano, depois define cartão e
pagador com `payment_token`. A assinatura local permanece pendente até que a
primeira cobrança seja aprovada. O callback recebe apenas um token de
notificação; o app consulta esse token na SDK, reconcilia a assinatura e
responde HTTP 200 à Efí. A consulta ao token é necessária para que a Efí
considere a notificação recebida. Há também conciliação periódica e uma
conferência ao abrir o painel de planos quando a assinatura Efí está pendente.
Em produção, testar aprovação, recusa,
notificação duplicada, renovação não paga e cancelamento.

Se houver assinaturas ativas no Mercado Pago, manter suas credenciais e webhook
até o cancelamento dessas assinaturas. Os cartões não são transferíveis; o
lojista precisa assinar novamente na Efí. A preferência por Efí vale para
novas assinaturas quando a configuração estiver completa.

Na migração, um checkout Mercado Pago que ficou pendente apenas com o ID do
plano (sem ID de assinatura) pode ser substituído localmente por uma nova
tentativa na Efí mesmo com o gateway antigo desabilitado. O plano remoto antigo
não é cancelado nesse caso; o ID é registrado no log para auditoria. Se já
existir ID de assinatura Mercado Pago, a migração é bloqueada até conferência
e cancelamento no provedor para evitar cobrança duplicada.

## Referências

- [Assinaturas Efí](https://dev.efipay.com.br/docs/api-cobrancas/assinatura/)
- [Tokenização de cartão](https://dev.efipay.com.br/docs/api-cobrancas/cartao/)
- [Notificações](https://dev.efipay.com.br/docs/api-cobrancas/notificacoes/)
- [SDK Java oficial](https://github.com/efipay/sdk-java-apis-efi)
- [Logo Efí usado no formulário](https://github.com/efipay/js-payment-token-efi/blob/main/assets/img/logo-ef.svg)

## Consultar IDs dos planos

Em **Backoffice → Pagamentos → Consultar planos da Efí e seus IDs**, o sistema consulta
`GET /v1/plans` usando o ambiente e as credenciais Efí configurados. A página exibe
ID, nome, intervalo em meses, repetições e criação, com paginação de 50 registros.
É somente leitura e não exige que os IDs já estejam cadastrados no catálogo local.
Copie o `plan_id` do Ultra para o catálogo do mesmo ambiente. Planos criados no painel
que não forem retornados devem ter sua disponibilidade na API confirmada com a Efí.
