# NC-39 — e-mails de ativação

## Sequência e estado atual

A sequência é **D3 → D7 → D11**, contados desde `stores.installed_at`, sem reiniciar o prazo em acessos. D11 substitui o antigo contato D15 por decisão de 04/11/2026. O texto D11 oferece ajuda para configurar o primeiro produto e informa que é o último e-mail desta sequência. Não promete desconto, não anuncia vencimento do teste e não cria cobrança.

D15 não é mais agendado. Registros D15 antigos, quando processados, são suprimidos com `d15_temporarily_disabled`; não são transformados em envios atrasados D11. A ação específica no último dia do futuro free trial continua pendente, conforme o [plano de ofertas gratuitas temporárias](plano-free-temporario.md#comunicação-no-fim-do-teste--referência-aprovada-em-04112026).

O fluxo permanece **desativado por padrão**, com simulação por padrão. Validar código e usar transporte de e-mail simulado nos testes não habilita envios reais, não publica a versão e não comprova entrega em uma caixa postal.

## Agendamento e elegibilidade

- D3 orienta a configurar o primeiro produto; D7 oferece ajuda; D11 encerra a sequência de apoio.
- A rotina consulta somente lojas ativas permitidas na lista de teste e cuja instalação esteja na data de corte ou depois dela. Cada loja é revalidada sob lock antes de gravar a fila.
- A unicidade é `(store_id, installed_at, step)`. Um registro D3 não impede a criação posterior de D7 ou D11. Repetir a rotina não repete a etapa.
- Não há envio antes do dia previsto. Cada etapa tem uma janela de até 24 horas depois do vencimento; etapas perdidas são suprimidas, não enviadas em bloco muito depois.
- A sequência para se já existem campos configurados, mesmo que atualmente pausados ou desativados, ou se o histórico registra campos que foram excluídos. Isso evita pedir a alguém que já configurou o app para configurar seu “primeiro produto”. A rotina grava ACTIVATED e suprime pendências da instalação; esse marcador também impede a retomada posterior.
- Desinstalação e solicitação de exclusão impedem envio. Um registro cuja data de instalação diverge do cadastro é suprimido. Uma reinstalação que preserve a data original não reinicia etapas já registradas. Exclusão LGPD remove a fila NC-39.
- Falhas em uma loja ou mensagem são registradas e não interrompem o processamento das demais.

## Entrega, concorrência e falhas

Agendamento e reserva de entrega usam transações independentes por loja/mensagem. A ordem dos locks é loja → registro da fila. O estado CLAIMED é confirmado no banco antes de chamar o SMTP; a transação não permanece aberta durante a chamada de rede. Dois processos não devem enviar a mesma etapa.

Estados: PENDING, CLAIMED, SENT, SIMULATED, SUPPRESSED, FAILED, REVIEW e ACTIVATED.

- SENT significa que a chamada ao transporte retornou com sucesso, não confirmação de entrega ou leitura na caixa postal.
- SIMULATED não chama SMTP e é terminal; desligar a simulação posteriormente não reenvia automaticamente essa mesma etapa.
- Erros de validação anteriores ao SMTP têm no máximo três tentativas, com espera de 1 e 2 minutos entre elas.
- Resultado SMTP incerto fica REVIEW e nunca é reenviado automaticamente. CLAIMED remanescente após interrupção também exige análise operacional. Não há promessa de entrega exatamente uma vez via SMTP.
- Etapas fora da lista D3/D7/D11 são suprimidas. A flag global e a data de corte são verificadas também nas entradas diretas de processamento.

## Configuração e logs

| Propriedade | Padrão | Função |
| --- | --- | --- |
| `activation.email.enabled` | `false` | Habilitação global |
| `activation.email.dry-run` | `true` | Simulação sem SMTP |
| `activation.email.start-at` | vazio | Data de corte ISO-8601; ausente/inválida impede processamento |
| `activation.email.allowlist` | vazio | IDs das lojas permitidas, separados por vírgula |
| `activation.email.suppression-ready` | `false` | Trava de liberação para processamento da entrega |
| `activation.email.schedule-delay-ms` | `60000` | Intervalo do agendamento NC-39; separado da rotina NC-04 de 12 horas |

Eventos INFO/WARN permitem acompanhar a fila com `nc39.email.queued`, `nc39.activation.detected`, `nc39.email.suppressed`, `nc39.email.simulated`, `nc39.email.claimed`, `nc39.email.sent`, `nc39.email.validation_failed`, `nc39.email.review`, `nc39.schedule.failed` e `nc39.dispatch.failed`. DEBUG inclui início do ciclo, rotina desativada e espera pela trava de liberação. Data de corte inválida gera WARN.

Os registros usam ID da loja, ID da mensagem, etapa, prazo, estado, tentativa, motivo controlado e classe da exceção. Não registrar endereço de e-mail, nome da loja, corpo da mensagem, tokens, senha SMTP ou mensagem bruta da exceção. Consultar o estado persistido na fila para investigar resultados incertos.

## Validação e limites da entrega

Testes locais verificam a jornada D3 → D7 → D11 sem D15; repetição de agendamento; primeira configuração, pausa e histórico excluído; interrupção de pendências; simulação e flags; janela temporal; lista de lojas; reinstalação/desinstalação; exclusão LGPD; validação e tentativas limitadas; timeout SMTP; trabalhadores e agendadores concorrentes; claim confirmado antes do SMTP; logs sem dados pessoais; e migração V67 com unicidade da fila.

Revalidação local em 04/11/2026: 36 testes específicos da NC-39 passaram; suíte Java completa com 898 testes, zero falhas/erros e um ignorado; 160 testes JavaScript aprovados. Integração e contrato da migração foram verificados em H2 no modo MySQL, com o transporte de e-mail simulado. Isso não equivale a um teste concorrente no MySQL de produção nem a uma entrega SMTP real.

**Ainda não é uma liberação para envio real:** a trava `suppression-ready` é uma configuração, não implementa descadastro, bounce ou reclamações por si só. A integração dessas proteções com a NC-39 e uma validação controlada em caixa postal continuam pendentes. O código não deve ser considerado uma campanha completa pronta para produção somente porque esses testes passaram. Nenhum envio real foi feito nesta revalidação.
