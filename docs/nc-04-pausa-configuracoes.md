# NC-04 — pausa reversível por limites do plano

## Regra e comprovação

O plano efetivo de `Store.getEffectivePlan()` determina o acesso: bônus válido e acesso pago mantido durante a carência de renovação continuam válidos. Inadimplência só provoca corte depois de o fluxo de cobrança encerrar esse acesso (a carência existente de um dia não foi alterada). Free e Free interno usam os limites do catálogo Free para este controle.

Sem plano pago válido, somente uma configuração completa elegível ao Free permanece disponível. A ordem é `personalization_rules.created_at ASC, id ASC`; o ID desempata datas iguais. Configurações desativadas manualmente não concorrem à vaga. Se nenhuma configuração for elegível, nenhuma permanece disponível. Não se remove nem se oculta parte dos campos de uma configuração para torná-la elegível.

Elegível significa ter pelo menos um campo, caber integralmente no limite de campos do catálogo Free vigente e respeitar a disponibilidade de campos de imagem e seu limite de opções. Os outros tipos disponíveis no editor não possuem restrição de tipo específica no catálogo atual.

Cada regra mantém `plan_eligibility_evidence` com versão da política NC04/v1, ID e limites do catálogo Free, resultado da elegibilidade, ID/data da regra e IDs/tipos/quantidade de opções de seus campos. Registra também o catálogo/limites efetivos da avaliação. `plan_eligibility_evaluated_at` indica quando essa evidência mudou. O registro é evidência da avaliação atual, não um histórico imutável de todas as avaliações.

A pausa usa exclusivamente `plan_paused` e `plan_paused_at`. Não muda `enabled`, não apaga campos e não modifica pedidos. A reativação limpa somente a pausa NC-04. Configurações manualmente desativadas continuam desativadas. Na reativação paga, configurações pausadas completas são selecionadas na mesma ordem, respeitando a capacidade restante de produtos, campos, produtos com imagens e opções. Excedentes permanecem pausados. Configurações já disponíveis em um plano pago mantêm o comportamento existente de apresentação/limites; a NC-04 não introduz uma nova pausa por downgrade entre planos pagos.

## Aplicação e carrinhos em andamento

Um reconciliador persistente por loja executa com intervalo de 12 horas após o término de cada execução, com lock da loja, cobrindo alterações de cobrança, downgrade/upgrade, suspensão, reativação, bônus expirado/concedido e edições/exclusões de campos. Por padrão, a primeira execução ocorre 12 horas após a inicialização da aplicação; reiniciar a aplicação reinicia essa espera. O intervalo e a espera inicial usam `personalization.plan-reconcile-delay-ms`, cujo padrão é `43200000` ms. É idempotente: avaliações repetidas não geram novo aviso. Falha em uma loja não interrompe as demais; a próxima execução tenta novamente. O primeiro ciclo após implantação também trata lojas Free já excedentes e cria os respectivos avisos.

A rotina de 12 horas persiste as pausas/reativações e enfileira os avisos. A validação do plano nas consultas da loja e no admin permanece ativa, portanto uma configuração pode deixar de ser oferecida antes da rotina de 12 horas. O e-mail é enfileirado quando a rotina persiste a nova pausa; esse intervalo não concede uma carência adicional de 12 horas.

A API pública avalia a mesma política ao carregar configurações, mesmo antes da persistência da pausa. Respostas já em cache podem durar até o TTL configurado (padrão um minuto); mudanças persistidas invalidam o cache após commit. Uma página já aberta não é removida remotamente do navegador.

Campos pausados não são oferecidos nas consultas seguintes da configuração. Valores já preenchidos/enviados em carrinhos e pedidos são preservados; o corte não limpa propriedades nem altera pedidos antigos. Checkout consulta a configuração atual e não exige os campos de uma configuração que deixou de estar disponível. Uma tela já aberta pode concluir com os valores carregados anteriormente: essa continuidade é deliberada, sem promessa de atualização instantânea de abas abertas. Histórico e relatórios continuam consultando o catálogo completo, inclusive campos pausados.

## Comunicação

O admin mostra aviso global, status por produto e aviso no editor de campos, com acesso a cobrança. O estado exibido é calculado pela política atual, sem esperar o ciclo de persistência.

Na mesma transação da nova pausa, cria-se um registro em `configuration_pause_emails`. O envio automático ocorre depois do commit (consulta a cada 15 segundos), ao e-mail cadastrado da loja, em português para BR e espanhol para os demais países. O texto informa pausa já aplicada, produtos que continuam disponíveis, preservação dos campos/pedidos e reativação conforme os limites. Não anuncia prazo fictício. O botão abre a reativação existente no aplicativo autenticado, com o mesmo plano do contrato EFI quando esse fluxo está disponível; nos demais casos abre cobrança. O envio é descartado se o plano pago já voltou a ser válido, mesmo que ainda existam configurações excedentes.

Reativação, desinstalação, exclusão ou desativação manual anterior ao processamento tornam o aviso obsoleto; ele é marcado SKIPPED. Um aviso de episódio anterior não cobre uma pausa posterior do mesmo produto. Claim persistido antes do SMTP evita reenvio cego. Resultado SMTP incerto fica REVIEW; erro de validação fica FAILED; ambos exigem revisão operacional. CLAIMED após interrupção também exige revisão, sem retry automático. Sem SMTP configurado, o registro permanece PENDING. Não há garantia de entrega exatamente uma vez pelo SMTP. Exclusão LGPD remove a nova fila.

## Validação e pendências de gestão

Testes cobrem retenção, configuração incompatível inteira, desempate, preservação de campos, suspensão, bônus, reativação limitada, desativação manual, idempotência e obsolescência dos avisos. A implantação deve aplicar V66 antes de executar a nova versão.

A aprovação dos scores G/$/M/E continua pendente; esta implementação não atribui nem aprova esses scores. Implementação/testes locais não significam implantação ou envio de e-mails em produção.
