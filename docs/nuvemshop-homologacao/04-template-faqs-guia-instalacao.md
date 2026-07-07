# Template Nuvemshop de FAQs e guia tutorial de instalacao

Este arquivo atende ao requisito obrigatorio de publicacao: "Template Nuvemshop de FAQs (com atencao ao Guia Tutorial de Instalacao)".

## FAQs

### O que o app Campos Personalizados faz?

O app permite adicionar campos personalizados em produtos da loja, como nome, numero, mensagem, data, medida, observacao ou selecao de opcoes. As respostas do comprador sao enviadas junto com o item no carrinho e ficam disponiveis no pedido da Nuvemshop.

### Para quem o app e indicado?

Para lojas que vendem produtos personalizados, como camisetas, canecas, brindes, convites, itens gravados, produtos sob medida e kits com informacoes especificas do cliente.

### Quais tipos de campo o app suporta?

O app suporta texto curto, numero, area de texto e lista de selecao. Cada campo pode ter label, obrigatoriedade, placeholder, limite de tamanho e validacoes conforme o tipo.

### Os dados aparecem no pedido?

Sim. Os campos sao enviados usando `properties[...]`, mecanismo da propria Nuvemshop para acompanhar informacoes extras no item do carrinho e no pedido.

### O app altera o checkout?

O app adiciona os dados da personalizacao ao item antes do checkout. Ele nao substitui o checkout da Nuvemshop e nao processa pagamentos do pedido.

### O app cobra valor extra por opcao personalizada?

Na versao atual, o foco e coletar as informacoes da personalizacao. Cobranca extra por campo ou opcao depende de definicao tecnica/comercial separada.

### Quais planos existem?

- `FREE`: 1 produto personalizado e 1 campo por produto.
- `PREMIUM`: ate 10 produtos personalizados e ate 3 campos por produto.
- `PREMIUM_PLUS`: produtos e campos ilimitados.

### Como funcionam os planos pagos?

Quando o billing automatico estiver ativo, o lojista escolhe um plano pago em `/admin/billing`. O app solicita a assinatura pela Billing API da Nuvemshop e somente libera o plano local depois da confirmacao da plataforma.

### O que acontece se uma assinatura for suspensa?

Se a Nuvemshop enviar `app/suspended`, o app marca a cobranca como suspensa e bloqueia o acesso premium. Quando receber `app/resumed`, o app reativa o acesso e sincroniza a assinatura.

### Como recebo suporte?

O lojista pode acessar a pagina de ajuda dentro do app em `/admin/help` e a pagina publica de suporte em `/support/`.

## Guia Tutorial de Instalacao

### Antes de comecar

- Tenha permissao de administrador na loja Nuvemshop.
- Confirme que sua loja esta ativa.
- Se estiver reinstalando, use o mesmo navegador onde esta logado na loja para evitar escolher a conta errada.

### Passo a passo

1. Acesse o link de instalacao do app.
2. A Nuvemshop exibira a tela de autorizacao com as permissoes solicitadas.
3. Revise as permissoes e confirme a instalacao.
4. Ao retornar ao app, aguarde a abertura do painel.
5. Acesse `Produtos` ou `Comecar rapido`.
6. Escolha o produto que recebera campos personalizados.
7. Crie os campos ou aplique um template de nicho.
8. Salve as alteracoes.
9. Abra o produto na vitrine e teste a compra adicionando o item ao carrinho.
10. Verifique se as informacoes aparecem no carrinho/pedido.

### Instalacao com Nexo/painel embutido

Se o app abrir dentro do painel da Nuvemshop, a tela embutida pode redirecionar para `/install` quando a sessao ou token estiverem ausentes. Esse fluxo reconecta a loja e reinstala scripts/webhooks de forma idempotente.

### Reinstalacao

Use o link de instalacao novamente. O app atualiza o token da loja, preserva as regras existentes quando o `store_id` e o mesmo e garante novamente os scripts e webhooks necessarios.

### Desinstalacao

Ao desinstalar pela Nuvemshop, o app recebe `app/uninstalled`, marca a loja como desinstalada, limpa dados locais de assinatura e tenta remover os scripts da vitrine.

### Problemas comuns

| Problema | Causa provavel | Como resolver |
| --- | --- | --- |
| Campos nao aparecem na vitrine | Script ainda nao instalado, produto sem regra ativa ou limite do plano atingido. | Reinstale/reconecte o app e confira o produto em `/admin/products`. |
| Produto nao aparece no painel | Token sem acesso ou falha temporaria na API. | Reinstale o app e tente novamente. |
| Pedido nao aparece no dashboard | Loja instalada sem `read_orders` ou sem pedidos recentes com personalizacao. | Reinstale autorizando os escopos e faca um pedido de teste. |
| Assinatura nao atualiza | Billing desativado, mercado sem preco configurado ou erro da Billing API. | Conferir `/admin/billing`, logs e configuracoes de billing. |
| App premium ficou bloqueado | Nuvemshop enviou `app/suspended`. | Regularizar a assinatura; o webhook `app/resumed` reativa o plano. |
