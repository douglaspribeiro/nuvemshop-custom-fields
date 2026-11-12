# Tolerância para mudanças de plano por webhook

Quando um webhook de renovação faria uma loja paga perder acesso, o aplicativo mantém o plano por uma hora a partir da primeira tentativa de downgrade. Notificações repetidas não estendem o prazo. A data de término e as datas de cobrança informadas pelo provedor continuam sendo dados do provedor; a espera é registrada separadamente. Cancelamentos recebidos por webhook são processados imediatamente, inclusive se já houver uma espera pendente; o período pago continua sujeito às regras existentes de cancelamento agendado.

Se uma renovação aprovada for confirmada antes do prazo, a espera é cancelada e o plano permanece ativo, sem uma transição intermediária para Free. No fim da hora, uma rotina consulta o provedor novamente: uma renovação confirmada preserva o acesso; caso contrário, o plano passa para Free. A rotina roda a cada minuto por padrão. A desinstalação continua revogando o acesso imediatamente.

A tolerância de 24 horas já existente para falha de renovação confirmada continua sendo aplicada aos casos que atendem suas condições. O e-mail automático dessa falha passa a aguardar ao menos uma hora e é descartado se a renovação tiver sido confirmada antes do envio. Uma tentativa de SMTP com resultado incerto não é reenviada automaticamente.

A migration V80 registra o prazo pendente e adia avisos pendentes recentes. Não altera datas contratuais ou cobranças no provedor. A publicação e a verificação de um caso real são separadas dos testes locais.
