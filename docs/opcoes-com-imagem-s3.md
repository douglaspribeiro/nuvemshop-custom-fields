# Opções com imagem: configuração e operação

O editor oferece `IMAGE_SELECT` (“Opções com imagem”). Free não inclui imagens; Essencial permite 1 produto com imagens e 3 opções por campo; Plus (`PREMIUM_PLUS`) permite 50 produtos com imagens e 8 opções por campo; Ultra permite produtos ilimitados e 25 opções por campo. Vários campos com imagens no mesmo produto consomem uma única vaga. Os limites são versionados no catálogo do backoffice e também respeitam os limites gerais de produtos/campos. Não há exceções para configurações antigas. Upload e salvamento validam o plano no servidor; a vitrine e as URLs públicas respeitam o plano efetivo, inclusive em suspensão ou downgrade. Configurações excedentes ficam ocultas; as imagens são apagadas quando a configuração/produto é removido, conforme o fluxo de limpeza existente.

Cada opção tem nome e uma imagem JPG ou PNG de até 5 MB e 20 megapixels. A loja pode reordenar, renomear, substituir imagens e remover opções.

Na vitrine, SDK, temas tradicionais e Patagonia exibem as imagens, nomes e seleção. “Ampliar imagem” abre a imagem maior em outra aba. O carrinho/pedido recebe o nome selecionado nas propriedades existentes, e não a URL do arquivo. O nome já registrado no pedido não é reescrito quando o catálogo muda. O envio de imagem pelo comprador continua como próxima etapa.

## Properties e ativação

A configuração está em `src/main/resources/application.yml`:

```yaml
images:
  s3:
    bucket: ${IMAGES_S3_BUCKET:}
    region: ${IMAGES_S3_REGION:us-east-2}
    prefix: ${IMAGES_S3_PREFIX:production/options}
  cleanup-delay-ms: ${IMAGES_CLEANUP_DELAY_MS:60000}
```

O bucket vem de `images.s3.bucket`; pode ser definido nos properties do ambiente ou por `IMAGES_S3_BUCKET`. Vazio desabilita o envio e apresenta a indisponibilidade no editor. A região precisa corresponder à do bucket. Use buckets ou prefixos separados entre homologação e produção, por exemplo `homolog/options` e `production/options`. O código não cria o bucket.

O S3 usa **as mesmas credenciais AWS já utilizadas pelo SQS**, conforme decisão de 05/10/2026. Ambos os clientes usam a cadeia padrão de credenciais do SDK no mesmo servidor: role, variáveis `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` (e `AWS_SESSION_TOKEN` quando aplicável) ou perfil AWS configurado. O cliente S3 e o assinador de URLs seguem essa mesma configuração; não há chaves específicas de S3 nos properties. Nenhuma credencial é enviada ao navegador. O bucket pode permanecer privado e com bloqueio de acesso público. O upload passa pelo backend, sem exigir CORS de escrita no bucket.

O usuário/role precisa de `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` e `s3:DeleteObjectVersion` nos objetos do prefixo; `s3:ListBucketVersions` e `s3:GetBucketVersioning` no bucket. Se a política restringir listagens por prefixo, permitir os prefixos usados pela aplicação. Caso o bucket use chave KMS própria, configurar também as permissões necessárias dessa chave. Não habilitar Object Lock/retenção obrigatória nos objetos desse fluxo, pois isso impediria a exclusão exigida pelo produto.

A migration **V47** acrescenta a configuração visual nos campos e o registro `personalization_images`. Faça o build normal do Maven, que também gera os bundles da vitrine. Antes de publicar, configurar bucket, região e credenciais/permissões e verificar upload, visualização e exclusão com um produto de teste no ambiente correspondente.

### Publicar o JavaScript da vitrine no Partner Portal

Atualizar o backend não substitui automaticamente o arquivo do Storefront SDK que a Nuvemshop hospeda em sua CDN. Depois do build, criar uma nova versão do script existente da vitrine no Partner Portal, enviar `src/main/frontend/dist/nuvemshop-storefront-sdk.js` e publicar essa versão. Preservar o identificador do script, a marcação NubeSDK e as permissões/configurações atuais. Associar/atualizar a versão na loja de teste conforme o fluxo do portal e confirmar no navegador qual arquivo da CDN está sendo carregado. Esta etapa não altera o script de checkout.

Antes de publicar a versão do script para as lojas, guarde o arquivo anterior e valide na loja de teste: um produto com os campos existentes (texto, número e lista), um produto com imagens, campos obrigatórios, troca de variante/quantidade, carrinho e checkout. Confira se os nomes escolhidos acompanham o item e se a compra não adiciona itens duplicados. Repita em desktop e celular. Os testes automatizados cobrem esses componentes, mas a validação no tema real continua necessária. Se houver regressão, republicar o arquivo anterior permite recuperar o comportamento anterior.

