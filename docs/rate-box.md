# Rate Box interno

O convite fica na visão geral (`/admin`), em um card que acompanha o visual do app. Não interrompe a configuração de produtos, não bloqueia a navegação e usa português/espanhol conforme o mercado da loja.

## Quem recebe

- Loja ativa, com plano pago e assinatura de produção `ACTIVE`, com acesso e período pago ainda válidos.
- Pelo menos sete dias completos de acesso pago confirmado na assinatura atual.
- Não exibir em plano gratuito, cortesia, override manual, sandbox, cobrança suspensa, falha de renovação, cancelamento ou acesso de operador pelo backoffice.
- Ao fechar sem avaliar, o convite pode reaparecer após 14 dias do fechamento. Após avaliar, o intervalo é de 21 dias da avaliação; sem resposta, de 21 dias da exibição. Nota 5 com clique em **Avaliar publicamente** encerra os convites para a loja, inclusive em outras sessões e assinaturas. Nota 5 sem clique segue o intervalo de 21 dias. Fechar o agradecimento após avaliar não reduz esse prazo. O controle fica no banco, não no navegador; uma trava por loja impede duplicação entre abas e sessões.

`payment_subscriptions.paid_started_at` é registrado na ativação confirmada de acesso pago e preservado nas renovações e upgrades do mesmo contrato. Trocar o contrato limpa a data e exige nova confirmação de pagamento. V83 só reconstrói datas de assinaturas antigas que tenham início do período atual e evento de ativação pago registrados: usa o maior valor entre essas datas e a criação do registro. Sem evidência suficiente, a data permanece desconhecida; a próxima confirmação de pagamento começa a contagem de forma conservadora.

## Fluxo

1. Quando a página está visível, o navegador solicita uma exibição. O servidor confere assinatura, idade e intervalo antes de registrar a data e liberar o card.
2. A seleção usa cinco rádios acessíveis por teclado. O lojista pode mudar a nota antes de clicar em **Enviar avaliação**.
3. Notas de 1 a 4 mostram o campo **O que podemos melhorar?**, opcional, com até 2.000 caracteres. Nota e comentário são internos.
4. Nota 5 é salva antes de mostrar agradecimento e convite opcional à avaliação pública. O aplicativo não publica nada automaticamente. O clique no link público é registrado e impede novos convites; ele não comprova que a avaliação foi publicada na Nuvemshop.
5. Fechar ou escolher **Agora não** dispensa o convite sem exigir nota. O fechamento continua funcionando sem rede; a data de exibição já está persistida.
6. O servidor revalida a elegibilidade ao salvar e recusa edição de uma avaliação concluída. Repetir exatamente a mesma requisição apenas confirma o resultado já salvo.

O link público informado para escrever uma avaliação é `https://www.nuvemshop.com.br/loja-aplicativos-nuvem/campos-personalizados7/rating`. A etapa pública depende da sessão e das regras da Nuvemshop.

## Persistência

V83 cria `merchant_ratings` com ID, `store_id`, `displayed_at`, nota, comentário, `rated_at`, `dismissed_at` e `public_review_clicked_at`. Não se registra uma nota ao apenas mostrar ou fechar o card. Avaliações são apagadas pelo fluxo de exclusão definitiva da loja, como os demais dados internos.

As avaliações podem ser consultadas no backoffice em `/backoffice/ratings`, com o mesmo estilo da tela de sugestões, filtros por nota e loja e as últimas 100 respostas. Há atalhos no suporte, nas sugestões e no detalhe da loja. A lista mostra nota, comentário, nome/ID da loja, plano atual, datas e clique no convite público.

A tabela contém tanto convites quanto avaliações. Para consultar avaliações recebidas, filtre `rated_at IS NOT NULL`; registros sem nota permitem conferir o controle do intervalo e dispensas. Não há criação automática de chamado ou envio de e-mail.

## Google Analytics

Eventos enviados pelo administrador do lojista quando o GA estiver configurado:

- `rate_box_view`: convite exibido após autorização do servidor.
- `rate_box_star_selected`: seleção de uma nota; parâmetro `rating`.
- `rate_box_submitted`: avaliação confirmada pelo servidor; `rating` e `has_comment`.
- `rate_box_dismissed`: fechamento; `after_rating` distingue o agradecimento do convite sem resposta.
- `rate_box_public_review_clicked`: clique para avaliar publicamente; não comprova publicação na Nuvemshop.
- `rate_box_error`: falha; `operation` identifica a etapa, sem enviar mensagens de erro.

Todos incluem `store_id`, `plan` e `app_area=merchant_admin`. Não enviamos comentários, nomes ou e-mails ao GA. O modo operador não exibe o Rate Box. Após publicar o backend, acompanhe os eventos em Tempo real; para analisar os parâmetros em relatórios personalizados, registre as dimensões correspondentes na propriedade GA4. Nenhuma alteração na propriedade do GA foi realizada por esta implementação.

## Validação e publicação

Testes locais cobrem limites exatos de sete, 14 e 21 dias, notas 1–5, comentário, exclusões por assinatura, autorização por loja, sessões paralelas, fechamento sem nota, repetição idempotente, recuperação de erro no navegador, PT/ES e exclusão de dados. O contrato MySQL verifica atualização de V82, instalação nova e mapeamentos Hibernate.

Publicar o backend, incluindo V83 e os assets administrativos. Nenhum script da vitrine ou checkout precisa ser publicado para o Rate Box. Não foi realizada validação visual em navegador nem alteração em produção.
