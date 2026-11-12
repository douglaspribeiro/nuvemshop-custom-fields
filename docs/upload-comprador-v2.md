# Upload do comprador v2 (Beta)

- Pro: um arquivo por campo, até 10.000.000 bytes, cota de 2 GB.
- Ultra: máximo configurável de 1 a 5 arquivos por campo, até 20.000.000 bytes por arquivo, cota de 10 GB.
- Campo obrigatório exige pelo menos um arquivo. Seleção múltipla envia os arquivos em sequência, mostra andamento e permite exclusão individual.
- Arquivos existentes continuam com a localização, retenção e permissão de download originais. Downgrade limita novos envios; não apaga arquivos.
- V77 adiciona `personalization_fields.max_files`, inicialmente 1. Templates e histórico preservam a configuração.
- SDK: seletor nativo em iframe do próprio aplicativo, pois o SDK não oferece `getFormFile` nem seleção múltipla no Form.Field. O iframe usa a configuração pública do campo e transfere apenas referência e sessão ao Worker. Arquivos não atravessam o Worker. Mensagens recebidas pelo componente são processadas pelo canal do iframe do SDK.
- Cada referência é validada antes de adicionar ao carrinho; a associação ao pedido usa os dados autenticados da API da Nuvemshop.
- Limpeza padrão: uma hora entre execuções, até 100 arquivos por lote. Temporários: 24h; pedidos: 90 dias. A cota só é liberada após exclusão confirmada.

## Publicação

Implantar aplicação e migrações pendentes. Publicar o Storefront SDK compilado em `src/main/frontend/dist/nuvemshop-storefront-sdk.js`. Os arquivos `buyer-upload.html`, `buyer-upload-widget.js` e `buyer-upload-dom.js` acompanham a aplicação. Conferir permissões de iframe no Partner Portal/SDK, além de CSP do servidor. Validar em loja real a seleção de vários arquivos, carrinho, checkout, pedido e downloads. Testes locais não comprovam entrega pela CDN ou permissões do Partner.

As descrições da loja foram atualizadas nos arquivos de texto; sua publicação no Partner Portal é separada. O card `buyer-uploads-v2` reaparece para quem dispensou o anúncio anterior, respeitando a disponibilidade do armazenamento configurado.

## GA4

O editor emite `configuration_started` (primeira alteração por tipo de campo) e `configuration_submitted` (envio do formulário). Parâmetro `configuration_type`: TEXT, TEXTAREA, NUMBER, SELECT, CHECKBOX, IMAGE_SELECT ou FILE_UPLOAD. Não representa confirmação de salvamento. Sem rótulos, conteúdo ou nomes de arquivos. Sessões do operador não carregam o script. Para agrupar no GA4, cadastrar `configuration_type` como dimensão personalizada de evento. A configuração remota do GA4 não é alterada pelo deploy.

As propriedades internas do pedido contêm somente IDs compactos (até 224 caracteres para cinco arquivos). A descrição visível contém nomes, limitada a 500 caracteres. Não enviar URLs nem tamanhos no carrinho. Pedidos antigos com URL cortada só podem recuperar uma associação quando o prefixo de UUID é suficientemente longo e identifica exatamente um arquivo da mesma loja e produto.
