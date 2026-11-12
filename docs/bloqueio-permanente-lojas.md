# Bloqueio permanente pelo backoffice

No detalhe da loja, **Forçar uninstall e bloquear loja** exige digitar o ID da loja
e um token de ação da sessão autenticada do backoffice.

A operação encerra a instalação local, limpa a credencial armazenada, remove o
override e revoga o acesso. Sessões já abertas deixam de acessar a administração
e o cache da vitrine é invalidado após a confirmação da transação. O evento fica
registrado nos logs da loja. Repetir a operação não duplica o bloqueio.

O OAuth recusa o mesmo ID antes de salvar credenciais, registrar webhooks ou
instalar scripts. O registro de impedimento contém apenas ID e data e fica em
uma tabela independente, sem exclusão em cascata: apagar os dados da loja não
autoriza sua reinstalação. Não há ação de desbloqueio na interface.

Não se trata de uma chamada de desinstalação remota à Nuvemshop: o aplicativo
pode continuar listado no painel da plataforma. A ação também não cancela uma
cobrança no gateway; o cancelamento da assinatura continua na ação específica
do backoffice. Lojas bloqueadas não entram em campanhas de reconquista.
