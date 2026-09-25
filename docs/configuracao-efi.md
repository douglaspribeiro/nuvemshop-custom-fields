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
notificação; o app consulta esse token na SDK e reconcilia a assinatura. Há
também conciliação periódica. Em produção, testar aprovação, recusa,
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
