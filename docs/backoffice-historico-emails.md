# Histórico de e-mails por loja

No detalhe da loja, **Histórico de e-mails** abre `/backoffice/stores/{storeId}/emails`, uma página exclusiva do backoffice com 25 registros por página, mais recentes primeiro. A página mostra data, tipo, destinatário, assunto, conteúdo em texto e situação do envio. Conteúdo é escapado; a resposta HTTP usa `Cache-Control: no-store`.

A migração V69 cria o histórico e importa os registros de envio já existentes nas filas de ativação, reconquista, falha de renovação e pausa de configuração. Não importa tentativas simuladas ou apenas agendadas. Os dados de assunto, destinatário e mensagem antigos que não foram guardados aparecem como não registrados; não se usa o cadastro atual ou o template atual para reconstruí-los. E-mails manuais e notificações de suporte anteriores à implantação não possuem esse histórico recuperável.

O transporte SES compartilhado passa a registrar as novas tentativas antes do SMTP, incluindo os e-mails manuais, avisos com ação, ativação, reconquista e respostas de suporte. A associação é feita pelo ID da loja ou do chamado, sem procurar lojas pelo endereço de e-mail. O snapshot conserva a alternativa em texto, com parâmetros `token` e `access_token` ocultos. A exclusão dos dados da loja também remove esse histórico.

- **Envio registrado:** o transporte aceitou a mensagem, ou a fila histórica tinha confirmação de envio salva.
- **Sem confirmação de envio:** tentativa registrada antes de o processo confirmar o resultado.
- **Resultado incerto:** o transporte retornou erro; isso não autoriza reenvio automático.
- **Entrega confirmada / devolução / reclamação:** obtidos dos eventos SES já persistidos para ativação e reconquista. Nos demais casos, aparece **Sem confirmação de entrega**. Não se infere entrega a partir de aceitação SMTP.

Se o registro inicial falhar, o transporte não envia. Se a atualização do histórico falhar depois da aceitação SMTP, o envio não é transformado em erro que possa provocar duplicação. O registro fica sem confirmação e o problema é registrado em log sem conteúdo ou destinatário. A tela não oferece reenvio.

A nova tabela exige a migração V69 antes da execução da aplicação atualizada. Implementação e validação locais não significam implantação no servidor.
