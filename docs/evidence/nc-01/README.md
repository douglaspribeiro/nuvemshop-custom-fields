# NC-01 — relatório de assinaturas e conciliação V1

Entrega local validada em 28/10/2026. Rota: `/backoffice/reports#subscriptions`.

Uma linha por loja, com plano atribuído e da assinatura, origem/provedor, ambiente, referência contratual, status/acesso, valor/moeda, último ID/status de pagamento, próxima cobrança, sincronização e elegibilidade ao MRR com motivo de exclusão. Contrato atual prevalece sobre legado; a mesma regra decide inclusão na tabela e nos agregados por moeda. Sem preço de catálogo como substituto.

Valor/data confirmados do pagamento sem fonte persistida confiável aparecem como não informados; repasse líquido aparece como não disponível no sistema. Nenhuma consulta nova a gateways, migração, filtro ou CSV da nova tabela. Relatórios e CSV anteriores preservados.

## Testes

`mvn -o -q -Dskip.frontend=true -Dncf.release.screens=/tmp/nc01-visual test`

Resultado: **760 testes, zero falhas, zero erros, 1 ignorado**. Maven terminou com código 0. `git diff --check` passou. Frontend build dispensado: alteração no template Thymeleaf e CSS estático.

Cobertura: produção/teste, pendência, cancelamento solicitado/efetivado, cortesia, suspensão, inatividade, ausência de contrato/preço/moeda, contratos legados, precedência sem duplicação, moedas separadas, reconquista, projeção e preservação dos valores registrados. Testes MVC renderizam o template real com sessão do operador e verificam proteção da rota/CSV, escape de conteúdo, campos ausentes e relatórios anteriores.

## Validação visual

Chrome headless, com dados fictícios produzidos pelo teste MVC `PlanPerformanceReportTest.blockedStoresAreNotReportedAsHavingNoSalesAndShowTheRequiredAction`. A seção renderizada pelo Thymeleaf foi extraída do HTML real do teste e aberta com o CSS atual embutido, sem dependência de servidor/gateway. Inspeção visual das imagens:

- [Desktop 1800 × 1100](desktop.png): contrato USD ativo e loja sem contrato, valores e lacunas legíveis.
- [Colunas finais](desktop-direita.png): cobrança, MRR, sincronização e repasse após rolagem horizontal.
- [Celular 390 × 1200](mobile.png): texto contido no painel, tabela com rolagem horizontal própria.

A validação é local com fixtures; não representa consulta a contratos reais nem caixa conciliado. Sem commit, push, implantação ou verificação em produção nesta entrega.
