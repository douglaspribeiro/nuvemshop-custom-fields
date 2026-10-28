# Tela de auditoria — evidências locais

Validada em 28/10/2026. Acesso: `/backoffice/audit`, menu Auditoria da visão geral ou botão Auditoria da jornada no detalhe da loja.

## Verificação funcional

`mvn -o -q -Dskip.frontend=true -Dncf.audit.screens=/tmp/ncf-audit-ui test`

**795 testes, zero falhas, zero erros, 1 ignorado**; Maven terminou com código 0. `git diff --check` passou.

Os testes `BackofficeAuditPageTest` cobrem sessão anônima/lojista sem acesso, validação dos filtros, consulta geral e por loja, resultado e ator combinados, ordenação por data/ID, paginação de 50 eventos com filtros preservados, ausência de resultados, estados anteriores/posteriores e conteúdo escapado. Consultar a tela não cria eventos adicionais. O detalhe da loja inclui o novo link.

## Verificação visual

HTML renderizado pelo template real em teste MVC com fixtures fictícias. O CSS atual foi embutido para abrir o documento localmente no Chrome headless. Os detalhes do primeiro evento foram abertos na captura desktop. Imagens inspecionadas visualmente:

- [Desktop 1440 × 1900](desktop.png): filtros, antes/depois, detalhes de jornada e dados estruturais, resultados concluído/recusado.
- [Celular 390 × 2300](mobile.png): filtros e cards em uma coluna, sem transbordamento horizontal observado.
- [Sem registros 1440 × 1000](empty.png): filtros preservados e mensagem explícita.

Validação local; sem consulta a dados de produção, nova migration, alteração da retenção, commit, push ou implantação nesta etapa.
