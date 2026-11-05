# NC-39 — e-mails de ativação

## Sequência e estado atual

A sequência é **D3 → D7 → D11**, contados desde `stores.installed_at`, sem reiniciar o prazo em acessos. D11 substitui o antigo contato D15 por decisão de 04/11/2026. O D11 lembra o lojista de configurar os campos dos produtos e oferece suporte. Por decisão de 05/11/2026, não anuncia que é o último contato. Não promete desconto, não anuncia vencimento do teste e não cria cobrança.

D15 não é mais agendado. Registros D15 antigos, quando processados, são suprimidos com `d15_temporarily_disabled`; não são transformados em envios atrasados D11. A ação específica no último dia do futuro free trial continua pendente, conforme o [plano de ofertas gratuitas temporárias](plano-free-temporario.md#comunicação-no-fim-do-teste--referência-aprovada-em-04112026).

O fluxo permanece **desativado por padrão**, com simulação por padrão. Validar código e usar transporte de e-mail simulado nos testes não habilita envios reais, não publica a versão e não comprova entrega em uma caixa postal.

## Agendamento e elegibilidade

- D3 orienta a configurar o primeiro produto; D7 oferece ajuda; D11 lembra a configuração dos campos.
- A rotina consulta lojas ativas e revalida cada uma sob lock antes de gravar a fila. D3/D7 exigem a lista de lojas e a data de corte. **D11 abrange qualquer loja ativa com pelo menos 11 dias de instalação e sem campos configurados atualmente**, inclusive lojas de 30, 90 dias ou mais; essa etapa não usa lista nem corte de instalação.
- A unicidade é `(store_id, installed_at, step)`. Um registro D3 não impede a criação posterior de D7 ou D11. Repetir a rotina não repete a etapa.
- Não há envio antes do dia previsto. D3/D7 têm janela de até 24 horas após o vencimento e não são enviados retroativamente. D11 permanece elegível a partir de 264 horas, sem expiração no D12. Uma loja antiga recebe somente D11, sem receber D3/D7 atrasados.
- Campos ainda salvos em um produto, inclusive pausados ou desativados, impedem as três etapas. D3/D7 também param após o primeiro histórico de configuração ou marcador ACTIVATED. Pela nova regra, D11 avalia a ausência **atual** de campos: se os produtos anteriores foram excluídos e nenhum continua configurado, a loja pode receber o lembrete genérico D11, sem afirmar que se trata de seu primeiro produto.
- Desinstalação e solicitação de exclusão impedem envio. Um registro cuja data de instalação diverge do cadastro é suprimido. Uma reinstalação que preserve a data original não reinicia etapas já registradas. Exclusão LGPD remove a fila NC-39.
- Falhas em uma loja ou mensagem são registradas e não interrompem o processamento das demais.

## Entrega, concorrência e falhas

Agendamento e reserva de entrega usam transações independentes por loja/mensagem. A ordem dos locks é loja → registro da fila. O estado CLAIMED é confirmado no banco antes de chamar o SMTP; a transação não permanece aberta durante a chamada de rede. Dois processos não devem enviar a mesma etapa.

Estados: PENDING, CLAIMED, SENT, SIMULATED, SUPPRESSED, FAILED, REVIEW e ACTIVATED.

- SENT significa que a chamada ao transporte retornou com sucesso, não confirmação de entrega ou leitura na caixa postal.
- SIMULATED não chama SMTP e é terminal; desligar a simulação posteriormente não reenvia automaticamente essa mesma etapa.
- Erros de validação anteriores ao SMTP têm no máximo três tentativas, com espera de 1 e 2 minutos entre elas.
- Resultado SMTP incerto fica REVIEW e nunca é reenviado automaticamente. CLAIMED remanescente após interrupção também exige análise operacional. Não há promessa de entrega exatamente uma vez via SMTP.
- Etapas fora da lista D3/D7/D11 são suprimidas. A flag global é verificada também nas entradas diretas. Data de corte e allowlist continuam exigidas para D3/D7; D11 independe delas.

## Configuração e logs

| Propriedade | Padrão | Função |
| --- | --- | --- |
| `activation.email.enabled` | `false` | Habilitação global |
| `activation.email.dry-run` | `true` | Simulação sem SMTP |
| `activation.email.start-at` | vazio | Data de corte ISO-8601 para D3/D7; ausente/inválida impede essas etapas, sem impedir D11 |
| `activation.email.allowlist` | vazio | IDs permitidos para D3/D7, separados por vírgula; D11 não usa a lista |
| `activation.email.suppression-ready` | `false` | Liberação operacional explícita; a configuração real de feedback também é verificada pelo código |
| `activation.email.schedule-delay-ms` | `60000` | Intervalo do agendamento NC-39; separado da rotina NC-04 de 12 horas |

Eventos INFO/WARN permitem acompanhar a fila com `nc39.email.queued`, `nc39.activation.detected`, `nc39.email.suppressed`, `nc39.email.simulated`, `nc39.email.claimed`, `nc39.email.sent`, `nc39.email.validation_failed`, `nc39.email.review`, `nc39.schedule.failed` e `nc39.dispatch.failed`. DEBUG inclui início do ciclo, rotina desativada e espera pela trava de liberação. Data de corte inválida gera WARN.

Os registros usam ID da loja, ID da mensagem, etapa, prazo, estado, tentativa, motivo controlado e classe da exceção. Não registrar endereço de e-mail, nome da loja, corpo da mensagem, tokens, senha SMTP ou mensagem bruta da exceção. Consultar o estado persistido na fila para investigar resultados incertos.

## Validação e limites da entrega

Testes locais verificam a jornada D3 → D7 → D11 sem D15; repetição de agendamento; primeira configuração, pausa e histórico excluído; interrupção de pendências; simulação e flags; janela temporal; lista de lojas; reinstalação/desinstalação; exclusão LGPD; validação e tentativas limitadas; timeout SMTP; trabalhadores e agendadores concorrentes; claim confirmado antes do SMTP; logs sem dados pessoais; e migração V67 com unicidade da fila.

Revalidação local em 04/11/2026: 36 testes específicos da NC-39 passaram; suíte Java completa com 898 testes, zero falhas/erros e um ignorado; 160 testes JavaScript aprovados. Integração e contrato da migração foram verificados em H2 no modo MySQL, com o transporte de e-mail simulado. Isso não equivale a um teste concorrente no MySQL de produção nem a uma entrega SMTP real.

## Descadastro, bounces e reclamações

A V68 adiciona supressões por loja, histórico idempotente dos eventos do SES e os campos de correlação da entrega. Nenhum endereço ou corpo bruto do provedor é persistido nessas novas tabelas. O hash do destinatário é dado pseudonimizado e também é removido na exclusão LGPD.

Cada entrega NC-39 usa MIME com `X-SES-CONFIGURATION-SET`, tags `nc39_event`/`nc39_store`, link de descadastro visível no texto e os cabeçalhos `List-Unsubscribe` e `List-Unsubscribe-Post`. O destinatário fica fixado na reserva de envio; se o cadastro de e-mail mudar antes do transporte, a validação falha sem enviar ao endereço diferente.

O link contém um token aleatório de 256 bits. Somente seu SHA-256 é armazenado. GET em `/activation-emails/unsubscribe?token=...` mostra uma confirmação PT/ES, sem alterar preferências; POST confirma o descadastro, inclusive o POST de um clique dos clientes de e-mail. Não exige login. Token ausente, desconhecido ou removido por LGPD não autoriza alteração. A página não carrega recursos externos nem analytics, e envia `no-store`, `no-referrer`, `noindex` e CSP restritiva. Logs de aplicação usam somente a rota sem query; proxies e outros acessos externos também devem omitir a query desta rota.

O descadastro grava um bloqueio durável para a loja, suprime imediatamente as etapas PENDING e impede novas etapas. Repetir o POST não cria efeitos adicionais. O bloqueio persiste em reinstalações e mudanças de e-mail, sem alteração automática do plano, dos campos, dos pedidos ou dos avisos transacionais de pagamento/suporte. Um opt-out anterior registrado no winback também impede a NC-39, sem reabrir contato em uma reinstalação.

O consumidor autenticado SQS existente agora encaminha cada mensagem ao winback e à NC-39. O ACK só ocorre depois de ambos terminarem suas transações; falhas conservam a mensagem para nova tentativa e não interrompem as demais. A NC-39 não expõe webhook público para aceitar alegações de bounce ou reclamação.

Antes de aceitar um evento, são conferidos Configuration Set, tags da loja/mensagem, destinatário único com hash correspondente à reserva, estado de entrega e ID SES já vinculado quando existir. Bounce exige destinatário afetado correspondente; reclamações sem destinatário individual usam a correlação do destino único original. O endereço atual do cadastro não substitui o destinatário da mensagem histórica. Eventos raw e envelope SNS `Notification` são aceitos. Eventos de outras campanhas são ignorados. A política IAM/SQS deve limitar escrita ao SNS de origem confiável; a aplicação confia nessa fronteira, sem verificar assinatura de SNS em uma rota pública.

- `Bounce`: pausa a sequência para a loja. Inclui bounces Permanent e Transient finais publicados pelo SES, quando o provedor já encerrou suas tentativas. `DeliveryDelay` não causa supressão.
- `Complaint`: pausa a sequência quando a mensagem foi marcada como spam.
- `Subscription`: pausa se `newTopicPreferences.unsubscribeAll=true` ou existir tópico em `OptOut`. Opt-in e eventos Delivery/Send não desfazem um bloqueio anterior.
- Eventos duplicados são deduplicados no histórico, sob lock loja → mensagem. A primeira causa de supressão é preservada. Eventos fora de ordem não reativam a sequência. Uma entrega REVIEW ainda pode receber feedback correlacionado, sem reenvio.

O bloqueio é reavaliado na reserva e novamente antes de entrar no transporte. Uma mensagem que já iniciou a chamada SMTP não pode ser recolhida; o bloqueio interrompe os próximos envios. SENT continua significando aceitação pelo transporte, sem prometer chegada à caixa postal.

A exclusão LGPD remove tokens, hashes de destinatário, supressões, histórico de feedback e fila NC-39. Eventos tardios não recriam esses registros quando a mensagem/loja já foi removida. Um cadastro criado depois de uma exclusão completa é um novo ciclo de dados; não se conserva uma blacklist pessoal fora da política de exclusão.

Logs adicionais: `nc39.suppression.recorded`, `nc39.ses.feedback` e `nc39.transport.accepted`. Registram IDs e motivos/tipos controlados, sem token, endereço, diagnóstico SMTP ou corpo bruto. O bloqueio de configuração incompleta aparece em DEBUG como `feedback_not_configured`.

Revalidação após integrar as proteções em 04/11/2026: **929 testes Java, zero falhas, zero erros e um ignorado**, incluindo 26 testes de integração das proteções, 26 testes do job, 12 testes da jornada, a migração V67 → V68 e regressões do transporte/consumidor compartilhados. `git diff --check` aprovado. Banco H2 em modo MySQL, transporte SMTP e clientes externos simulados. A rota real MVC e a renderização Thymeleaf foram exercitadas nos testes; não foi feita inspeção visual em navegador nem validação operacional AWS/caixa postal. Os 160 testes JavaScript mencionados acima pertencem à revalidação anterior; nenhum JavaScript foi alterado nesta etapa.

## Operação e habilitação

A fila de eventos informada pelo operador é **`email-tracking-events`**, já utilizada pelos retornos de e-mail. Reutilizar essa fila no consumidor compartilhado, sem criar outro consumidor concorrente. Em `AWS_SES_EVENTS_QUEUE_URL`, informar sua URL completa; o nome isolado não é suficiente. A região deve corresponder à fila existente. Confirmar também a publicação de Bounce e Complaint: mensagens Send/Delivery na fila não comprovam que os demais tipos estejam habilitados.

Os padrões continuam `ACTIVATION_EMAIL_ENABLED=false`, `ACTIVATION_EMAIL_DRY_RUN=true` e `ACTIVATION_EMAIL_SUPPRESSION_READY=false`. Subir esta versão e aplicar V68 não inicia a campanha.

Para operar a sequência, o ambiente precisa de:

- SMTP SES válido, remetente verificado e `APP_BASE_URL` público HTTPS para o descadastro.
- O consumidor compartilhado se chama `SesEventConsumer`. A leitura e a consulta de quantidade na fila são horárias por padrão, com primeira execução após uma hora de processo: `AWS_SES_EVENTS_POLL_DELAY_MS=3600000` e `AWS_SES_EVENTS_ATTRIBUTES_DELAY_MS=3600000`. Reinstanciar o app reinicia a espera. Um bounce/complaint pode levar até esse ciclo para ser aplicado; o descadastro via link é imediato. Painéis e métricas estão descritos em [monitoramento Grafana](monitoramento-grafana.md#consumo-horário-dos-eventos-de-e-mail-ses).
- `AWS_SES_EVENTS_ENABLED=true`, `AWS_SES_EVENTS_QUEUE_URL`, `AWS_SES_EVENTS_REGION` e `AWS_SES_CONFIGURATION_SET` configurados. O Configuration Set precisa publicar pelo menos Send, Delivery, Bounce e Complaint no SNS inscrito nessa fila SQS; Subscription também pode ser consumido quando o gerenciamento de contatos SES estiver em uso. O link próprio de descadastro funciona independentemente desse recurso de contatos.
- Política SQS permitindo escrita somente pelo tópico SNS autorizado; credenciais da aplicação com ReceiveMessage/DeleteMessage/GetQueueAttributes conforme a operação do consumidor; DLQ/redrive e monitoramento dos erros `ses.events.poll_failed`/`ses.events.event_failed`, compartilhados pelo consumidor.
- Liberação `ACTIVATION_EMAIL_SUPPRESSION_READY=true`, `ACTIVATION_EMAIL_ENABLED=true` e, para envio real, `ACTIVATION_EMAIL_DRY_RUN=false`. Esses controles habilitam D11 para toda a base elegível. D3/D7 exigem adicionalmente data de corte ISO-8601 em `ACTIVATION_EMAIL_START_AT` e IDs explícitos em `ACTIVATION_EMAIL_ALLOWLIST`.

Mesmo com a liberação manual, envio real fica pendente se feedback estiver desativado, fila/região/Configuration Set estiverem ausentes ou a URL pública não for HTTPS. Simulação não envia SMTP e continua terminal; não use a mesma linha SIMULATED esperando um envio posterior automático.

A implementação das três integrações está concluída no código. A comprovação operacional em AWS e caixa postal real é uma etapa separada: publicar a versão, confirmar a rota pública e o fluxo SES → SNS → SQS, usar uma loja/caixa de teste autorizada, conferir as tags/cabeçalhos, executar descadastro e simular bounce/complaint do SES para verificar a supressão no banco. A campanha não foi habilitada, não foi implantada e nenhum e-mail real foi enviado nesta implementação.

Referências oficiais usadas para o contrato: [cabeçalhos SMTP e tags SES](https://docs.aws.amazon.com/ses/latest/dg/event-publishing-send-email.html), [campos de bounce, complaint e subscription](https://docs.aws.amazon.com/ses/latest/dg/event-publishing-retrieving-sns-contents.html) e [exemplos de eventos](https://docs.aws.amazon.com/ses/latest/dg/event-publishing-retrieving-sns-examples.html).

## Regra D11 e textos atualizados em 05/11/2026

D11 já aceito pelo SMTP (SENT), reservado (CLAIMED), incerto (REVIEW), simulado
(SIMULATED) ou com tentativas esgotadas (FAILED) não é recriado, inclusive quando
houver outra data de instalação registrada para a mesma loja. Revisão operacional
continua necessária para os resultados incertos.

Somente supressões D11 das regras de público substituídas (`stage_expired`,
`outside_cohort`, `not_allowlisted`, `first_activation`) podem voltar a PENDING.
Isso ocorre sob lock e após conferir loja ativa, ausência atual de campos e
proteções de contato. Supressões por descadastro, bounce, reclamação ou instalação
incompatível não são revertidas. D15 continua desativado. Essa mudança não habilita
a campanha por si só e não altera os padrões de simulação/desativação.

Assuntos, corpos e rodapés dos três e-mails ficam em `messages.properties` (PT) e
`messages_es.properties` (ES), nas chaves `activation.email.d3.*`,
`activation.email.d7.*`, `activation.email.d11.*` e
`activation.email.unsubscribe`. A resolução usa `StoreLocale.forCountry`, a mesma
regra do app: Brasil/Portugal em português, demais países cadastrados em espanhol,
país ausente em português. A mensagem recebe o nome da loja como argumento, não
como padrão de formatação. Os envios usam versão de template `nc39-v2`; mensagens
antigas já enviadas não têm sua versão alterada.

**D11 PT — assunto:** Lembrete: configure os campos personalizados dos seus produtos

> Olá {nome da loja}! Sua loja ainda está sem produtos com campos personalizados configurados. Abra o aplicativo pelo painel da Nuvemshop, escolha um produto e adicione os campos que seus clientes devem preencher.
>
> Se precisar de ajuda, fale conosco pelo suporte do aplicativo.
>
> Equipe Campos Personalizados.

**D11 ES — assunto:** Recordatorio: configurá los campos personalizados de tus productos

> ¡Hola {nombre de la tienda}! Tu tienda todavía no tiene productos con campos personalizados configurados. Abrí la aplicación desde el administrador de Tiendanube, elegí un producto y agregá los campos que tus clientes deben completar.
>
> Si necesitás ayuda, contactanos desde el soporte de la aplicación.
>
> Equipo Campos Personalizados.

Cada versão inclui o próprio link de descadastro, no mesmo idioma. Alterar os
arquivos de mensagens exige publicar uma nova versão da aplicação.

Validação final em 05/11/2026: **994 testes Java, zero falhas/erros e um ignorado**.
Cobertura inclui D11 em lojas de 11/30/90 dias, ausência de envio retroativo D3/D7,
recuperação apenas das antigas supressões de público, preservação de bloqueios de
contato e resultados incertos, ausência atual de configuração, métricas somente
leitura, contador de envios confirmados e assuntos/corpos/rodapés PT/ES nos bundles
e no transporte MIME. A campanha não foi habilitada e nenhum e-mail real foi enviado.
