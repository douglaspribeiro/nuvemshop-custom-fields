# Investigação pendente: miniaturas na lista de produtos

Registrado em 12/11/2026 para investigar posteriormente: o lojista relatou que imagens em `/admin/products` às vezes não carregam.

A lista recebe `images` da API da Nuvemshop e usa a imagem de menor posição com URL HTTP(S). A interface carrega a imagem diretamente e apresenta um ícone quando ela falha. A causa específica ainda não foi identificada.

Para investigar, obter a loja e um produto afetado; comparar a resposta da API, o endereço enviado ao navegador e o resultado da requisição da imagem. Avaliar indisponibilidade, formato da URL, rede e políticas do navegador sem presumir a causa.
