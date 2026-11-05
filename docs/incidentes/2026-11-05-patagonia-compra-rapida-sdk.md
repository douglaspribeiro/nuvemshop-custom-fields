# Patagonia: teste real da compra rápida do Storefront SDK

Data: 05/11/2026. Loja de teste: `cajaspersonalizadas2.mitiendanube.com`, ID 8398782, tema Patagonia. Produto 379192178, variante 1633744953. Campo SELECT obrigatório `Moido ou Granel`, opções En Grano e Molido.

**Status: ajuste local em validação; não publicar como solução completa.** O caminho explícito de personalização funciona até o checkout. O botão nativo ainda permite pular os campos nesta versão do tema.

## Ambiente e limites da evidência

O teste usou Playwright/Chrome, uma sessão autenticada isolada e a configuração real do produto. A autenticação foi renovada depois da troca do tema. A vitrine carregava `store-front-v5/16.js`. O bundle local foi carregado somente na sessão de teste, substituindo o módulo importado pelo Worker; nenhum script foi publicado no Partner Portal. A substituição do módulo foi confirmada, em vez de presumida a partir da tentativa de interceptar a URL de rede.

Não foram alteradas configurações de personalização nem finalizado pedido ou pagamento. Senha, cookies e URL/token de checkout não fazem parte deste documento nem dos arquivos versionados.

## Caminho personalizado validado

A implementação local acrescenta a ação **Personalizar** no card configurado do Patagonia. Ela abre o formulário em `modal_content`, inclusive para produto de variante única, cujo botão nativo adiciona diretamente e não abre `quickbuy:open`. Produtos sem campos mantêm a ação nativa. O suporte ao slot de compra rápida nativa continua para os temas que o oferecem e emitem o evento.

1. A tentativa vazia pelo botão do app foi impedida, sem adicionar produto.
2. Pela ação Personalizar, foi adicionada 1 unidade En Grano e depois 1 Molido.
3. Pela página do produto, usando Adicionar com personalização, foram adicionadas mais 5 unidades Molido.
4. O carrinho mostrou duas linhas com as respostas corretas: 6 Molido e 1 En Grano.
5. O estado persistido do checkout confirmou `quantity=6, properties={"Moido ou Granel":"Molido"}` e `quantity=1, properties={"Moido ou Granel":"En Grano"}`.
6. “Ítems Personalizados” mostrou as quantidades 6 e 1 com suas respectivas escolhas. Não houve bloqueio de personalização no checkout.
7. No viewport de 390 × 844, o modal, seletor, quantidade e botões ficaram visíveis dentro da largura da tela. Essa verificação de layout não equivale a uma compra concluída em dispositivo físico.

No estado SDK da vitrine, as duas linhas apareceram com `properties=[]`, apesar de a interface nativa do carrinho e o estado do checkout exibirem as respostas corretas. Portanto, esse estado transitório não é suficiente para concluir perda de dados. As respostas vieram na confirmação `cart:add:success`, e a verificação final usou o checkout.

Evidências locais desta máquina: `/tmp/cajas-sdk-patagonia-acceptance.json`, `/tmp/cajas-sdk-modal-grano.png`, `/tmp/cajas-sdk-modal-mobile.png`, `/tmp/cajas-sdk-checkout-properties.png`. Não são arquivos de publicação.

## Pendência: compra pelo botão nativo

O botão Comprar de variante única adicionou diretamente, sem abrir a personalização. Foram observados `cart:update` e `cart:add:success`; não foi observado `cart:before_update` nem `quickbuy:open` nessa operação.

A configuração foi investigada separadamente com o próprio objeto SDK do Worker. Após reenviar `config:set` com `handle_cart_before_update=true`, `getState().config` do Worker retornou true, enquanto o estado do runtime na página continuou false. A causa dessa divergência requer investigação na integração/runtime da plataforma; não atribuir a uma configuração do Partner sem prova.

Foi tentado substituir visualmente apenas o controle nativo adjacente ao formulário do app. O SDK rejeitou o seletor de ancestral com a mensagem `Selector ":has(&) (ancestor climb)" is not supported.` A tentativa foi removida do código. A implementação não manipula o DOM do tema nem depende de CSS rejeitado pelo SDK.

A ação própria mantém uma rota funcional para personalizar; sua existência não protege o botão nativo. Não reintroduzir bloqueio no checkout para compensar essa pendência.

Os itens dos dois testes (fluxo personalizado e reprodução nativa sem respostas) foram removidos ao final pelos controles do próprio carrinho. A recarga confirmou o carrinho da sessão de diagnóstico vazio.

## Próximo aceite necessário

Confirmar com a plataforma como fazer o opt-in do Worker chegar ao runtime da página e obter `cart:before_update` antes da compra nativa no Patagonia. Repetir o botão nativo vazio/preenchido e as edições de quantidade, além da ação do app, depois da correção do runtime ou de uma adaptação de tema suportada. A etapa não está encerrada enquanto um caminho nativo puder ignorar campos obrigatórios.

Referências oficiais consultadas: [eventos do carrinho e opt-in](https://tiendanube.dev/apps/nube-sdk/events/cart), [eventos de compra rápida](https://tiendanube.dev/apps/nube-sdk/events/ui-and-custom), [slots de Storefront](https://tiendanube.dev/es-AR/apps/nube-sdk/slots/storefront-slots). A documentação descreve separação de linhas por propriedades; esse comportamento foi confirmado no checkout do caminho explícito neste teste.
