# Link de arquivos no detalhe da venda da Nuvemshop

No Portal de Parceiros, abra o aplicativo e crie um **Link de Administrador**:

- Local: detalhe de pedido/venda.
- Texto PT: **Campos Personalizados — Baixar arquivos**.
- Texto ES: **Campos Personalizados — Descargar archivos**.
- URL: `https://campos-personalizados.wzhub.pro/nuvemshop/order-files`.

A Nuvemshop acrescenta `store`, `id` e `locale`. O aplicativo usa o ID do pedido para abrir `/admin/buyer-uploads?orderId=...`, mostrando somente seus arquivos. A sessão autenticada deve pertencer à mesma loja. Sem sessão, o destino é preservado por até dez minutos durante a autenticação Nexo ou a reconexão OAuth. Os parâmetros não concedem acesso nem autorizam downloads.

Os downloads continuam exigindo autenticação e propriedade da loja. A consulta é sempre filtrada pela loja autenticada, mesmo que o ID do pedido seja alterado na URL. O atalho não é colocado nas propriedades do item, no carrinho ou no checkout.

Isso não altera o bloco de informações nativo da Nuvemshop nem remove referências técnicas de uploads já gravadas no pedido. Os identificadores `NCF-FILE` são utilizados para vincular todos os arquivos aos itens.

Referência: [Links de Administrador na documentação Nuvemshop](https://tiendanube.github.io/api-documentation/guides/shipping-provider#how-to-create-an-admin-link).

O código foi preparado localmente. O cadastro do link no Portal de Parceiros e a validação do fluxo dentro da Nuvemshop ainda precisam ser realizados após publicar o backend.
