# Próxima funcionalidade: envio de imagens pelo comprador

Status: planejamento, ainda indisponível. Próxima etapa após o material da loja de aplicativos.

## Objetivo e experiência

Permitir que o comprador envie uma foto, logotipo ou arte na página do produto.
O campo é distinto de `IMAGE_SELECT`, que permite selecionar imagens cadastradas
pelo lojista. O lojista configura o nome e a obrigatoriedade do novo campo.

O comprador vê uma prévia e pode substituir/remover o arquivo antes de comprar.
A compra aguarda o envio e a validação quando o campo for obrigatório. Carrinho
e checkout mostram a identificação da imagem; o pedido mantém um vínculo durável
para o lojista acessar o arquivo para produção. Tratar múltiplos itens do mesmo
produto com imagens diferentes sem misturar arquivos.

## Limites por plano

Disponibilidade e limites seguirão o plano contratado. **As quantidades ainda não
foram definidas** e não devem ser copiadas automaticamente de `IMAGE_SELECT`.
Prever parâmetros separados e editáveis no catálogo versionado:

- Produtos que podem ter campos de upload.
- Campos de upload por produto e imagens por campo/item de compra.
- Tamanho máximo de arquivo e dimensões permitidas.
- Cota de armazenamento ou volume de envios, caso adotada.
- Retenção de arquivos temporários e concluídos.

Proposta a avaliar: Free sem upload; Essencial com uso inicial limitado; Pro e Ultra
com capacidades crescentes. Definir os números antes de liberar o recurso.

## Armazenamento e associação

- S3 privado, bucket/região/prefixo pelas properties e credenciais AWS existentes.
- Identificador opaco gerado pelo backend, vinculado à loja, produto,
  configuração/campo e sessão de compra. URLs assinadas não são o único vínculo no pedido.
- Validar formato real, tamanho e dimensões no backend; restringir inicialmente
  a formatos de imagem aprovados. Separar prévia de arquivo para produção para
  preservar a qualidade necessária à impressão.
- Confirmar associação ao pedido por webhook e conciliação idempotente,
  considerando abandono, falhas e eventos fora de ordem.
- Leitura do original exige autorização da loja correspondente. O comprador
  só pode editar/remover seus uploads ainda não finalizados.
- Controle de taxa e cota nos envios anônimos; nunca expor credenciais AWS ou escrita ilimitada.

## Exclusão e retenção

- Limpar temporários abandonados, inválidos, removidos ou substituídos.
- Ao remover produto, campo ou configuração, apagar as imagens vinculadas,
  conforme o requisito existente. Antes da liberação, informar o impacto dessa
  remoção no acesso a arquivos de pedidos anteriores.
- Exclusão definitiva da loja apaga os arquivos vinculados.
- Usar exclusão persistente com novas tentativas, contemplando original,
  miniaturas e versões S3 quando houver versionamento.
- Definir e informar a retenção dos arquivos de pedidos. Não aplicar a expiração
  curta de temporários aos arquivos necessários para produção.

## Validação antes de liberar

Upload válido/inválido, troca/remoção, obrigatoriedade, interrupção de rede,
múltiplos itens, associação ao pedido, isolamento entre lojas/compradores,
limites por plano, downgrade, abandono e exclusões. Validar NubeSDK, legado,
Patagonia, carrinho e checkout.

## Comunicação pública desta etapa

“Upload de imagens pelo comprador — em desenvolvimento. O recurso ainda não está
disponível e terá disponibilidade e limites conforme o plano contratado. Os limites
específicos e a data de lançamento ainda serão informados.”
