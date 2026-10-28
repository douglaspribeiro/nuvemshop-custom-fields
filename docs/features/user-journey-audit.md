# Auditoria da jornada do lojista

Implementação local em 28/10/2026. Tabela `user_journey_audit`, migration V63. Sem cron ou exclusão automática por idade; o prazo de 90 dias fica para implementação futura.

## Eventos registrados

A navegação e as ações que chegam aos controllers de `/admin/**`, `/oauth/callback` e suporte autenticado são interceptadas automaticamente: entrada OAuth/Nexo, início, dashboard, ajuda, produtos, editor de campos, onboarding/aplicação de template, criação/edição/exclusão de campos, remoção da configuração do produto, estilo, planos, checkout/upgrade/cancelamento, imagens enviadas, sugestões e tickets/mensagens de suporte. Leituras de imagens e polling `/admin/billing/status` são excluídos para não registrar tráfego automático como intenção do lojista.

O evento contém loja, UUID de jornada independente do identificador real da sessão, ator (`MERCHANT` ou `OPERATOR` no modo loja do backoffice), ação `Controller.metodo`, rota parametrizada, método HTTP, IDs de produto/campo quando disponíveis, plano escolhido válido, data de início e fim, resultado e HTTP status. Não armazena URL de checkout, query string, código OAuth, token Nexo, senha, email, IP, user-agent, dados de pagamento, conteúdo do formulário ou mensagem de suporte.

O estado anterior/posterior contém quantidades locais de regras/campos; para ações de um produto, existência da regra, habilitação e IDs/tipos/obrigatoriedade/ordenação dos campos. Textos de labels/opções e valores do comprador não são copiados. Abertura do editor pode criar uma regra vazia: `exists=true` sem campos não comprova configuração concluída, exibição na vitrine ou venda.

Tentativas e conclusão usam transações independentes da operação de negócio. `STARTED` significa sem conclusão registrada; `COMPLETED` significa requisição concluída, não pagamento aprovado ou tarefa comercial concluída; `REJECTED` identifica erro de formulário/flash ou HTTP 4xx; `ERROR` identifica exceção/HTTP 5xx; `ACCESS_REQUIRED` identifica redirecionamento ao acesso embedded. HTTP 200/302 isoladamente não são prova de sucesso de negócio. Falha na auditoria gera aviso técnico sem dados da requisição e não impede o uso do produto; indisponibilidade do banco pode produzir lacunas.

Após OAuth/Nexo, a loja é identificada pela sessão resultante, sem atribuir a ação à loja anterior. Nesses eventos não há estado anterior disponível. Sem identidade de loja não se cria evento. Sessões antigas de lojas já excluídas não recriam auditoria.

## Preservação e exclusão

- Desinstalação (`app/uninstalled`): preserva auditoria.
- Solicitação `store/redact`: preserva auditoria enquanto aguarda a revisão manual existente.
- Ação do operador **Excluir loja**, com as proteções existentes do backoffice: apaga auditoria da loja na mesma transação de exclusão dos demais dados.
- Nenhum job, cron, TTL ou deleção automática por 90 dias nesta versão; registros antigos permanecem.

## Consulta da linha do tempo

```sql
SELECT id, occurred_at, completed_at, journey_id, actor, action,
       route, product_id, field_id, selected_plan, outcome, http_status,
       before_state, after_state
FROM user_journey_audit
WHERE store_id = :store_id
ORDER BY occurred_at, id;
```

Índices por `(store_id, occurred_at, id)` para rastreio e por `occurred_at` para a futura retenção. `occurred_at` é obrigatório e possui default no banco, além da inicialização na entidade.

São observações de requisições ao servidor a partir da implantação, sem retroatividade. Digitação não enviada, fechamento da aba, preview somente no navegador e motivo subjetivo de abandono não são observados. Uma sequência de requisições não informa a razão de quem desistiu sem explicar. Eventos de plataforma, sincronizações e confirmação financeira continuam nos registros e modelos específicos existentes, sem serem atribuídos ao lojista como cliques.

## Validação

Testes MVC usam rotas reais para navegação, rejeição de estilo, criação/edição/exclusão de campos e remoção do produto. Verificam ator operador, ordenação, estado antes/depois, isolamento de dados sensíveis e jornada. Testes de interceptor cobrem OAuth trocando a loja, falhas, acesso exigido, anonimato e exclusão de polling/imagens. Teste transacional verifica sobrevivência ao rollback e preservação de evento com 100 dias; teste de migration executa V63 em H2 MySQL e verifica data automática e loja obrigatória. Teste de ciclo de vida verifica preservação na desinstalação/redact, exclusão manual e sessão antiga após exclusão.

Comando: `mvn -o -q -Dskip.frontend=true test`. Sem alteração visual, commit, push, implantação ou execução de migration em produção nesta etapa.

Resultado final da suíte local: **770 testes, zero falhas, zero erros, 1 ignorado**. Maven terminou com código 0; `git diff --check` passou.
