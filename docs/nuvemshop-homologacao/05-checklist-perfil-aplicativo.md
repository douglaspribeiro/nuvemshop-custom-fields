# Checklist do perfil do aplicativo no painel de parceiros

Este arquivo cobre o item complementar solicitado: completar todos os campos do perfil do aplicativo no painel de parceiros.

## Campos de publicacao

- Nome do app: Campos Personalizados.
- Descricao curta: App para criar campos personalizados por produto na Nuvemshop.
- Descricao completa: explicar que o lojista configura campos por produto e as respostas seguem no carrinho/pedido via `properties[...]`.
- Categoria: personalizacao, produtividade ou operacao de loja, conforme opcoes disponiveis no painel.
- Mercados: BR, AR, MX, CO e/ou CL, conforme precos e suporte liberados.
- Idiomas de suporte: portugues inicialmente; adicionar espanhol se publicar em AR, MX, CO ou CL.
- URL de instalacao: endpoint publico `/install`.
- URL de callback OAuth: endpoint publico `/oauth/callback`.
- URL de suporte: pagina publica `/support/`.
- URL de politica de privacidade: pagina publica `/privacy/`.
- Email de suporte: informar canal operacional monitorado.
- URL do site ou landing page do app, se exigido.

## Arquivos e midias

- Logo do app nos tamanhos solicitados.
- Icone quadrado nos tamanhos solicitados.
- Screenshots do painel do app.
- Screenshot da configuracao de campos.
- Screenshot da vitrine com campos renderizados.
- Screenshot do pedido/carrinho com dados de personalizacao.
- Video de demonstracao conforme [roteiro](./02-video-demonstracao.md).

## Scopes cadastrados

- `read_store`
- `read_products`
- `read_orders`
- `read_scripts`
- `write_scripts`
- `billing`, somente se planos pagos forem publicados com assinatura automatica.

## URLs tecnicas

- `APP_BASE_URL` deve usar HTTPS publico.
- `NUVEMSHOP_REDIRECT_URI` deve usar HTTPS publico e apontar para `/oauth/callback`.
- Webhooks devem apontar para `/webhooks/nuvemshop`.
- Paginas publicas `/privacy/` e `/support/` devem funcionar sem login.

## Evidencias para anexar

- Diagrama de sequencia e escopos.
- Video de demonstracao.
- Requisitos tecnicos de assinatura, se houver planos pagos.
- Template de FAQs com guia de instalacao.
- Prints das telas principais.
- Confirmacao dos campos preenchidos no perfil do app.
