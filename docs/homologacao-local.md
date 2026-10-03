# Homologação local: Docker + Spring, sem painel da Nuvemshop

Este fluxo usa **MySQL no Docker** e **Spring rodando no host**, através de
`scripts/start-local-docker-and-spring.sh`. Não exige uma loja real, uma aplicação
real da Nuvemshop, OAuth nem redirecionamento pelo painel da Nuvemshop.

O perfil `local-homolog` oferece uma entrada protegida que cria uma loja fictícia
de ID `990000000001`, estabelece a mesma sessão utilizada pelo painel e redireciona
para `/admin`. A loja inicia no plano Free. Reentrar preserva o plano e a assinatura
de teste: não zera dados e não cria outra assinatura.

## 1. Pré-requisitos e isolamento

- Java 25, Maven, Docker com Compose e daemon iniciado.
- O painel funciona diretamente em `http://localhost:8080`, sem ngrok.
  Um túnel HTTPS é opcional e necessário apenas quando serviços externos, como
  a Efí, precisam entregar callbacks à aplicação local.
- Banco separado: `nuvem_custom_fields_homolog`, dentro do MySQL local na porta 3307.
  O script cria banco e usuário próprios, sem apagar o banco local existente.
- Não use dump de produção, credenciais de produção nem o `.env` de produção.

Não use `--reset-db`: o volume `mysql-local-data` contém outros bancos locais.
A opção `--homolog-local` rejeita esse reset para proteger os dados existentes.

## 2. Configuração: Infisical ou arquivo local

### Opção A: carregar stage do Infisical antes do MySQL

O launcher já conhece os identificadores informados para este projeto:

- Project ID: `831846ed-254b-4e13-b763-f124da86d11f`.
- Environment slug: `stage`.
- Secret path: `/app_custom-fields`.

