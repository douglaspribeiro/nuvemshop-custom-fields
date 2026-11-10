# Em breve na página inicial

A página `/admin` divulga arquivos do comprador, templates reutilizáveis e edição em massa para todos os planos e lojas. O bloco é informativo: não concede acesso às funcionalidades nem promete uma data de lançamento. Os textos ficam em `messages.properties` e `messages_es.properties`.

Cada link “Tenho interesse” abre `/admin/suggestions` com um rascunho traduzido do recurso. O lojista pode editar e enviar pelo fluxo existente. Só o envio registra a solicitação, consultável em `/backoffice/suggestions`; clicar no link não cadastra interesse nem inscreve em notificações automáticas.

“Dispensar” oculta o bloco e guarda a preferência em `localStorage`, por loja e versão do anúncio (`ncf-coming-soon:{storeId}:personalization-v1`). Outros dispositivos/navegadores não compartilham a preferência. Com armazenamento bloqueado, a dispensa vale apenas na página atual. Sem JavaScript, o conteúdo e os links continuam acessíveis.

Ao lançar um recurso, retire-o desta lista e anuncie sua disponibilidade com o destino correto. Para reapresentar uma atualização relevante, altere `data-coming-soon` no template `admin/index.html`. Mantenha a lista curta e não reutilize a versão para um anúncio que deva reaparecer.
