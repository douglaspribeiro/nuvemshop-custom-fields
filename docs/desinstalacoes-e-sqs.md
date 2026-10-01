# Histórico de saídas e publicação na SQS

## Estado atual — 01/10/2026

O desenvolvimento abaixo está implementado no workspace. Ainda não foi publicado
nem ativado em produção; as flags de SQS, e-mail, eventos SES e desconto permanecem
desligadas por padrão.

| Parte | Estado atual |
| --- | --- |
| Contagem de desinstalações | Histórico diário anônimo implementado, preservado após `store/redact`, com deduplicação por instalação. |
| Recuperação dos registros antigos | Script e testes prontos; falta reunir os logs completos, revisar o SQL e aplicá-lo após a V30. |
| Fila de desinstalações | URL fornecida configurada; outbox, publicação e consumidor implementados. Integração real com AWS ainda não validada. |
| E-mails de reconquista | Coleta do motivo e seguimento implementados, usando o mesmo remetente e SMTP do suporte. Nenhum envio real realizado nesta implementação. |
| Lista no backoffice | Lista, filtros e detalhe implementados: desinstalação, resposta, eventos de e-mail, reinstalação, cupom e conversão. |
| Abertura e clique | Consumidor SES implementado; falta configurar a fila de eventos e o Configuration Set. Abertura detectada não comprova leitura. |
| Incentivo BR/Efí | Cupom de 50% na primeira mensalidade implementado para lojas elegíveis sem pagamento anterior. Reutiliza o plano existente e restaura o preço integral após aprovação da primeira cobrança. |
| Outros países/gateways e lojas com histórico pago | Comunicação sem desconto automático. Aprovação manual de incentivo ainda não implementada. |
| Funcionalidade solicitada | Registro, estados no backoffice e aviso quando marcada como entregue implementados. |
| Exclusão de dados | Campanhas, e-mails, eventos e cupons removidos junto à loja; apenas totais anônimos permanecem. |
| Patagonia | Correção local do SDK feita; falta validar na loja e publicar o script `9319`. Detalhes em [diagnóstico Patagonia](diagnostico-patagonia-e-scripts.md). |

### Validação concluída

- Suíte Java completa: **239 testes, zero falhas e zero erros**, concluída em
  01/10/2026 às 00:06 BRT. Log local: `/tmp/ncf-winback-discount-full-tests.log`.
- Frontend: 43 testes aprovados, verificação de tipos e build concluídos.
- Recuperação de totais: 3 testes Python aprovados.
- Verificação de alterações com `git diff --check` sem problemas.

Os testes de integração usam H2 e serviços externos simulados. Não comprovam a
execução das migrações em MySQL, a entrega real SES/SQS ou a cobrança real da Efí.
Nenhuma migração, SQL de recuperação, publicação SDK, envio real ou cobrança real
foi executado como parte deste desenvolvimento.

### Pendências para concluir e ativar

1. Informar URL e região da fila de eventos SES, nome do Configuration Set e
   disponibilizar acesso AWS para a aplicação. A fila de desinstalações já foi informada.
2. Aplicar e validar as migrações V30–V33 em MySQL de homologação, respeitando a
   sequência completa de migrações do repositório.
3. Validar o fluxo real de desinstalação, envio, resposta, abertura/clique,
   reinstalação e exclusão em homologação.
4. Validar no sandbox Efí a primeira cobrança com 50%, a renovação integral,
   recusa, resposta perdida e recuperação de falha ao restaurar o preço.
5. Conferir e recuperar os totais antigos usando os logs completos.
6. Publicar a aplicação e ativar as flags conforme as integrações forem validadas.
   Desconto em produção exige também `WINBACK_DISCOUNT_ALLOW_PRODUCTION=true`.
7. Validar o Patagonia em loja de teste e publicar o novo bundle SDK `9319`.

