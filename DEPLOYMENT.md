# Deploy validado

O Dockerfile declara `app.deploy.protocol=1`. A infraestrutura controla readiness,
tráfego, shutdown, migrações e recuperação; publicar uma imagem não ativa o
controlador em um servidor ainda não adotado.

`APP_BACKGROUND_ENABLED=false` desliga jobs nas réplicas web e temporárias.
Somente a primeira réplica recebe `true`. O shutdown aguarda trabalho em execução
e emite marcador de conclusão; encerramento incompleto bloqueia troca do dono.
`APP_DEPLOY_CONTROL_ENABLED=true` habilita o controle de drain apenas em loopback.
O Nginx bloqueia acesso externo ao endpoint.

O perfil Spring `deploy` usa sessões JDBC compartilhadas, com inicialização
automática desabilitada. A nova migração Flyway cria as tabelas de sessão.
Somente a etapa dedicada de migração executa Flyway; réplicas web usam
`SPRING_FLYWAY_ENABLED=false` e `SPRING_JPA_HIBERNATE_DDL_AUTO=none`.

Validação: testes Maven do produto e aceitação com imagens reais, MySQL e Nginx
isolados. Consulte `deploy/DEFAULT-DEPLOY.md` e as evidências no repositório de
infraestrutura para limites, execução e rollback.
