# NC-40 — Agenda financeira

Desenvolvimento em 17/11/2026. Entrada pelo relatório do backoffice, em `/backoffice/finance`. A V90 cria um livro financeiro independente do estado contratual das assinaturas. Não há operações de saque, transferência, antecipação, reembolso ou alteração de acesso nesta funcionalidade.

## Fluxo disponível

- Consultar mês, semana ou lista, filtrando período, etapa, gateway, conta, moeda, meio, estado e ambiente. Produção é o padrão; sandbox exige seleção explícita.
- Consultar totais por moeda, certeza e estado e posições parciais por gateway. Valores desconhecidos não viram zero; somas de etapas diferentes não são combinadas.
- Abrir pagamento, liberação, repasse ou crédito, consultar assinatura/loja, movimentos relacionados, rateios e histórico antes/depois. A consulta original permanece acessível mesmo após ajustes documentados.
- Exportar a seleção e a lista sem previsão. O CSV preserva datas civis e identifica origem, certeza da data, certeza dos valores, vigência aplicada, moeda e câmbio comprovado. Texto é protegido contra fórmulas de planilha.
- Criar regra por conta/ambiente, país/moeda, meio e parcela, com vigência, base de pagamento/captura/lançamento contábil, corte, fuso, dias úteis/corridos, feriados, janelas de repasse, prazo bancário, mínimo, reserva e carência inicial. Simular antes de salvar uma versão imutável. Sem instante para avaliar o corte, o cálculo é conservador e usa o dia seguinte.
- Recalcular estimativas com prévia e confirmação. Uma versão nova não altera silenciosamente previsões antigas nem datas específicas do provedor. Mínimo sem saldo elegível conhecido, retenção e saque sob solicitação não geram data garantida.
- Importar histórico documentado em CSV, revisar e confirmar atomicamente. As prévias ficam em staging no banco por 30 minutos; a sessão guarda somente identificadores, respeitando o BLOB de sessões compartilhadas. Uma falha cancela a importação inteira. Identidades estáveis impedem duplicação; renovações ficam em movimentos distintos.
- Vincular liberações parciais a um payout agregado na mesma conta/moeda, com limites de rateio e evidência. A tarifa do payout permanece no próprio payout.
- Conciliar crédito bancário com referência estável, data, valor, evidência e responsável. Os créditos acumulados não podem exceder o repasse. Sucesso do payout ou passagem do tempo não cria crédito bancário.
- Registrar estorno, disputa, tarifa, retenção/liberação de reserva, antecipação, retorno, câmbio e divergência de data como ajustes auditados. Ajustes na mesma moeda afetam somente o líquido relacionado; várias liberações exigem rateio explícito, sem distribuição presumida.

## Fontes consultadas em produção, somente leitura

Consultas autorizadas pelo responsável em 17/11. Nenhuma configuração, cobrança, transferência ou assinatura remota foi alterada. Valores abaixo descrevem cobertura, não caixa agregado.

| Gateway | Situação observada | Automação implementada | Complemento documentado |
| --- | --- | --- | --- |
| Efí | Habilitado, produção; 36 assinaturas conhecidas. Cobrança consultada devolveu `total`, `paid_value`, status, histórico e meio; sem data individual de disponibilidade/tarifas | Histórico da assinatura e detalhe de cada cobrança, usando valor efetivamente pago quando informado | Datas de pagamento ausentes por comprovante; liberação, taxas e repasses por relatório/regra da conta. `settled` isolado não é prova de pagamento efetivo |
| Dodo | Habilitado, produção; 6 assinaturas conhecidas. Detalhe e ledger acessíveis; nenhum payout na consulta. Exemplo sanitizado: USD 4,99 e tarifa USD 0,71 no ledger | Pagamentos paginados, detalhes, ledger por pagamento, moeda de liquidação, tarifas/movimentos e payouts | `created_at`/`updated_at` do pagamento não são data de pagamento. Pode-se configurar base de lançamento contábil comprovado. Ledger não comprova disponibilidade para saque. Payout agrupado e crédito bancário exigem vínculo/conciliação |
| Creem | Habilitado, produção; 11 assinaturas conhecidas. Pesquisa paginada acessível; o primeiro 403 foi superado com identificação adequada da requisição | Transações históricas paginadas, validação do ambiente por item, valores pagos/impostos/estornos | Data efetiva quando ausente, disponibilidade, taxas de repasse e payouts por relatório/regra. Criação da transação não vira pagamento |
| Stripe | Habilitado em sandbox; saldo transacional e payouts consultáveis, sem movimentação na amostra; sem assinatura local conhecida | Faturas, pagamentos/cobranças, transação de saldo, `available_on`, taxa/líquido/moeda de liquidação e payouts paginados | Crédito bancário e vínculo agregado por conciliação. Não houve transação de produção para validar nesta conta |
| Mercado Pago | Desabilitado para novas operações; credencial permite consulta de pagamentos; nenhuma assinatura local conhecida | Histórico de pagamentos autorizados de assinaturas locais e detalhes: `date_approved`, `money_release_date/status`, líquido, tarifas e impostos | Sem importar pagamentos alheios ao produto. Contratos externos pertencem à NC-41. Saques/liberações parciais não retornadas pela consulta por relatório documentado |
| Dodo Test | Desabilitado; credencial de sandbox consultável | Mesmo adaptador financeiro, sempre em sandbox | Excluído da consulta de produção |
| Paddle / dLocal Go | Sem adaptador operacional neste produto | Cobertura explicitamente pendente | Importação manual por conta/ambiente; enum não implica integração |