Ainda faltam ações no backoffice para reenvio manual de e-mails com confirmação
SMTP incerta e aprovação de incentivo para lojas que já pagaram. A expressão
“usou o ticket” aguarda definição: uso de cupom já é acompanhado; chamados de
suporte aparecem como contexto, sem atribuição automática à campanha.

## Causa do zero no painel

Os logs de 30/09/2026 confirmaram `app/uninstalled` junto a `store/redact`.
O último exclui o cadastro da loja e seus registros vinculados. A contagem antiga
dependia de `stores.uninstalled_at`, por isso desaparecia após a exclusão.

O app agora mantém apenas totais diários em `store_departure_daily`, sem Store ID,
nome, contato, token ou dados de pagamento. O painel exibe **Saídas no histórico**
e distingue desinstalações registradas de exclusões recebidas antes da desinstalação.
Uma exclusão, sozinha, não é apresentada como prova de desinstalação.

O lock do cadastro e `departure_counted` garantem uma saída por instalação,
inclusive com eventos concorrentes. Depois da exclusão, um webhook atrasado não
recria o cadastro nem aumenta o total. Uma nova instalação pode contar nova saída.
Reinstalações não apagam os totais históricos. A exclusão dos dados continua imediata.
Tokens inválidos detectados pelo admin não geram por si só uma nova estatística.

## Recuperar totais anteriores à correção

A migração V30 importa os cadastros desinstalados ainda existentes. Cadastros já
excluídos não podem ser reconstruídos pelo banco. Os totais podem ser recuperados
dos logs disponíveis com `scripts/recover-departure-counts.py`, sem restaurar contatos.

Use todos os logs anteriores ao início da migração V30, em ordem cronológica,
incluindo eventos `oauth.installed`, `webhook.receive.valid` e
`lgpd.store_redact.completed`. O script só contabiliza cadastros cuja exclusão foi
confirmada e gera SQL para revisão; não conecta ao banco.

```bash
python3 scripts/recover-departure-counts.py \
  --before '<instante real de início da migração V30, com fuso>' \
  --log-timezone UTC < /tmp/logs-anteriores-v30.log > /tmp/recuperar-totais.sql
```

Revise e execute o SQL após a migração V30. As colunas `recovered_*` são separadas
dos novos contadores e sua atualização não soma novamente ao repetir o mesmo
arquivo. Sempre regenere a partir do conjunto completo dos logs; uma amostra menor
sobrescreveria os totais recuperados daqueles dias. Os timestamps dos logs precisam
usar o fuso indicado; o painel agrupa novos eventos por `America/Sao_Paulo`.
O dia dos cadastros importados pela V30 usa o fuso da sessão SQL.

## Fila fornecida

```text
WINBACK_SQS_QUEUE_URL=https://sqs.us-east-2.amazonaws.com/265105089924/eventosDesistalacao
WINBACK_AWS_REGION=us-east-2
WINBACK_ENABLED=false
```

A região da fila é independente da região SMTP do SES.

Os e-mails de reconquista usarão o mesmo remetente `AWS_SES_FROM_EMAIL` e a mesma
conexão SMTP do suporte, via `notifications.ses` e o bean compartilhado
`sesMailSender`. Não será necessário configurar outro remetente ou outras
credenciais SMTP. O consumidor do primeiro e-mail de coleta de motivo está
implementado, mas permanece desligado por `WINBACK_MAIL_ENABLED=false`.

A publicação está implementada. O webhook grava uma outbox junto à desinstalação
na mesma transação e responde sem esperar a AWS. Um job publica os eventos a cada
30 segundos, usando `DelaySeconds=900`. Falhas mantêm o evento no banco com novas
tentativas progressivamente espaçadas. Múltiplas instâncias usam lock antes de enviar.
O SQS Standard pode entregar duplicatas: o consumidor precisa deduplicar por `eventId`,
inclusive se um envio chegar à AWS e sua confirmação não chegar ao aplicativo.

