# Planos No Banco Com Ajuste Pelo Backoffice

## Resumo

Centralizar billing e limites comerciais em uma tabela versionada por vigencia, mantendo `PlanType` como identidade da loja. O backoffice ganha uma tela para consultar e criar versoes de planos, permitindo reajuste imediato ou agendado sem alterar codigo/env vars.

## Mudancas Principais

- Criar tabela `plan_assets` com versoes por plano: tipo, nome, descricao, external ID de billing, moeda, valor, limites, vigencia e status ativo.
- Criar migration inicial com `FREE`, `PREMIUM` e `PREMIUM_PLUS`.
- Criar entidade, repositorio e servico de catalogo para buscar plano vigente, planos pagos vigentes e resolver plano por external ID.
- Reduzir `NuvemshopBillingProperties` para configuracao global: `enabled`, `apiBaseUrl` e `conceptCode`.

## Backoffice

- Adicionar link e tela `/backoffice/plans`.
- Listar versoes por plano e destacar a versao vigente.
- Criar novas versoes com tipo, nome, descricao, external ID, moeda, valor, limites e datas de vigencia.
- Ao criar versao nova, encerrar a versao vigente anterior no dia anterior ao inicio da nova vigencia.
- Bloquear sobreposicao de vigencias para o mesmo `plan_type`.

## Relatorios

- Usar os precos vigentes do catalogo para o MRR estimado.
- Adicionar projecao de pagamentos do mes atual com base em `billing_next_execution`.
- Somar apenas lojas ativas, pagas, sem cortesia e sem billing suspenso.

## Testes

- Testar selecao de asset vigente, ausencia de asset e bloqueio de sobreposicao.
- Testar billing usando preco/external ID do catalogo.
- Testar limites vindos do catalogo.
- Testar relatorio com MRR e projecao mensal.
