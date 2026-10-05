# Planejamento: opções com imagem

Data: 05/10/2026. Status: implementação local da primeira etapa; configuração S3 e publicação pendentes.

Os detalhes do fluxo implementado, limites e operação estão em [Opções com imagem: configuração e operação](opcoes-com-imagem-s3.md).

## Objetivo e sequência

Permitir que o comprador escolha uma estampa, capa ou outro modelo por imagem. A primeira etapa é **Opções com imagem**, cadastradas pelo lojista. O envio de foto, logo ou arte pelo comprador fica registrado como próxima etapa, com planejamento próprio de armazenamento, acesso pela loja e retenção.

O relato da Papel e Propósito orienta a seleção visual de estampas/capas. O relato da Láser Mataderos orienta o envio de arquivos pelo comprador. Há um relato explícito para cada necessidade; isso indica casos de uso, mas ainda não permite estimar adesão ou impacto na conversão.

## Experiência do lojista

- Criar um campo como “Escolha sua capa”, com opções contendo nome e imagem.
- Enviar imagens JPG ou PNG, visualizar uma prévia e organizar a ordem das opções.
- Adicionar, editar, substituir a imagem e remover opções.
- Definir se a escolha é obrigatória.
- Receber mensagens claras de arquivo inválido, falha no envio ou opção incompleta, preservando os demais dados digitados.

O armazenamento das imagens será no **Amazon S3**, conforme decisão do lojista responsável pelo sistema. Os limites de quantidade, tamanho e dimensões das imagens e eventual disponibilidade por plano devem ser definidos durante o desenho técnico. A funcionalidade não foi vinculada ao Ultra nesta decisão.

## Armazenamento no S3

