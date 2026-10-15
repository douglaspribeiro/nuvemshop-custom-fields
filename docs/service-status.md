# Página pública de status

A página `/status/` é pública, tem traduções em português e espanhol e é
acessível pelo rodapé do site e pela navegação do painel. `/status` redireciona
para a URL com barra final.

## Configuração

No UptimeRobot, gere uma **Monitor-specific API key** para o monitor que deseja
publicar. Configure-a no ambiente do processo/container da aplicação:

```dotenv
UPTIMEROBOT_API_KEY=<chave específica do monitor>
```

Reinicie/recrie a aplicação após configurar a variável. Não inclua a chave no
Git, no HTML, em parâmetros de URL ou em arquivos públicos. Não é necessário
informar o ID: a chave específica restringe a consulta a um único monitor.
Respostas com zero ou vários monitores são rejeitadas. A chave fica apenas no
servidor; a aplicação não disponibiliza uma API de proxy com a resposta original.

Sem a variável, a página informa que o monitoramento está em preparação, sem
fazer chamadas externas. Para validar a integração real, abra `/status/` depois
de configurar a chave e confira o horário da atualização, o estado e os períodos.

## Comportamento

- Consulta `POST https://api.uptimerobot.com/v2/getMonitors`, compatível com a
  chave específica do monitor, com credencial no corpo do formulário.
- Busca disponibilidade de 1, 7 e 30 dias e até dez eventos de queda/recuperação.
  A abrangência do histórico depende do plano e dos dados retornados pelo UptimeRobot.
- Compartilha o cache por 60 segundos entre visitantes **de cada instância**.
  Falhas também têm esse intervalo antes de uma nova tentativa. A página se
  atualiza a cada 60 segundos; o intervalo de verificação do monitor continua
  sendo aquele configurado no UptimeRobot.
- Limita conexão a 3 segundos e leitura a 5 segundos.
- Em falhas de transporte, autenticação ou resposta inválida, mantém os últimos
  dados em memória, preserva o horário e os identifica como desatualizados.
  A faixa principal passa a informar que o estado atual está indisponível.
- Uma falha na consulta nunca é interpretada como queda do serviço monitorado.
- Publica somente estado, percentuais e tipo/data dos eventos. Nomes internos,
  URLs, credenciais, contatos e motivos técnicos não são apresentados.
- Datas são exibidas com fuso explícito de São Paulo. Após reinício, o cache
  começa vazio e só apresenta métricas quando uma consulta tiver sucesso.

## Hospedagem

Esta implementação roda dentro do aplicativo Spring. Se o aplicativo ou sua
infraestrutura cair, a página também pode ficar inacessível. Para disponibilidade
independente, publique futuramente uma página em infraestrutura separada ou use
a página hospedada do próprio UptimeRobot. Isso não é configurado por esta mudança.

## Verificação

```sh
mvn -o -q -Dskip.frontend=true -Dtest=ServiceStatusServiceTest,ServiceStatusPageTest,MessageBundleParityTest test
```

Os testes simulam o UptimeRobot e cobrem cache de sucesso/falha, preservação do
horário, ausência de configuração, resposta inválida, estados, privacidade dos
dados publicados e renderização das duas línguas. Não substituem a validação
com a credencial real após configuração no ambiente.

Referência: [API v2 / getMonitors](https://uptimerobot.com/api/legacy/).
