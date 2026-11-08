# Rollback do código após a atualização de uploads

Branch: `rollback/pre-upload-1.56.1-2026-11-08`. Base: `c817802` (1.56.1), com adaptação de compatibilidade para banco V70–V75.

## Como funciona

Não se apagam tabelas, colunas, arquivos nem linhas de `flyway_schema_history`. As migrations V71–V75 são aditivas em relação à versão V70 da produção; o que V73 remove foi criado somente pela V71. O Hibernate da versão anterior aceita as tabelas/colunas adicionais.

A branch inclui os mesmos SQLs V71–V75 da versão de upload para reconhecer e validar os checksums já aplicados. `spring.flyway.target=current` impede aplicar migrations versionadas novas durante o rollback. Flyway e validação Hibernate continuam ligados; não usar `repair`, `clean`, exclusão de histórico, `validate-on-migrate=false` ou `ddl-auto=update` para forçar o startup.

O enum antigo passa a reconhecer FILE_UPLOAD para conseguir ler configurações gravadas pela versão nova sem falhar. Esse tipo não é oferecido para criação/edição nesta versão. O formulário público de upload fica indisponível: campos obrigatórios recebem validação que não aceita texto como substituto de arquivo. A configuração original continua intacta no banco. Upload opcional pode ser omitido; esta versão não recebe novos arquivos nem oferece o novo gerenciador de downloads.

Os arquivos físicos, seus buckets/chaves/provedores, templates e preferências por loja permanecem preservados para a retomada da versão de upload. Continuar atendendo downloads de arquivos existentes durante o rollback exige manter um serviço compatível de download; não afirmar que o código anterior inclui esse recurso.

## Procedimento de operação

1. Antes da atualização, guardar a referência da imagem em execução e backup verificado do banco. Não usar o backup antigo para substituir o banco ativo depois de novos pagamentos/pedidos sem conciliação.
2. Construir e publicar **uma imagem desta branch com seu novo commit**, usando tag própria. A imagem antiga 1.56.1 de `c817802` não contém esta adaptação; apenas selecionar essa imagem não é o rollback preparado aqui.
3. Ao reverter, trocar a imagem pela imagem de rollback. Preservar variáveis de conexão do mesmo banco; não sobrescrever `SPRING_FLYWAY_TARGET=current` com `latest`. Conferir histórico sem falhas até V70–V75.
4. Usar temporariamente `APP_BACKGROUND_ENABLED=false` para impedir tarefas antigas de manipular imagens compartilhadas por templates novos. Webhooks e rotas continuam disponíveis; reconciliação agendada fica pausada enquanto durar a recuperação. Não manter processos antigos e novos manipulando os mesmos dados simultaneamente sem coordenação.
5. Conferir `/actuator/health`, login e fluxo antigo; manter produtos que exigem upload sem novas compras até restaurar o serviço de upload. A versão de recuperação não é uma substituição completa das funções novas.
6. Para retomar a versão de upload, trocar a imagem pela versão nova com configuração normal (`target=latest` e background conforme o papel da instância). As migrations já aplicadas são validadas, não reaplicadas; os dados preservados voltam a ser utilizados.

Uma migration que falhou parcialmente, um checksum divergente ou um schema alterado fora dessa sequência requer análise própria. Não há promessa de recuperação automática de qualquer falha de DDL.

## Validação automatizada

`RollbackCompatibilityIT` usa somente MySQL descartável em loopback: migra até V75, grava configuração FILE_UPLOAD, arquivo AWS e preferência OCI/template, inicia Spring Boot com Hibernate validate/Flyway ativos e consulta saúde HTTP. Confere que arquivo/template/preferência e histórico não foram perdidos e que voltar ao migrador normal executa zero migrations. Um segundo caso confirma que `target=current` não transforma um banco V70 em V75.

Comando (a URL e a senha são de banco local descartável):

```bash
mvn -Dskip.frontend=true -Dtest=RollbackCompatibilityIT \
  -Dncf.migration.mysql.url='jdbc:mysql://127.0.0.1:PORT/ncf_migration_test_contract?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false' \
  -Dncf.migration.mysql.password=TEST_PASSWORD test
```

Não houve aplicação destas alterações no banco de homologação/produção, publicação de imagem ou deploy.

Referência: [Flyway target=current](https://documentation.red-gate.com/fd/flyway-target-setting-277579044.html).
