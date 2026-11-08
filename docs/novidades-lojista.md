# Novidades do app na página inicial do lojista

A página `/admin` exibe um bloco discreto com funcionalidades novas e links diretos. O objetivo é apresentar melhorias, inclusive aos lojistas de planos que ainda não dão acesso a elas.

## Comportamento

- Não há filtro por plano nem indicação de plano nos cards. As regras de acesso continuam sendo aplicadas pelas funcionalidades de destino.
- Cada novidade tem título, descrição curta do benefício e link de ação.
- O bloco não é renderizado quando não há novidades elegíveis.
- Atualmente, templates e uploads seguem `HOMOLOG_ALLOWED_STORE_IDS`: `0`, vazio ou ausente bloqueiam; `*` libera; uma lista autoriza apenas seus IDs. Consulte [a configuração universal de homologação](homologacao-funcionalidades.md).
- A novidade de upload só aparece quando o armazenamento está habilitado. Ela é apresentada a todos os planos das lojas autorizadas, sem conceder acesso ao upload.
- Textos em português e espanhol usam os arquivos de mensagens existentes.

## Novidades cadastradas

| ID | Funcionalidade | Destino |
|---|---|---|
| `templates-v1` | Templates reutilizáveis | `/admin/templates` |
| `buyer-uploads-v1` | Arquivos do comprador | `/admin/products` |

## Como cadastrar uma novidade

1. Adicione um `Item(id, messageKey, href)` em `MerchantNewsService.forStore`. Use um ID estável e exclusivo e um destino interno válido. A ordem da lista determina a ordem dos cards.
2. Cadastre `messageKey.title`, `messageKey.description` e `messageKey.action` em `messages.properties` e `messages_es.properties`.
3. Aplique somente as condições de disponibilidade/publicação necessárias. Não use o plano como filtro de divulgação nem acrescente selos de plano aos cards.
4. Mantenha a seleção curta, preferencialmente até três itens. Esse é um critério editorial; o código não impõe limite automático.
5. Atualize esta documentação e os testes de elegibilidade quando necessário.

O filtro universal está hoje na entrada de `forStore`, pois as duas novidades cadastradas estão em homologação. Ao acrescentar funcionalidades já públicas, ajuste a seleção para que apenas os itens em homologação dependam desse filtro. Não restrinja uma novidade pública acidentalmente.

## Dispensar e reapresentar

O botão “Dispensar novidades” guarda uma marca por loja e por ID no `localStorage` do navegador, usando `ncf-news:{storeId}:{newsId}`. Novidades dispensadas não reaparecem nesse navegador, mas itens com IDs novos continuam aparecendo. Quando todos os itens estão dispensados, o bloco fica oculto.

- A preferência não é sincronizada com o servidor, outros navegadores ou dispositivos.
- Se o armazenamento do navegador estiver bloqueado, o botão ainda oculta o bloco na página atual; a preferência pode se perder ao recarregar.
- Para uma atualização relevante que deva reaparecer, use uma nova versão do ID, por exemplo `templates-v2`. Uma alteração apenas de texto não exige mudar o ID.
- Remover um item da lista deixa de anunciá-lo sem modificar sua funcionalidade ou os dados da loja.

## Arquivos e validação

- `MerchantNewsService.java`: seleção das novidades, IDs e destinos.
- `AdminController.java` e `templates/admin/index.html`: modelo e renderização da página inicial.
- `static/assets/merchant-news.js`: dispensa e isolamento por loja/novidade.
- `static/styles/merchant.css`: apresentação responsiva.
- Testes focados: `MerchantNewsServiceTest`, `MerchantOverviewProgressTest`, `AdminPendingEfiReconcileTest` e `src/admin/merchant-news.test.ts`.

Validação local: 10 testes Java e 3 JavaScript passaram. Não foi realizada verificação visual no navegador nem publicação em produção.