O corpo contém somente `version` e um UUID aleatório `eventId`. A relação com a
loja e a data fica na outbox local, removida por `store/redact`.
Nome, e-mail, token e Store ID não são enviados à SQS.

Reinstalação cancela eventos antigos ainda não publicados. Exclusão de dados apaga
toda a outbox da loja, inclusive o vínculo dos eventos já enviados. O futuro
consumidor descarta mensagens sem vínculo local ou de lojas reinstaladas,
e confirmar novamente a elegibilidade antes de gerar cupom ou enviar e-mail.

## Acesso AWS e ativação

Configure a role ou as credenciais de API pelas variáveis de ambiente padrão AWS.
Não reutilize as credenciais SMTP do SES. A publicação precisa desta permissão:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "sqs:SendMessage",
    "Resource": "arn:aws:sqs:us-east-2:265105089924:eventosDesistalacao"
  }]
}
```

O consumidor do primeiro e-mail, seu template HTML e texto em português/espanhol,
o formulário de motivos, a lista no backoffice, os e-mails de oferta por motivo
e o cupom da primeira mensalidade por cartão na Efí estão implementados localmente.
Mantenha `WINBACK_ENABLED=false` e `WINBACK_MAIL_ENABLED=false` até o acesso e o
escopo de disparos estarem validados. Nenhuma mensagem real foi enviada nos testes;
o cliente SQS é simulado.

## Lista de reconquistas e eventos de e-mail

O backoffice tem uma lista paginada em `/backoffice/winback`, por instalação
encerrada, vinculada à campanha. Mostra loja, desinstalação, e-mails, entrega,
abertura detectada, clique, reinstalação e interrupção de comunicações.
O detalhe exibe motivo, resposta, estado de cada envio, eventos do e-mail e
chamados da loja. Os chamados são contexto, sem atribuição automática à campanha.
A lista tem filtros por loja/ID, período no fuso de São Paulo, abertura, clique,
resposta, reinstalação, falha de entrega, interrupção de comunicações,
cupom usado, pagamento com cupom confirmado, histórico pago e solicitação de funcionalidade.
O cupom distingue emissão, aplicação no checkout, pagamento confirmado e
restauração do valor integral das próximas mensalidades.

A campanha é registrada mesmo com o envio desligado. A V32 importa desinstalações
com cadastro ainda existente, sem gerar mensagens para elas. Os cadastros apagados
continuam somente nos totais anônimos. O OAuth marca a reinstalação da mesma loja.

Cada envio tem identificação própria, associada ao UUID da campanha, para
correlacionar eventos sem confundir tentativas ou e-mails de suporte. No SMTP,
o envio da reconquista informa `X-SES-CONFIGURATION-SET` e
`X-SES-MESSAGE-TAGS` com identificadores aleatórios de campanha e envio,
sem e-mail ou Store ID nas tags. O nome do Configuration Set e a URL/região
da fila de eventos SES ainda precisam ser informados e validados. Configure:

```text
AWS_SES_EVENTS_ENABLED=false
AWS_SES_EVENTS_QUEUE_URL=<URL da fila SES já existente>
AWS_SES_EVENTS_REGION=<região dessa fila>
AWS_SES_CONFIGURATION_SET=<nome do Configuration Set>
WINBACK_MAIL_ENABLED=false
```

O consumidor dos eventos é ativado separadamente por `AWS_SES_EVENTS_ENABLED`.
Configure no destino SES ao menos `Send`, `Delivery`, `Open`, `Click`, `Bounce`
e `Complaint`. A role precisa de `sqs:ReceiveMessage`, `sqs:DeleteMessage` nas duas
filas, além de `sqs:SendMessage` na fila de desinstalações. Se a fila SES recebe
SNS, restrinja a política de publicação ao tópico correspondente. Configure DLQ
e política de redrive para eventos inválidos persistentes; eles não são confirmados
pelo aplicativo. A fila SES pode compartilhar eventos com o suporte: mensagens
sem as tags de reconquista são descartadas por este consumidor. Não execute outro
consumidor concorrente que dependa dessas mesmas mensagens; SQS distribui mensagens
entre consumidores, sem difundir uma cópia para cada consumidor.

A fila de eventos SES é consumida separadamente da `eventosDesistalacao`.
O consumidor aceita evento direto ou envelope SNS,
valida o evento, correlaciona somente envios conhecidos e processa
reentregas sem duplicar a linha do tempo. Só exclui a mensagem SQS após
persistir o resultado; falhas transitórias permitem nova tentativa.
Eventos sem vínculo local após exclusão de dados são descartados, sem
recriar cadastro. Eventos fora de ordem não podem desfazer entrega ou
apagar outros eventos já registrados. Não são armazenados o corpo bruto do evento,
destinatários, IP, user agent nem URLs contendo tokens; o clique guarda uma
classificação do destino. Abertura e clique são detectados pelo SES, não pelo GET
do formulário. Eventos tardios reconciliam o envio mesmo sem confirmação SMTP local.

O primeiro e-mail pede o motivo da desinstalação. Depois da resposta, é preparado
um e-mail específico para o motivo. Dificuldade de configuração e funcionalidade
ausente exigem texto para indicar a etapa ou o recurso. Se houver elegibilidade e
desconto habilitado, a resposta e o e-mail de seguimento mostram a oferta.
Os demais casos recebem a comunicação de contato sem cupom automático.
O link aleatório permite responder por 30 dias,
não expõe dados da loja e deixa de funcionar após exclusão. A opção de não receber
novas mensagens permanece acessível após os 30 dias, até a exclusão dos dados. Um POST registra motivo
e texto de até 2.000 caracteres ou interrompe comunicações. Opt-out, reclamação e
bounce impedem novos disparos também em campanhas posteriores da mesma loja,
enquanto o cadastro estiver retido.

Antes do SMTP, uma transação registra a intenção de envio; outra revalida a loja
sob lock e envia. Se ocorrer falha ou interrupção sem confirmação, o estado fica
“aguardando confirmação do envio”. Uma duplicata SQS não reenvia automaticamente.
Eventos SES podem confirmar a aceitação. Casos sem evento precisam de conferência
operacional; ainda não existe ação de reenvio manual no backoffice. Essa limitação
evita prometer entrega exatamente uma vez por SMTP.

| Informação na lista | Fonte e significado |
| --- | --- |
| Enviado | Aceite pelo provedor; separado de entrega ao destinatário. |
| Entregue | Evento `Delivery` do SES. |
| Abertura detectada | Evento `Open`; não comprova leitura humana. |
| Clique detectado | Evento `Click`, com data e destino; não comprova retorno ou pagamento. |
| Falha/recusa de e-mail | Eventos `Bounce`, `Reject` e falhas de envio. |
| Reclamação | Evento `Complaint`; interrompe novos envios da campanha. |
| Reinstalou | Novo OAuth da mesma loja, correlacionado à campanha. |
| Aplicou cupom | Cupom validado no checkout; ainda não é uso confirmado. |
| Usou cupom / convertido | Primeira cobrança com desconto confirmada pelo provedor de pagamento. |

A definição de “ticket” aguarda confirmação: se for cupom, seguem os dois
estados acima; se for chamado de suporte, será um vínculo ao chamado, com
abertura, resposta e resolução separados da conversão financeira.

`store/redact` remove campanhas, envios, interações e cupons
associados à loja. A lista individual não preservará dados apagados; o histórico
de saídas continuará disponível pelos totais anônimos já implementados.

Solicitações de funcionalidade têm os estados `NOVA`, `EM_ANALISE`, `PLANEJADA`,
`ENTREGUE` e `NAO_PREVISTA`, alterados no detalhe do backoffice. `ENTREGUE` prepara
um aviso único, que também pode ser enviado após reinstalação e respeita opt-out.

## Cupom e primeira mensalidade na Efí

```text
WINBACK_DISCOUNT_ENABLED=false
WINBACK_DISCOUNT_ALLOW_PRODUCTION=false
```

A emissão exige loja brasileira elegível para Efí, resposta à campanha e ausência
de pagamento anterior no histórico retido. São consultados a assinatura, a outbox
de pagamentos confirmados e os eventos de ativação de plano por pagamento. Esse
histórico é capturado também no momento da desinstalação. Cortesias e alterações
manuais de plano não equivalem a pagamento.

Há um cupom por loja enquanto seu cadastro estiver retido. Ele vale por 30 dias,
só se aplica após reinstalação da mesma loja, e um novo evento de desinstalação
não cria outro código. Expiração e opt-out impedem aplicação. A validação acontece
novamente no POST do checkout; valores e desconto são calculados no servidor.
Os preços do primeiro mês e das mensalidades seguintes aparecem antes de confirmar.
Se houver centavo indivisível, a primeira cobrança arredonda para baixo, favorecendo
o cliente. O incentivo automático ainda não abrange outros gateways ou países.

O plano mensal existente é reutilizado. A assinatura Efí é criada com os itens
reduzidos para a primeira cobrança. A aprovação dessa cobrança marca o cupom como
usado e a campanha como convertida; depois, `updateSubscription` envia os itens
com o preço integral capturado no checkout. A resposta deve confirmar assinatura
e valor. O ID do plano compartilhado fica em `provider_price_id`, sem ocupar o
campo de checkout único por assinatura. A V33 ajusta os registros Efí existentes.

Falhas de restauração deixam uma tarefa persistente em `payment_subscriptions`.
Um job tenta novamente a cada minuto e segue ativo mesmo se novos descontos forem
desabilitados. Uma resposta perdida de pagamento pode ser reconciliada pela primeira
cobrança consultada no provedor; a repetição não inicia outro débito. Uma primeira
cobrança recusada encerra a recorrência promocional e mantém o cupom sem uso.
Uma tarefa pendente bloqueia a abertura de outra assinatura para a mesma loja.

Antes de `store/redact`, o aplicativo tenta concluir uma restauração pendente; se
isso não for possível, solicita o cancelamento da assinatura promocional para
impedir renovações reduzidas. Se o provedor estiver indisponível também para esse
cancelamento, a exclusão local prossegue e registra erro de limpeza remota. Esse
caso exige conferência no provedor: dados apagados não serão retidos para retentar.
A exclusão remove cupons e histórico individual; assim, a proteção por loja não
pode reconhecer um cadastro já apagado que volte a instalar.

A produção permanece bloqueada para novos descontos mesmo com
`WINBACK_DISCOUNT_ENABLED=true`, até habilitar explicitamente
`WINBACK_DISCOUNT_ALLOW_PRODUCTION`. Primeiro valide no sandbox o valor efetivo
da primeira cobrança, o preço do segundo ciclo, recusa, resposta perdida,
reinstalação, exclusão e falha de restauração. Os testes locais usam H2 e simulam
SDK, SMTP e SQS; não validam a migração em MySQL nem a semântica real da Efí.

Pendências operacionais: dados e validação da fila SES, acesso AWS, migrações em
homologação, sandbox Efí, publicação e ativação. Ainda não existe ação de reenvio
manual para SMTP incerto nem aprovação de incentivo extra para quem já pagou.
A definição de “ticket” continua aguardando confirmação.
Referência financeira: [assinaturas Efí](https://dev.efipay.com.br/docs/api-cobrancas/assinatura/).
Referências: [eventos SES](https://docs.aws.amazon.com/ses/latest/dg/monitor-using-event-publishing.html),
[headers SMTP de rastreamento](https://docs.aws.amazon.com/ses/latest/dg/event-publishing-send-email.html).

Referências: [envio pelo SDK Java](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-sqs-messages.html)
e [atraso SQS](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-delay-queues.html).