Os limites comerciais de imagens dependem da publicação do backend com a migração `V48__plan_image_limits.sql`. O script de vitrine recebe apenas os campos/opções permitidos pela API; não precisa conhecer o plano nem os limites do catálogo.

Diagnóstico de 05/10/2026 na LojaTeste1 (Morelia): o produto tinha três opções com imagem e a miniatura respondeu 200/JPEG. O bundle servido pelo backend continha `IMAGE_SELECT` e `imageOptions`, mas o script carregado pela loja, `store-front-v5/4.js`, não continha esses recursos. Nesse cenário, é necessário publicar a nova versão no portal; alterar o bucket ou suas permissões não atualiza o JavaScript hospedado pela Nuvemshop.

## Upload e entrega

O backend valida o conteúdo real do arquivo, formato e dimensões antes de enviar. Gera dois JPEGs sem metadados do original: prévia de até 1200 pixels e miniatura de até 240 pixels no maior lado, sem ampliar imagens pequenas. Transparência é convertida para fundo branco. O original enviado é descartado; não fica salvo como um terceiro objeto.

As chaves têm a forma `{prefix}/{storeId}/{productId}/{UUID}/preview.jpg` e `thumbnail.jpg`. Os nomes enviados pelo navegador não escolhem a chave. O banco guarda proprietário, vínculo com campo e chaves, sem binários ou URLs assinadas permanentes.

Antes de gravar no S3, o sistema confirma a loja/produto e persiste a intenção de upload em uma transação independente. Assim, uma falha posterior deixa um registro rastreável para limpeza. O registro passa de `PENDING` para `READY` somente quando ambos os objetos são enviados. Até salvar o campo, a imagem é temporária e sua prévia exige a sessão da loja. Há um limite de 100 uploads temporários por loja.

Salvar o campo valida nomes únicos, IDs, loja/produto e vínculo exclusivo da imagem com um campo. A gravação da configuração e os vínculos/intenções de exclusão participam da mesma transação. A edição preserva o formulário quando há erro. Uma substituição só desvincula a imagem antiga após salvar com sucesso.

A configuração pública retorna URLs do app. O app confirma loja ativa, configuração habilitada e imagem vinculada antes de redirecionar para uma URL GET assinada do S3, válida por 15 minutos. O redirecionamento usa `Cache-Control: no-store`; os objetos usam cache privado de até 60 segundos. Solicitações de exclusão definitiva bloqueiam a entrega pública. Imagens temporárias não são entregues pelo endpoint público.

## Limpeza obrigatória

Remover uma opção, substituir imagem, mudar para um tipo sem imagens, excluir campo ou remover produto/configuração marca os objetos inutilizados como `DELETING`. O webhook `product/deleted` faz o mesmo. A exclusão definitiva da loja também marca imagens temporárias e mantém a fila necessária para concluir a limpeza.

O job roda a cada 60 segundos por padrão. Exclusões ficam elegíveis após cinco minutos para evitar corrida com um upload já em andamento. Depois, ele apaga a prévia e a miniatura no S3 e remove o registro da fila somente se ambas as exclusões forem concluídas. Em caso de falha, preserva o registro, registra o erro e tenta novamente após cinco minutos. Não cria marcadores adicionais em buckets versionados: remove permanentemente todas as versões e marcadores da chave exata. Objetos vizinhos com prefixo semelhante não são excluídos.

Uploads `PENDING` sem conclusão expiram após uma hora. Imagens `READY` sem campo salvo expiram após 24 horas. A conciliação também encontra imagens cujo campo foi removido por SQL. O registro de upload precede todos os envios ao S3; essa conciliação usa o registro persistente, sem varrer nem apagar arquivos desconhecidos do bucket.

A fila não possui FK para loja/campo, para sobreviver à remoção desses registros. Mantém apenas IDs de propriedade e dados necessários para apagar os objetos; esses dados são removidos após a limpeza. A desinstalação do app preserva configurações conforme a política atual de reinstalação; a exclusão definitiva remove os arquivos. Cópias que já foram baixadas pelo navegador não são recuperáveis pelo sistema, e o cache pode durar até seu prazo configurado.

Monitorar `images.upload.failed`, `images.cleanup.retry` e `images.cleanup.deleted`, além da quantidade e idade de registros `DELETING`. Alterar bucket/região exige considerar objetos e exclusões ainda pendentes: cada registro guarda o bucket de origem, mas o cliente usa a região configurada no ambiente.

## Verificação

Os testes usam armazenamento S3 simulado e cobrem validação de arquivo, autenticação/propriedade, configuração pública, substituição com rollback, exclusão de opção/campo/produto/webhook/loja, uploads abandonados, remoção por SQL e novas tentativas. O adaptador S3 tem teste para exclusão de versões/marcadores da chave exata. Os testes da interface cobrem edição, falha de upload, seleção e propriedades do carrinho nos três caminhos de vitrine.

A validação em um bucket real depende da configuração do ambiente e deve ocorrer antes da publicação. Não foi criado bucket nem feita alteração em infraestrutura AWS durante a implementação local.
