# Configuração da Paddle

Este guia descreve como criar os acessos, credenciais, produtos, preços e
webhooks necessários para a integração Paddle do Campos Personalizados.

A configuração deve começar no **Sandbox**. Sandbox e produção possuem contas,
credenciais, catálogos e webhooks separados. Nenhuma credencial real deve ser
gravada no Git, em arquivos versionados ou enviada por chat.

Documentação oficial: [Paddle Sandbox](https://developer.paddle.com/sdks/sandbox/).

## 1. Criar ou acessar a conta Sandbox

1. Acesse o [Paddle Sandbox Dashboard](https://sandbox-vendors.paddle.com/).
2. Crie uma conta Sandbox ou entre na conta existente.
3. Confirme que o painel indica o ambiente de teste antes de criar qualquer
   credencial ou produto.

## 2. Criar a API key do backend

No painel Sandbox:

1. Acesse **Developer tools → Authentication**.
2. Abra a aba **API keys**.
3. Clique em **New API key**.
4. Use, por exemplo:
   - nome: `Campos Personalizados - Backend Sandbox`;
   - descrição: `Backend multigateway para AR, MX e CL`.
5. Defina uma data de expiração e, se desejado, habilite a rotação da chave.
6. Conceda somente estas permissões:
   - **Transactions: Write**;
   - **Subscriptions: Write**;
   - **Prices: Read**.
7. Salve e copie a chave imediatamente. Ela só é exibida uma vez.

As permissões de escrita também permitem a leitura da mesma entidade. A chave é
usada somente no backend e normalmente começa com `pdl_sdbx_apikey_` no Sandbox.

Armazene-a no segredo de ambiente:

```env
PADDLE_API_KEY=pdl_sdbx_apikey_...
```

Referências oficiais:
[autenticação](https://developer.paddle.com/api-reference/about/authentication/) e
[permissões](https://developer.paddle.com/api-reference/about/permissions/).

## 3. Criar o client-side token

Ainda em **Developer tools → Authentication**:

1. Abra a aba **Client-side tokens**.
2. Clique em **New client-side token**.
3. Use o nome `Campos Personalizados - Checkout Sandbox`.
4. Salve e copie o token.

No Sandbox, o token normalmente começa com `test_`:

```env
PADDLE_CLIENT_SIDE_TOKEN=test_...
```

Esse token autentica o Paddle.js que abre o checkout. Ele é próprio para uso no
frontend e tem poderes limitados. A API key, por outro lado, nunca deve ser
exposta no navegador.

Referência oficial:
[client-side tokens](https://developer.paddle.com/paddle-js/about/client-side-tokens/).

## 4. Cadastrar domínio e Default payment link

O Paddle exige um Default payment link antes de permitir a criação de
transações.

1. Acesse **Checkout → Website approval**.
2. Adicione o domínio `campos-personalizados.wzhub.pro`.
3. Acesse **Checkout → Checkout settings**.
4. Em **Default payment link**, informe inicialmente:

   ```text
   https://campos-personalizados.wzhub.pro/
   ```

5. Salve a configuração.

No Sandbox, a validação de domínio é simplificada. Em produção, o domínio deve
ser aprovado pelo Paddle.

Referência oficial:
[Default payment link](https://developer.paddle.com/build/transactions/default-payment-link/).

## 5. Criar o destino de webhook

1. Acesse **Developer tools → Notifications**.
2. Clique em **New destination**.
3. Preencha:
   - descrição: `Campos Personalizados - Sandbox`;
   - tipo: `URL`;
   - API version: `1`;
   - uso/tráfego: **Platform and simulations** ou **All**, se disponível;
   - URL:

     ```text
     https://campos-personalizados.wzhub.pro/prod/webhooks/paddle7
     ```

4. Inscreva o destino nestes eventos:

   ```text
   transaction.completed
   transaction.payment_failed
   transaction.past_due
   transaction.canceled

   subscription.created
   subscription.updated
   subscription.activated
   subscription.past_due
   subscription.paused
   subscription.resumed
   subscription.canceled
   ```

5. Salve o destino.
6. Abra o destino criado e copie sua **Secret key**.

Armazene a Secret key como:

```env
PADDLE_WEBHOOK_SECRET=pdl_ntfset_...
```

O nome do destino é apenas descritivo. O endpoint que efetivamente recebe as
notificações é `/prod/webhooks/paddle7`. A Secret key é confidencial e é usada
para validar o cabeçalho `Paddle-Signature`.

Referência oficial:
[notification destinations](https://developer.paddle.com/webhooks/about/notification-destinations/).

## 6. Criar produtos e preços

Em **Catalog → Products**, crie dois produtos:

1. `Campos Personalizados - Essencial`;
2. `Campos Personalizados - Pro`.

Use a categoria fiscal correspondente a software/SaaS, normalmente **SaaS**.

Dentro dos produtos, crie os seis preços abaixo. `PREMIUM` é o identificador
interno do plano Essencial e `PREMIUM_PLUS` é o identificador interno do plano
Pro.

| País | Plano comercial | Plano interno | Moeda | Valor mensal |
| --- | --- | --- | --- | ---: |
| Argentina | Essencial | `PREMIUM` | ARS | 5.599 |
| Argentina | Pro | `PREMIUM_PLUS` | ARS | 8.399 |
| México | Essencial | `PREMIUM` | MXN | 99 |
| México | Pro | `PREMIUM_PLUS` | MXN | 149 |
| Chile | Essencial | `PREMIUM` | CLP | 4.199 |
| Chile | Pro | `PREMIUM_PLUS` | CLP | 6.299 |

Configure cada preço com:

- cobrança recorrente;
- período **Monthly**;
- frequência de 1 mês;
- `Tax mode` igual a **Internal / Tax included**;
- sem período de teste;
- quantidade padrão igual a 1.

Depois de criar cada preço, abra seu menu, use **Copy price ID** e guarde o ID
que começa com `pri_`. A aplicação usa um Price ID independente para cada
combinação de país e plano.

O validador do backoffice confere remotamente moeda, valor, recorrência mensal e
`tax_mode=internal`. Uma divergência impede a habilitação do preço ou da rota.

Referências oficiais:
[criação de produtos e preços](https://developer.paddle.com/build/products/create-products-prices/)
e [campos de um preço](https://developer.paddle.com/api-reference/prices/create-price/).

## 7. Configurar os segredos da aplicação

Cadastre as seguintes variáveis no ambiente de execução:

```env
PADDLE_ENABLED=true
PADDLE_SANDBOX=true
PADDLE_API_BASE_URL=https://sandbox-api.paddle.com

PADDLE_API_KEY=pdl_sdbx_apikey_...
PADDLE_CLIENT_SIDE_TOKEN=test_...
PADDLE_WEBHOOK_SECRET=pdl_ntfset_...

PADDLE_API_VERSION=1
PADDLE_GRACE_DAYS=3
PADDLE_WEBHOOK_TOLERANCE_SECONDS=300
PADDLE_CHECKOUT_TOKEN_MINUTES=30
```

Depois de cadastrar ou alterar essas variáveis, reinicie/republique a aplicação.
A integração só é considerada configurada quando está habilitada e a URL da
API, API key, client-side token e webhook secret estão preenchidos.

## 8. Cadastrar os Price IDs no backoffice

1. Acesse:

   ```text
   https://campos-personalizados.wzhub.pro/backoffice/payments
   ```

2. Localize cada combinação Paddle/Sandbox de país e plano.
3. Cole o `pri_...` correspondente.
4. Salve e execute a validação.
5. Confirme que os dois preços do país foram validados.
6. Habilite a rota Paddle daquele país.

A rota só fica operacional quando os preços dos planos Essencial e Pro daquele
país estão preenchidos, habilitados e válidos.

## 9. Testar o checkout e os webhooks

Para uma aprovação simples no Sandbox, use:

```text
Cartão: 4242 4242 4242 4242
Validade: qualquer data futura
CVV: qualquer valor válido
```

Para simular uma recusa:

```text
Cartão: 4000 0000 0000 0002
```

Os cartões disponíveis e seus comportamentos estão na
[documentação do Sandbox](https://developer.paddle.com/sdks/sandbox/).

Também é possível testar o endpoint em **Developer tools → Notifications →
Simulations → New simulation**. Comece simulando:

```text
transaction.completed
subscription.created
subscription.updated
```

O endpoint valida a assinatura, persiste o corpo original e responde HTTP 200.
Um worker processa a fila periodicamente. Falhas ficam registradas em
`payment_webhook_events` com status `FAILED`, erro, contador de tentativas e
data da próxima tentativa.

Antes de liberar o Sandbox, teste no mínimo aprovação, recusa, 3DS, renovação,
recuperação de pagamento e cancelamento.

## 10. Passagem para produção

Produção requer novos recursos criados no painel de produção:

- API key de produção;
- client-side token de produção, normalmente iniciado por `live_`;
- destino de webhook e Secret key de produção;
- produtos e seis Price IDs de produção;
- domínio aprovado e Default payment link de produção.

Altere o ambiente da aplicação:

```env
PADDLE_SANDBOX=false
PADDLE_API_BASE_URL=https://api.paddle.com
```

Substitua todas as credenciais e IDs Sandbox pelos equivalentes de produção,
valide o catálogo novamente e somente então habilite as rotas de produção.

## Roteamento do Brasil

A configuração Paddle não substitui nem altera o fluxo brasileiro. O
roteamento previsto permanece:

- Brasil (`BR`/`BRL`): EFI em produção;
- Argentina (`AR`/`ARS`): Paddle;
- México (`MX`/`MXN`): Paddle;
- Chile (`CL`/`CLP`): Paddle.

