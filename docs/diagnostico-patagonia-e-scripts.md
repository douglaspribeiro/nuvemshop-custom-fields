# Diagnóstico do tema Patagonia e scripts da Tiendanube

Atualizado em 30/10/2026 com a investigação da Disegno.mx, loja 5656805.

## Comportamento da plataforma

Em 29/09/2026, o suporte da Nuvemshop confirmou no chamado #8216779 que a plataforma escolhe SDK no Patagonia e legado nos outros layouts. A ausência do legado nesse tema é esperada. Manter ambos associados não significa execução simultânea.

O NubeSDK roda em Worker, sem `window`/`document` ou injeção de scripts externos. O adaptador DOM `nuvemshop-patagonia.js` é histórico; não pode ser importado como solução do Worker.

## Falha reproduzida em 30/10

A correção anterior havia removido uma regra que limpava o slot no Patagonia, permitindo renderizar os campos pelo SDK. A validação e o envio ainda dependiam exclusivamente de `cart:before_update` e tinham sido testados com eventos simulados.

Na Disegno.mx, o botão nativo adicionou o produto sem os campos obrigatórios e sem entregar `cart:before_update`, apesar de o app habilitar `handle_cart_before_update`. O pedido #147 chegou pago sem propriedades. A existência dos campos na vitrine não comprovava o envio.

O envio explícito via `cart:add` com `properties` foi confirmado no checkout real. O estado do SDK na vitrine chegou a retornar propriedades vazias mesmo com os dados persistidos; conferir checkout/API, não apenas esse estado transitório.

## Correção preparada

- Ação própria do SDK no Patagonia para validar e adicionar com propriedades, variante e quantidade.
- Barreira no checkout com `has_cart_validation`: exige os campos obrigatórios de cada item e bloqueia o pagamento se faltarem valores ou se a política não puder ser consultada.
- O botão nativo permanece; a barreira do checkout impede que esse caminho produza pagamento sem os dados.

Os bundles foram testados substituindo as requisições apenas na sessão local do navegador. O preenchimento chegou ao checkout e foi aceito; um item vazio inserido pelo botão nativo foi recusado e o botão de pagamento ficou desabilitado. Ainda falta publicação no Partner Portal e repetição da validação com a versão publicada, incluindo celular e pedido final em ambiente autorizado.

Na loja investigada, os IDs observados são storefront **11470** (`store-front-v5`) e checkout **7145** (`checkout-v1`). A referência histórica ao storefront 9319 não deve ser usada sem verificar o portal.

Evidências, recuperação e instruções de publicação: [incidente Disegno.mx](incidentes/2026-10-30-disegno-pedido-sem-personalizacao.md).

Referências oficiais:

- [Arquitetura do NubeSDK](https://github.com/TiendaNube/nube-sdk).
- [Eventos do carrinho e validação no checkout](https://nuvemshop.dev/apps/nube-sdk/events/cart).
