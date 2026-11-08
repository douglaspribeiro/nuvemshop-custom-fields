# Arquivos do comprador: AWS S3 e OCI Object Storage

## Regra de escolha

A escolha para **novos uploads** segue:

1. `stores.buyer_upload_storage_provider`, quando definido para a loja.
2. `buyer-uploads.provider` / `BUYER_UPLOAD_PROVIDER`, quando a loja usa o padrão.

Valores do enum: `AWS_S3` e `OCI_OBJECT_STORAGE`. No backoffice, abrir os detalhes da loja e escolher **Usar padrão do sistema**, **AWS S3** ou **OCI Object Storage**. Salvar uma escolha explícita exige que o adaptador esteja configurado. A alteração é registrada no histórico de integrações da loja. Não muda o plano, cotas, arquivos já enviados ou as imagens de opções cadastradas pelo lojista.

Cada arquivo em `buyer_uploads` registra `storage_provider`, `bucket` e `object_key`. Essa localização é gravada antes do envio remoto, de modo que uma falha/timeout também tenha o destino correto para limpeza. Leituras, downloads e exclusões sempre usam o provedor do arquivo, não a preferência atual da loja ou o padrão global. Não há fallback silencioso se o destino escolhido estiver indisponível.

A migration **V75** atribui `AWS_S3` aos registros existentes e deixa a preferência das lojas nula (seguir padrão). Não move nem copia objetos. A opção nula acompanha futuras mudanças do padrão; uma opção explícita fica fixa até ser alterada no backoffice.

## AWS S3

```dotenv
BUYER_UPLOAD_PROVIDER=AWS_S3
BUYER_UPLOAD_S3_BUCKET=seu-bucket-privado
BUYER_UPLOAD_S3_REGION=us-east-2
BUYER_UPLOAD_S3_PREFIX=production/buyer-files
```

Credenciais seguem a cadeia padrão do AWS SDK (perfil, ambiente ou role), já usada na aplicação. Os quatro controles de bloqueio de acesso público precisam estar ativos. Objetos são enviados com SSE-S3/AES256 e `Cache-Control: no-store`; downloads continuam autenticados e transmitidos pelo aplicativo.

## OCI Object Storage

A implementação usa o SDK **nativo da OCI**, não a API S3 Compatibility. Não precisa de Customer Secret Keys.

```dotenv
BUYER_UPLOAD_PROVIDER=OCI_OBJECT_STORAGE
BUYER_UPLOAD_OCI_BUCKET=seu-bucket-privado
BUYER_UPLOAD_OCI_NAMESPACE=seu-namespace
BUYER_UPLOAD_OCI_REGION=sa-saopaulo-1
BUYER_UPLOAD_OCI_PREFIX=production/buyer-files
BUYER_UPLOAD_OCI_AUTH=CONFIG_FILE
BUYER_UPLOAD_OCI_CONFIG_FILE=/run/secrets/oci/config
BUYER_UPLOAD_OCI_PROFILE=DEFAULT
```

O arquivo de configuração/perfil segue o formato oficial OCI: tenancy, user, fingerprint, key_file e region, com eventual passphrase. A chave privada fica fora do repositório, montada com permissões restritas. Para execução numa instância OCI com dynamic group/IAM configurados, usar:

```dotenv
BUYER_UPLOAD_OCI_AUTH=INSTANCE_PRINCIPAL
```

Nesse modo, o SDK usa a identidade da instância; arquivo de configuração e chave de usuário não são necessários. Uma OCI configurada precisa de namespace e autenticação válidos mesmo quando não é o padrão, pois pode atender arquivos históricos ou exceções por loja.

O bucket precisa ser privado (`NoPublicAccess`); o aplicativo consulta o bucket antes do envio e recusa buckets públicos. OCI criptografa objetos em repouso por padrão. Os uploads usam `Cache-Control: no-store`; a aplicação não cria URLs públicas ou pre-authenticated requests. Permitir ao principal operações de consulta do bucket, envio/leitura de objetos, listagem de versões e exclusão dos objetos/versões no bucket dedicado. Conferir políticas de retenção e lifecycle para não impedir limpeza nem remover arquivos válidos prematuramente.

A limpeza OCI enumera todas as páginas e remove versões/delete markers somente da chave exata, sem apagar objetos com prefixo semelhante nem criar um novo marcador após excluir versões. Falha de exclusão mantém o registro pendente e o espaço ocupado até confirmação.

## Trocar o padrão sem interromper arquivos existentes

1. Configurar os dois adaptadores e suas permissões no servidor.
2. Aplicar a migration V75 antes de usar a nova versão.
3. Alterar `BUYER_UPLOAD_PROVIDER` e reiniciar/reimplantar para carregar a propriedade.
4. Manter credenciais e configurações do provedor anterior enquanto houver arquivos ativos ou exclusões pendentes nele. A mudança de padrão não desativa esse adaptador.
5. No backoffice, revisar exceções por loja; elas têm prioridade sobre o novo padrão.
6. Conferir envio ao destino escolhido e download/exclusão de arquivos anteriores nos dois provedores.

Bucket e chave permanecem registrados por arquivo; prefixo novo não muda os caminhos anteriores. Região/conta AWS e namespace/região OCI devem continuar capazes de acessar os buckets históricos. Este modelo identifica dois provedores, não múltiplas contas arbitrárias do mesmo provedor. Mudar credenciais/namespace para outra conta sem manter acesso aos objetos históricos exige planejamento adicional.

Se o destino de novos uploads estiver indisponível, não redirecionar gravações automaticamente para outro provedor. Downloads e limpeza dos demais provedores continuam independentes do destino selecionado para novos envios. A limpeza não é interrompida só porque novos uploads estão desabilitados.

## Verificação

Testes locais cobrem roteamento por loja/padrão, persistência do enum por arquivo, transição entre provedores, download/exclusão de arquivos anteriores, falha no envio, regras de privacidade OCI, paginação de versões, migration de registros antigos e tela protegida do backoffice.

Homologação real exige buckets privados e identidades autorizadas nos dois serviços: enviar arquivos, confirmar associação ao pedido, alternar o padrão e a preferência da loja e baixar/excluir arquivos de ambos os destinos. Testes com mocks e H2 não comprovam IAM, rede, autenticação, migração MySQL ou integração real com os provedores.

Fontes: [API nativa e S3 Compatibility](https://docs.oracle.com/en-us/iaas/Content/Object/Tasks/s3compatibleapi.htm), [segurança de Object Storage](https://docs.oracle.com/en-us/iaas/Content/Security/Reference/objectstorage_security.htm), [SDK Java OCI](https://github.com/oracle/oci-java-sdk).