A leitura automática cobre assinaturas conhecidas no banco local e o histórico que o provedor devolve. Contratos antigos substituídos e ausentes dessa base, períodos não disponibilizados pela API e movimentação de outras aplicações não são declarados cobertos. A tela registra sucesso/falha e a lacuna por conta e assinatura. Relatórios documentados cobrem esses períodos sem inventar dados.

Fontes técnicas: [Stripe Balance Transaction](https://docs.stripe.com/api/balance_transactions/object), [gestão de pagamentos Mercado Pago](https://www.mercadopago.com.br/developers/pt/docs/subscriptions/additional-content/payment-management), [transações Creem](https://docs.creem.io/api-reference/endpoint/get-transactions), [pagamento Dodo](https://docs.dodopayments.com/api-reference/payments/get-payments-1), [ledger Dodo](https://docs.dodopayments.com/api-reference/balance-ledger/list-ledger-entries), [payouts Dodo](https://docs.dodopayments.com/api-reference/payouts/get-payouts). Payloads financeiros reais foram inspecionados sem salvar dados de cliente/cartão ou credenciais; cenários sanitizados estão nos testes.

## Importação e conciliação

O modelo CSV é fornecido na própria tela. Valores são unidades monetárias decimais, datas civis `AAAA-MM-DD` e instantes ISO com fuso. `capturedAt`, `postedDate` e `exchangeRate` são opcionais; não preencher por aproximação.

A origem pode ser o código de um lançamento existente ou uma referência `PAYMENT:identificador:parcela` / `RELEASE:identificador:parcela` / `PAYOUT:identificador:parcela` da mesma conta/ambiente. As etapas são ordenadas na confirmação para importar pais e filhos juntos. Parcelas distintas usam identificadores de parcela distintos.

Relatório que completa uma data de pagamento ausente cria complemento documentado, preservando o original da API. Relatório específico de liberação pode preencher uma lacuna da API e não é apagado por uma consulta posterior que continue sem esse dado. Divergências com informação autoritativa ficam no histórico; correções explícitas são movimentos separados. Um placeholder sem disponibilidade é omitido dos totais quando liberações específicas da mesma origem o substituem.

Não existe taxa, prazo Efí=31 ou Creem=7 presumido. Taxas projetadas ficam estimadas; líquido sem base suficiente permanece desconhecido. As três moedas — cobrança, liquidação e payout — ficam nos respectivos movimentos, relacionados por origem/vínculos, sem conversão para total único. Câmbio somente com evidência efetiva.

## Operação

A consulta automática ocorre a cada 15 minutos, com atraso inicial de 2 minutos, e pode ser acionada pelo operador. As chamadas usam os adaptadores existentes, seus limites de tempo e credenciais no servidor. O reconciliador financeiro não grava o estado contratual nem altera acesso pago. Falhas preservam os movimentos existentes e tornam a cobertura pendente.

`FINANCE_ENABLED`, `FINANCE_SYNC_DELAY_MS` e `FINANCE_INITIAL_DELAY_MS` controlam a rotina. `FINANCE_EFI_ACCOUNT`, `FINANCE_MERCADO_PAGO_ACCOUNT`, `FINANCE_DODO_ACCOUNT`, `FINANCE_DODO_TEST_ACCOUNT`, `FINANCE_STRIPE_ACCOUNT` e `FINANCE_CREEM_ACCOUNT` identificam as contas (padrão `default`). A identidade separa também gateway e ambiente. Ao trocar a conta financeira por trás das credenciais, atribuir outro identificador; rotação de credencial da mesma conta mantém o identificador. Contas adicionais sem adaptador podem ser documentadas por importação.

## Validação e limites de publicação

- Suíte Java completa executada com sucesso; ajustes finais revalidados nos testes financeiros, de página, de sincronização e dos adaptadores envolvidos.
- Migração V89 → V90 aplicada e validada em MySQL 8.4 descartável; persistência e idempotência conferidas. Nenhum banco de produção foi migrado.
- MVC renderizou agenda, calendário, detalhes, regras, prévias e erros; importação com revisão/rollback, autenticação, proteção de formulário e conciliação bancária foram exercitadas. Não houve verificação visual com navegador.
- Cálculos, feriados, cortes, janelas, falta de saldo elegível, sandbox, moedas, líquido desconhecido, prioridades de fontes, eventos atrasados/repetidos, novas renovações, estornos, rateios e suplementos manuais têm testes específicos.
- Dados reais foram consultados para cobertura dos gateways. O aplicativo desenvolvido ainda não foi implantado: sincronização periódica, regras específicas da conta e navegação na versão publicada não são afirmadas como verificadas em produção.

Código e documentação locais; commit, push e deploy são etapas separadas.
