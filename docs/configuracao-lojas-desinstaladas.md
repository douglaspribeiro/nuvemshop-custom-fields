# Consulta da configuração de lojas desinstaladas

No backoffice, acesse **Lojas desinstaladas → Ver configuração**. O mesmo acesso
está disponível em **Gerenciar loja** e nos detalhes da reconquista.

A tela mostra os produtos pelo nome e ID, se a personalização estava habilitada,
os campos e suas posições, tipos, obrigatoriedade, exemplos, limites, validações,
opções de seleção e nomes das opções com imagens. Também apresenta plano,
permissões, cores e até 20 eventos de integração registrados antes da saída.

O relato recebido pela reconquista aparece como **Resposta da loja**. O motivo e a
justificativa registrados pelo administrador aparecem separadamente como
**Anotação no backoffice**, para manter clara a origem de cada informação.

## Registros a partir da publicação

A migração V50 cria `store_configuration_snapshots`. A aplicação preserva a
configuração no primeiro processamento de `app/uninstalled`, antes de limpar o
plano e as permissões. Se `store/redact` chegar primeiro, a cópia é feita antes da
revogação, junto ao registro da solicitação de exclusão já existente.

O registro usa a loja e a data da saída. Webhooks repetidos não sobrescrevem a
cópia. Após reinstalação e nova desinstalação, outra cópia é criada. O histórico
permite consultar cada saída, mesmo depois que os produtos ou campos mudarem.
O link da reconquista abre a configuração correspondente à saída da campanha.
A preservação independe de envio de e-mails ou habilitação da SQS.

As cópias guardam as configurações locais, sem copiar o token de acesso ou as
respostas de compradores. A exclusão definitiva da loja remove também as cópias;
os totais anônimos de saídas continuam seguindo o comportamento existente.

## Desinstalações anteriores

Não há reconstrução retroativa. Quando ainda existem produtos e campos no banco,
a tela permite consultá-los e informa que não são uma cópia confirmada do momento
da desinstalação. Se os dados já foram apagados, não é possível recuperá-los por
esta consulta.

O registro da configuração e os eventos disponíveis ajudam a investigar um relato
como “os campos não apareceram”, mas não comprovam por si só a exibição no tema da
loja. A tela destaca produtos desativados, produtos sem campos e suspensão
registrada; a investigação pode exigir comparar o relato com os eventos.
