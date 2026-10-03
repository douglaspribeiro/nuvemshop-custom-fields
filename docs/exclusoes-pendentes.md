# Desinstalações e exclusões pendentes

Esta versão vale para os próximos eventos após o deploy. Não recupera cadastros
anteriores, não reconstrói contatos apagados e não altera registros em produção
durante o desenvolvimento.

## Recebimento

- `app/uninstalled`: preserva o cadastro, revoga acesso/token e registra a saída.
- `store/redact`: registra a primeira data em `erasure_requested_at`, marca a
  exclusão como pendente e revoga acesso/token. Não apaga imediatamente.
- Retentativas não mudam a data original nem duplicam os totais de saídas.
- Um evento posterior de desinstalação não cancela a exclusão pendente.
- Eventos de lojas já apagadas não recriam cadastros.

## Backoffice

Abra **Lojas → Desinstaladas**. A mesma lista inclui desinstalações e exclusões
pendentes. Não exige datas nem um prazo de sete dias. A data de recebimento fica
no banco; não há job de exclusão automática.

Cada cadastro inativo permite **Excluir definitivamente**, com confirmação
explícita. A ação remove a loja e seus registros vinculados, incluindo campos,
chamados, sugestões, pagamentos locais, ajustes de upgrade e reconquistas.
Somente totais anônimos de saídas permanecem. Lojas ativas não podem ser apagadas
por esse botão. As ações exigem sessão de backoffice e token do formulário.

**Motivo/justificativa**: os webhooks atuais não fornecem esses campos. A lista
mostra respostas obtidas pela campanha de reconquista quando disponíveis ou
permite registrar manualmente os dados copiados do painel da Nuvemshop. O registro
manual é identificado como tal e não envia mensagens ao lojista. Não se infere
um motivo a partir do evento `redact`.

**Disparar reconquista**: disponível para lojas desinstaladas, inclusive com pedido
de exclusão pendente. Nesse caso, abra **Reconquista manual** e confirme a autorização
do contato conforme a orientação obtida junto ao suporte. A autorização fica vinculada
à data do pedido atual em `winback_emails.erasure_contact_request_at`; não cancela a
exclusão nem autoriza contatos automáticos/ofertas posteriores. Requer
`winback.mail-enabled=true`, SMTP/SES e Configuration
Set configurados. O disparo manual não depende de uma nova mensagem SQS. Respeita
opt-out e não repete o e-mail FEEDBACK no mesmo ciclo, inclusive após falha de
resultado incerto do SMTP. Confira o status na página Reconquistas. A configuração
existente da automação não é desativada por este botão; pedidos de exclusão bloqueiam
os envios automáticos e links não autorizados de campanhas anteriores. O formulário
do feedback manual autorizado continua disponível para colher motivo e justificativa;
não gera ofertas nem follow-ups automáticos enquanto a exclusão estiver pendente.

**Reinstalação pendente**: após uma nova autorização OAuth válida, a loja é reativada,
`erasure_requested_at` é limpo e o cadastro sai da lista de exclusão pendente.
Configurações e campos existentes são preservados. O cancelamento da pendência é
registrado em `lgpd.erasure_cancelled_by_reinstallation`. Campanhas antigas são
marcadas como reinstaladas e não continuam disparando reconquista. Não se restaura
automaticamente um plano pago somente por reinstalar.

## Limite operacional e privacidade

A fila manual é uma etapa de processamento do pedido, não uma autorização para
retenção por tempo indeterminado ou marketing. Operadores devem revisar e concluir
as exclusões; a ausência de agendamento não garante atendimento dos prazos
aplicáveis. Valide este procedimento com a plataforma/responsável por privacidade
antes de utilizá-lo em produção. A
[documentação da Nuvemshop](https://tiendanube.github.io/api-documentation/v1/resources/webhook)
define `store/redact` como solicitação de exclusão dos dados da loja.

O responsável pelo produto informou ter obtido orientação do suporte permitindo
uma consulta manual por e-mail sobre melhorias. O sistema registra uma confirmação
explícita por loja para esse caso, mas essa confirmação operacional não comprova
consentimento do titular nem substitui a documentação da orientação ou revisão dos
fundamentos aplicáveis. Não há disparos automáticos para cadastros com exclusão pendente.
