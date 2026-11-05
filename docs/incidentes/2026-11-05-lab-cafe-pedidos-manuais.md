# LAB Cafe — verificação do fluxo manual em 05/11/2026

Loja 959276. Investigação somente leitura; nenhum pedido, pagamento, permissão ou script publicado foi alterado.

## Evidência de produção

Consultas autenticadas realizadas pelo servidor usando a credencial existente, sem registrar tokens ou dados pessoais:

- `GET /v1/959276/draft_orders?per_page=10`: HTTP 403, `Missing required scope: read_draft_orders`.
- `GET /v1/959276/orders?per_page=15`: HTTP 200, 15 pedidos recentes. `order_origin` é nulo ou string vazia em todos. Esse resultado não permite classificar pedidos como manuais.
- Nessas respostas, `checkout_app_blocked=false`, `checkout_enabled=true` e mensagens de validação vazias. Isso não reproduz nem refuta o bloqueio do Worker no navegador e não identifica o pedido do chamado.

## Superfície documentada

- [Estado Order](https://tiendanube.dev/apps/nube-sdk/state/order): `order` só fica disponível no sucesso, após conclusão. Não há flag documentado de pedido manual no estado Cart/Checkout consultado, nem nos tipos instalados `@tiendanube/nube-sdk-types`.
- [Eventos do carrinho](https://tiendanube.dev/apps/nube-sdk/events/cart): `cart:add` aceita properties e `cart:remove` remove itens. Não foi encontrado comando documentado de edição de properties de uma linha existente. Remover/recriar itens não está validado para pedidos manuais, seus preços e descontos.
- [Eventos Order](https://tiendanube.dev/es-AR/apps/nube-sdk/events/order): `order:add:extra` permite persistir metadados chave/valor no pedido. É uma alternativa documentada para investigar o preenchimento no checkout, mas não equivale a editar `products[].properties`. Substitui o objeto extra inteiro a cada envio.
- [Draft Order](https://tiendanube.dev/es-AR/api/resources/2025-03/draft-order): rascunhos contêm checkout_url e products[].properties. A presença desses dados não comprova edição pelo cliente ou acesso com o token atual.
- `OrderPersonalization` atualmente lê as properties dos produtos, sem consumir order.extra. Adotar extra exige integração explícita no relatório e validação da persistência/visibilidade ao lojista.

## Conclusão e pendência concreta

Não há base para implementar dispensa automática apenas para pedidos manuais com os sinais atualmente confirmados. Também não está comprovada a gravação de respostas em um pedido manual já existente.

Para concluir a reprodução, obter o link enviado ao cliente de um pedido manual afetado, preferencialmente um pedido de teste. Conferir etapa, estado SDK, slots disponíveis e comportamento de persistência sem pagar/concluir um pedido real. Se for necessário consultar rascunhos via Admin API, será necessária autorização adicional `read_draft_orders`; não ampliar permissões automaticamente.

Candidato para uma solução que preserve obrigatoriedade: campos no checkout para itens com respostas ausentes, com persistência suportada e confirmação antes de liberar o pagamento. `order:add:extra` é candidato técnico, não uma solução já validada. Não prometer recuperação de valores nunca informados.