- Salvar os arquivos no S3; manter no banco os nomes das opções, vínculos de propriedade e identificadores dos objetos. Não armazenar os binários no banco nem depender do disco local da aplicação para persistência.
- Usar chaves geradas pelo backend, separadas por ambiente e loja, com identificador único por imagem. Não usar o nome enviado pelo navegador como chave nem permitir que o cliente escolha um objeto de outra loja.
- Manter as credenciais AWS no servidor. O fluxo de upload será definido no desenho técnico: por backend ou por URL pré-assinada de curta duração. O S3 permite [upload por URL pré-assinada sem fornecer credenciais AWS ao navegador](https://docs.aws.amazon.com/AmazonS3/latest/userguide/PresignedUrlUploadObject.html).
- Validar conteúdo, tamanho e dimensões antes de disponibilizar a imagem na vitrine. Se o upload for direto, usar uma área temporária privada e só publicar a imagem validada; o sucesso do upload sozinho não confirma uma opção salva.
- Gerar imagens adequadas à miniatura e à ampliação. Registrar todos os objetos derivados para apagar tanto o original quanto as versões geradas quando a imagem for removida.
- Definir bucket, região, permissões, entrega das imagens na vitrine e configuração de CORS, caso haja upload direto, antes da publicação. O bucket não precisa permitir escrita pública.
- Registrar as exclusões do S3 em uma fila persistente, com novas tentativas e conciliação de órfãos. O registro da exclusão deve sobreviver à remoção da configuração e à exclusão definitiva da loja, contendo somente os dados necessários para concluir a limpeza.
- Se o bucket tiver versionamento habilitado ou suspenso, contemplar a remoção permanente das versões pertencentes à imagem. Um `DELETE` sem `versionId` pode apenas criar um marcador e manter o arquivo armazenado, conforme a [documentação de exclusão de versões do S3](https://docs.aws.amazon.com/AmazonS3/latest/userguide/DeletingObjectVersions.html).
- Definir expiração dos temporários, prazo de entrega das URLs e eventual tratamento do cache/CDN. Apagar o objeto de origem não deve ser confundido com invalidar cópias já armazenadas em cache.

## Experiência do comprador

- Ver miniaturas acompanhadas do nome da opção, com layout adequado ao celular.
- Ampliar a imagem para conferir os detalhes.
- Escolher uma opção por campo e identificar claramente a opção selecionada, inclusive com teclado e leitor de tela.
- Conseguir identificar a opção pelo nome caso a imagem não carregue.
- Preencher os campos obrigatórios antes de adicionar ao carrinho.

O nome selecionado acompanha a personalização no carrinho e no pedido, por exemplo, “Capa: Floral azul”. A edição ou exclusão posterior do catálogo não deve reescrever o texto já registrado no pedido. Esta etapa não acrescenta controle de estoque, preço por opção ou novas variantes de produto.

## Exclusão das imagens: requisito obrigatório

O armazenamento deve acompanhar o ciclo de vida da configuração. Excluir somente o vínculo no banco, deixando o arquivo salvo, não atende ao requisito.

| Ação | Limpeza exigida |
| --- | --- |
| Remover uma opção | Apagar a imagem que deixou de ser utilizada. |
| Substituir a imagem de uma opção | Após salvar a nova imagem e a configuração com sucesso, apagar a anterior. |
| Excluir o campo | Apagar todas as imagens pertencentes às opções do campo. |
| Alterar o campo para um tipo sem imagens | Apagar as imagens que deixaram de integrar a configuração. |
| Remover o produto da configuração no app | Apagar as imagens dos campos removidos. |
| Excluir o produto na Nuvemshop | O processamento do evento `product/deleted` deve apagar as imagens da configuração removida. |
| Excluir definitivamente os dados da loja | Apagar todas as imagens pertencentes à loja, incluindo arquivos temporários. |
| Abandonar ou falhar no cadastro/envio | Limpar arquivos temporários ou sem configuração salva após prazo definido. |

Regras de consistência:

- Cada imagem deve ter vínculo verificável com a loja e a configuração proprietárias. Uma operação de uma loja não pode apagar imagens de outra.
- Preferir imagens pertencentes a uma opção, sem compartilhamento entre configurações nesta primeira etapa. Se houver compartilhamento, só apagar o arquivo quando o último vínculo for removido.
- Na substituição, preservar a imagem atual se o envio ou a gravação da nova configuração falhar.
- A limpeza deve funcionar também nos caminhos de exclusão por webhook e por SQL direto; não depender apenas da tela do lojista ou de callbacks de entidade.
- Registrar a exclusão pendente no S3 de forma persistente e executar tentativas posteriores em caso de falha. A operação deve ser idempotente: repetir a exclusão de um arquivo já removido não deve causar erro permanente.
- Prever uma rotina de conciliação para detectar e limpar arquivos órfãos, com proteção para uploads ainda em andamento. Definir prazos e observabilidade antes da publicação.
- A desinstalação do app deve seguir a política vigente de retenção de configurações para reinstalação. A exclusão definitiva deve eliminar os arquivos; desinstalação e exclusão definitiva são eventos distintos no sistema atual.

Os pedidos desta etapa preservam o nome da escolha, sem depender da imagem do catálogo para manter a identificação textual. A futura funcionalidade de arquivo enviado pelo comprador terá outra política, porque a loja precisa acessar esse arquivo para produzir o pedido.

## Pontos existentes para integração

- Cadastro e edição: `PersonalizationAdminService` e `/admin/products/{productId}/fields`.
- Remoção manual de produto/configuração: `PersonalizationAdminService.deleteRule`.
- Exclusão do produto na plataforma: `WebhookLifecycleService.handleProductDeleted`, que já recebe `product/deleted` e remove a regra.
- Exclusão definitiva da loja: `StoreDataErasureService.erase`, que atualmente remove os campos e regras por SQL.
- Configuração pública: `FieldResponse` e `PublicPersonalizationController`.
- Vitrine: componentes NubeSDK, script tradicional e adaptador Patagonia. Os três caminhos devem receber suporte à seleção visual.
- Carrinho e pedido: manter o transporte do nome selecionado nas propriedades de personalização existentes.

A implementação usa a migration V47, metadados no banco e arquivos no S3. Foram consultadas as capacidades dos componentes [Image](https://nuvemshop.dev/en-US/apps/nube-sdk/components/image) e [Button](https://nuvemshop.dev/apps/nube-sdk/components/button) na implementação do caminho NubeSDK.

## Critérios de aceite

1. Cadastrar e editar opções com imagem, com validação do conteúdo do arquivo, prévia e nomes únicos dentro do campo.
2. Selecionar e ampliar imagens nos três caminhos de vitrine, com uso em celular e teclado.
3. Validar obrigatoriedade e enviar o nome correto ao carrinho/pedido; rejeitar escolhas que não pertencem ao campo.
4. Preservar o nome já registrado no pedido ao renomear ou remover uma opção do catálogo.
5. Verificar no S3 que excluir opção, campo, configuração ou produto remove as imagens correspondentes, incluindo derivados e versões, quando aplicáveis.
6. Verificar a limpeza ao receber `product/deleted` e ao executar a exclusão definitiva da loja.
7. Simular falhas de envio, gravação e exclusão, repetição de eventos e tentativa de acesso entre lojas: manter dados consistentes e concluir a limpeza por nova tentativa quando necessário.
8. Verificar a limpeza de uploads abandonados e a preservação de imagens que continuam vinculadas a configurações válidas.
9. Registrar no histórico de versão e no deploy a entrada da funcionalidade, para acompanhar adoção e relatos posteriores.