Esses identificadores não são credenciais. Instale o
[CLI oficial do Infisical](https://github.com/Infisical/cli) no host e autentique-o
na instância onde esse projeto existe, usando `infisical login`. Não execute login
nem informe tokens dentro do script. O launcher usa a autenticação existente do CLI.

Na pasta de stage, configure as variáveis do exemplo `.env.local-homolog.example`,
especialmente `LOCAL_HOMOLOG_ACCESS_KEY` e as credenciais **sandbox** da Efí.
Antes de reutilizar as variáveis de stage, confira os webhooks do Discord: devem
apontar para canais de teste, pois as sugestões locais também geram notificações.
Não substitua credenciais compartilhadas de stage sem avaliar quem as utiliza;
se necessário, use uma pasta específica para os testes locais com `--infisical-path`.

Inicie a aplicação sem precisar informar URL:

```bash
./scripts/start-local-docker-and-spring.sh \
  --homolog-local --infisical \
  -- -DskipTests
```

Fluxo: `infisical run` busca os segredos → reinicia o launcher com as variáveis
injetadas → valida a chave local → prepara o MySQL → inicia o Spring.
Nenhum arquivo `.env` é gerado. Se o CLI não estiver instalado, a autenticação
falhar ou os segredos não puderem ser carregados, o fluxo para antes do Docker.
A chave local ausente/inválida também interrompe a inicialização antes do MySQL.

Sem `--app-base-url`, a homologação usa `http://localhost:8080`, ignorando a URL
e o callback remotos vindos de stage. Para mudar a porta ou usar um túnel opcional,
informe `--app-base-url` explicitamente; o callback é recalculado para essa origem.
HTTP só é aceito em `localhost`/`127.0.0.1` neste modo. O launcher mantém banco/usuário locais e os perfis
`docker,local-homolog`; não usa o endereço de banco remoto vindo do Infisical.
O perfil exige Efí sandbox. Sem `--infisical`, o fluxo anterior permanece igual.

Para trocar o destino explicitamente (não use produção):

```bash
./scripts/start-local-docker-and-spring.sh \
  --homolog-local --infisical \
  --infisical-project-id 831846ed-254b-4e13-b763-f124da86d11f \
  --infisical-env stage --infisical-path /app_custom-fields \
  --docker-only
```

Também é possível definir `LOCAL_INFISICAL_PROJECT_ID`, `LOCAL_INFISICAL_ENV` e
`LOCAL_INFISICAL_PATH` no terminal; as opções explícitas têm precedência.
O launcher rejeita os slugs `prod`, `production` e `producao`, e rejeita `--reset-db`.
Use uma identidade com acesso somente a stage para impedir acesso a produção
independentemente do nome escolhido para o ambiente.

Teste do launcher, com CLI/Docker/Maven simulados e sem acessar segredos reais:

```bash
bash scripts/tests/test-local-launcher-infisical.sh
```

### Opção B: arquivo de configuração local

Copie `.env.local-homolog.example` para `.env.local-homolog` usando seu editor.
O arquivo real é ignorado pelo Git. Preencha:

- `LOCAL_HOMOLOG_ACCESS_KEY`: chave aleatória de pelo menos 24 caracteres;
  `openssl rand -hex 24` gera uma opção adequada. Não a coloque em URLs ou commits.
- `APP_BASE_URL`: `http://localhost:8080`; só mude se escolher outra porta ou um
  túnel HTTPS. Não inclua caminho `/prod`.
- Senha local do backoffice.
- Opcionalmente, webhook de **um canal Discord de testes**. Sem ele, as sugestões
  ficam salvas com o aviso pendente; serão enviadas quando o webhook for configurado.

Comece com `EFI_ENABLED=false` para validar a entrada, produtos simulados, campos,
telas e sugestões. Isso não realiza cobranças. Para testar pagamentos, faça a etapa 4.

Carregue o arquivo no terminal que vai executar o script:

```bash
set -a
source .env.local-homolog
set +a
./scripts/start-local-docker-and-spring.sh --homolog-local --app-base-url "$APP_BASE_URL" -- -DskipTests
```

O script ativa `docker,local-homolog`, cria o banco separado e inicia o Spring.
O perfil limita a aplicação a `127.0.0.1`. Cookies locais usam `SameSite=Lax` e,
em HTTP de loopback, não exigem HTTPS. A configuração de produção não muda.
Se usar um túnel HTTPS, o launcher ativa cookies `Secure` neste perfil.
As migrations, incluindo V40 e V41, são aplicadas automaticamente.

Para preparar somente o MySQL:

```bash
./scripts/start-local-docker-and-spring.sh --homolog-local --docker-only --app-base-url "$APP_BASE_URL"
```

É possível trocar porta/banco com `--local-port` e `--local-db`; o nome do banco
precisa terminar em `_homolog`. Se outro processo usa 8080, pare somente esse processo
ou configure uma porta diferente e ajuste `--app-base-url` de acordo.

## 3. Entrar sem a Nuvemshop

Abra **`http://localhost:8080/local/homologacao`**, informe a chave local e clique em
**Entrar no painel local**. A chave é enviada via POST, não na URL.

Você irá para `/admin` com a loja fictícia selecionada. O cliente da API Nuvemshop
é substituído por uma simulação exclusivamente neste perfil:

- Produtos: Caneca de teste, Camiseta de teste e Presente de teste.
- Pedidos, scripts e webhooks: listas vazias; operações de script/webhook são simuladas.
- Não são enviados pedidos para a API real da Nuvemshop.

Pode testar painel, personalizações, planos e sugestões. Isso **não valida** OAuth
real, iframe/Nexo, vitrine ou checkout reais da Nuvemshop. Esses continuam exigindo
testes específicos de integração com uma loja real de desenvolvimento.

## 4. Habilitar Efí sandbox

No painel Efí, abra **API → Aplicações → sua aplicação → Homologação** e use
somente o `client_id` e `client_secret` dessa aba. Configure também `EFI_PAYEE_CODE`.

Crie três planos mensais **pela API de homologação** (`POST /v1/plan`,
`interval: 1`, sem limite de repetições): Essencial, Pro e Ultra. Pode usar Postman
com a coleção oficial. Planos criados pela interface da conta não necessariamente
são retornados pela API; use os `plan_id` retornados pela API sandbox.

Preencha na pasta de stage do Infisical ou no arquivo local:

```env
EFI_ENABLED=true
EFI_SANDBOX=true
EFI_CLIENT_ID=CLIENT_ID_DA_ABA_HOMOLOGACAO
EFI_CLIENT_SECRET=CLIENT_SECRET_DA_ABA_HOMOLOGACAO
EFI_PAYEE_CODE=IDENTIFICADOR_DA_CONTA
EFI_PREMIUM_PLAN_ID=ID_ESSENCIAL_SANDBOX
EFI_PREMIUM_PLUS_PLAN_ID=ID_PRO_SANDBOX
EFI_PREMIUM_ULTRA_PLAN_ID=ID_ULTRA_SANDBOX
```

Pare o Spring com Ctrl+C, recarregue o arquivo e execute novamente o mesmo comando.
O inicializador local cria o catálogo **EFI / SANDBOX / BR** e preenche IDs ausentes
com essas variáveis. Os preços padrão são R$ 19,99, R$ 29,99 e R$ 59,90.
Ele também configura a rota do Brasil como sandbox; só habilita pagamentos quando
as credenciais e os catálogos Essencial/Pro estão completos. IDs salvos não são
sobrescritos: alterações posteriores devem ser feitas no backoffice local.

No backoffice, confira **Pagamentos → EFI / SANDBOX / BR** e use a página de consulta
dos planos para confirmar os IDs. O sandbox não usa os IDs da produção.

Para validar a entrega real de callbacks da Efí, localhost não é acessível
pela Efí. Nessa etapa, use um túnel HTTPS opcional, reinicie com
`--app-base-url https://SEU-TUNEL.ngrok-free.dev` e configure os callbacks sandbox.
O túnel deve encaminhar diretamente, sem exigir login próprio:

- POST `/prod/webhooks/efi3`: assinatura.
- POST `/prod/webhooks/efi-upgrades`: ajuste proporcional.

O prefixo `/prod` é um caminho legado dos callbacks, **não significa ambiente de
produção**. No perfil local, as chamadas à Efí continuam usando sandbox.

Fontes oficiais: [credenciais](https://sejaefi.com.br/central-de-ajuda/api/como-obter-chaves-client-id-e-client-secret-na-api),
[assinaturas](https://dev.efipay.com.br/docs/api-cobrancas/assinatura/) e
[cartão/simulação de aprovação e recusa](https://dev.efipay.com.br/docs/api-cobrancas/cartao/).

## 5. Roteiro de teste

1. Entre pela página local e assine o Pro no painel, usando os dados de teste da Efí.
2. Aguarde a confirmação do pagamento e confira o plano Pro ativo.
3. Cadastre e ative um cupom SANDBOX para Ultra em Backoffice → Pagamentos → Cupons,
   com percentual e limites de utilização. Solicite Ultra, aplique esse código e confira crédito, desconto, ajuste de hoje e
   próxima mensalidade. Como a assinatura acabou de começar, o crédito será de
   quase todo o ciclo; não será o exemplo de metade do mês.
4. Pague o ajuste no sandbox. Confira que os benefícios só foram liberados após
   confirmação e que o `subscription_id` permaneceu igual.
5. Confira o histórico do lojista e **Backoffice → Pagamentos → Acompanhar ajustes**.
6. Teste recusa antes de concluir o upgrade. Uma recusa preserva o Pro; retentativas
   e reenvios não devem criar outra cobrança para a mesma operação pendente.
7. Envie uma sugestão de melhoria e confira seu histórico, a página do backoffice
   e a mensagem no canal Discord de testes. O aviso é processado a cada 60 segundos.

Para testar precisamente metade do ciclo, expiração, preço alterado, timeout e
notificação duplicada sem manipular datas ou dinheiro na Efí, use os testes:

```bash
mvn -Dtest=EfiUpgradeServiceTest,FeatureRequestServiceTest,LocalHomologationTest test
```

Um segundo upgrade no mesmo ciclo é bloqueado. Para testar cenários independentes,
use bancos locais diferentes terminados em `_homolog` e planos/assinaturas sandbox;
não altere a mão o plano de uma assinatura já paga para forçar uma nova migração.

## 6. Proteções de produção

A entrada só é registrada com perfil **local-homolog**, propriedade habilitada e
sem perfis `prod`/`production`. O perfil Docker normal de produção não registra
esse endpoint. Sem ele, `/local/homologacao` retorna 404.

Se o perfil local for ativado indevidamente, a aplicação recusa iniciar quando:

- `environment` não é `LOCAL_HOMOLOG`;
- o banco não é MySQL em `127.0.0.1` com nome terminado em `_homolog`;
- a chave de acesso é curta ou ausente;
- Efí sandbox não está ativa ou outro gateway/native billing está habilitado.

Não publique `SPRING_PROFILES_ACTIVE=docker,local-homolog` no servidor. Não exponha
MySQL pela internet nem compartilhe as credenciais/chave local. O atalho não aceita
um ID arbitrário de loja e não promove automaticamente nenhum usuário a plano pago.

## 7. Sugestões dos lojistas em produção

**Sugerir uma melhoria** aparece no menu e na visão geral do painel. É diferente
de suporte: explica avaliação sem promessa de entrega, salva título/descrição,
loja e plano no banco (`feature_requests`, V41) e mostra o histórico da própria loja.
Máximo: título 160 caracteres, descrição 4.000 e 5 novos envios por hora por loja.
O token de formulário evita duplicação por reenvio da mesma solicitação.

O resumo enviado ao Discord usa `DISCORD_SUPPORT_WEBHOOK_URL` (com o fallback já
existente para `DISCORD_PAYMENT_WEBHOOK_URL`), inclui ID, loja, plano, título e
até 1.200 caracteres da descrição. Não permite menções automáticas. Se o Discord
falhar, o registro é preservado e o envio será tentado novamente com intervalo crescente.
Uma resposta de rede incerta pode gerar aviso duplicado no Discord; o ID da sugestão
identifica o mesmo registro, que não é duplicado no banco.

As sugestões também aparecem em **Backoffice → Suporte → Sugestões dos lojistas**.
O apagamento de dados da loja remove seus registros locais; avisos já publicados no
Discord não são removidos automaticamente por esse fluxo.
