# Falha na renovação: carência, aviso e reativação

Implementado em 03/11/2026, no detalhe da loja do backoffice, dentro de **Assinatura externa**. O administrador pode conferir destinatário, assunto e texto antes de clicar em **Enviar aviso de renovação falhou**.

O serviço usa o e-mail cadastrado da loja e o remetente SES existente. O texto é gerado em português ou espanhol conforme o país da loja. Informa que o pagamento da renovação não foi confirmado e que o acesso voltou ao plano gratuito. Códigos de erro conhecidos podem gerar uma explicação; mensagens técnicas arbitrárias nunca são copiadas para o e-mail. Quando não existe causa específica disponível, o aviso informa essa ausência.

O texto explica que as configurações continuam salvas, sem necessidade de recadastro. Não promete exclusão de configurações nem uma pausa em três dias.

## Carência de um dia e aviso automático

Uma nova falha confirmada de renovação em produção inicia 24 horas de carência e registra um aviso automático. Durante esse prazo o acesso ao plano pago é mantido. Webhooks repetidos e novas tentativas do mesmo episódio não reiniciam o prazo nem geram novos avisos. Uma contratação inicial sem acesso pago anterior, sandbox, cancelamento, expiração terminal, estorno ou upgrade pendente não iniciam essa carência.

O e-mail durante a carência informa a data e a hora limite reais. Depois do prazo, a conciliação verifica o provedor novamente: se o pagamento continua sem confirmação, o acesso volta ao FREE; se foi confirmado, a carência é encerrada e o acesso continua pago. Se o provedor estiver indisponível durante essa verificação, o sistema tenta novamente em vez de usar indisponibilidade como prova de falta de pagamento. Uma verificação é executada a cada minuto, além dos webhooks e da conciliação recorrente. Assinaturas locais em ERROR agora também são conciliadas.

O registro de falha e a fila são persistentes. A chave provedor/contrato/cobrança impede duplicação do mesmo evento. O envio é reivindicado em transação separada antes de acessar SMTP. Pagamento recuperado, loja desinstalada ou contrato substituído descartam o aviso pendente. Falhas de validação/configuração anteriores ao SMTP recebem até três tentativas com intervalo de cinco minutos. Resultado indeterminado do SMTP vai para REVIEW, sem reenvio automático; uma queda após reivindicar deixa CLAIMED para conferência operacional, também sem reenvio cego. O backoffice mostra o estado do último aviso, e bloqueia o envio manual repetido do mesmo episódio já registrado automaticamente.

A automação vale para novas falhas detectadas depois do deploy; não reabre carência nem dispara retrospectivamente para lojas que já estavam no FREE, como a Dark Hunter. O botão manual continua disponível para esses casos anteriores.

O envio exige cobrança identificada e não paga em produção, ausência de cortesia e histórico de acesso pago. Funciona durante a carência ou depois do retorno ao FREE. Não fica disponível para sandbox, pagamento recuperado, tentativa inicial sem histórico ou upgrade pendente. Na Efí, aceita ERROR ou PAST_DUE durante a carência, e o e-mail contém o botão **Reativar meu plano Ultra** (ou o nome do plano do contrato). O formato HTML inclui alternativa em texto.

O botão abre `/admin/billing/reactivate?storeId=...`, com autenticação normal do app. A página Planos também oferece o mesmo acesso para quem precisar abrir o aplicativo pelo administrador da loja. O plano vem do contrato anterior; não há escolha nem pergunta de plano no fluxo de recuperação. A loja informa os dados de pagamento e usa **Atualizar cartão e reativar**, com o valor visível.

A reativação é uma substituição do contrato com erro por uma nova assinatura do mesmo plano, não uma troca do cartão no contrato existente. Ela gera uma nova cobrança e reinicia o ciclo mensal, conforme informado no formulário. O servidor confere novamente elegibilidade e plano dentro da transação e bloqueia alterações de plano ou uma assinatura já recuperada antes de chamar o fluxo Efí existente. Uma substituição durante a carência não estende o prazo em caso de nova recusa. O clique no e-mail e a abertura do formulário não cobram nem cancelam contratos; isso ocorre somente no POST com token do cartão. Dados brutos do cartão não passam pelo servidor.

A rota POST é protegida pela autenticação do backoffice e por um token de uso único vinculado à loja e à prévia. Antes do envio, o serviço consulta novamente plano e pagamento e rejeita mudanças de cobrança ou cadastro. O POST não recebe destinatário nem texto editável e não altera a assinatura.

## Dark Hunter: evidência consultada

Loja 7744400, consulta somente leitura ao banco e à API Efí em 03/11/2026:

- Assinatura inicial em 02/10/2026: Pro; cobrança 1071113309, R$ 29,99, paga.
- Upgrade Pro → Ultra registrado em 05/10/2026, origem PAYMENT_UPGRADE.
- Cobrança 1083280808 criada pela Efí em 02/11/2026: R$ 59,90, unpaid.
- Ultra → FREE registrado em 02/11/2026, origem PAYMENT_WEBHOOK.
- Cadastro local em ERROR, sem acesso pago; contrato Efí 1559342 ainda active e próxima execução informada em 02/12/2026.

A Efí manteve a recorrência no dia 2. A data do upgrade não é evidência de vencimento no dia 5. Contrato remoto active não comprova que a mensalidade de novembro foi paga. Não houve envio de e-mail, cancelamento, nova cobrança ou alteração de acesso durante a investigação.

## Validação e publicação

Suíte completa aprovada com Java 25: **834 testes, zero falhas, zero erros e um ignorado** (`mvn -o -q -Dskip.frontend=true test`). Inclui prazo fixo de 24 horas, ausência de extensão em webhooks/retries, exclusão de primeira contratação/upgrade/cancelamento/estorno/sandbox, migração e unicidade da fila, confirmação de pagamento, expiração de substituição ainda pendente, reivindicação antes de SMTP, descarte após recuperação, resultado indeterminado sem reenvio automático, bloqueio do aviso manual duplicado, alternativa texto/HTML e reativação do plano anterior sem seletor. `git diff --check` aprovado.

Implementação local: requer deploy da aplicação e aplicação da migração **V65__renewal_failure_grace_and_email.sql**, que adiciona os identificadores da falha à assinatura e cria a fila de e-mails, sem preencher retroativamente as falhas anteriores. Não exige publicação de scripts no Partner Portal. Não foi feito commit, push ou deploy como parte desta implementação. Nenhum e-mail real foi enviado.
