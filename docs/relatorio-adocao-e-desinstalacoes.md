# Relatório de adoção, assinaturas e desinstalações

Disponível em `/backoffice/reports/adoption`, com acesso pelo backoffice em **Relatórios → Analisar adoção e saídas** e pelo diretório de lojas. A rota exige a sessão do backoffice.

## O que mostra

- Base de lojas com cadastro disponível, lojas ativas, desinstaladas e pedidos de exclusão pendentes.
- Assinaturas externas de produção ativas e sua participação na base ativa e na base inteira. Cortesias, testes, assinaturas pendentes/canceladas e cancelamento solicitado ficam fora do numerador. Billing nativo legado não é contado.
- Situação observada por loja: sem campos, campos sem produto habilitado, produto configurado, exibição reportada pela vitrine ou venda personalizada registrada. Cada loja ocupa uma única situação, com prioridade para o sinal de uso mais avançado.
- Participação de assinantes entre lojas ativas que possuem produtos habilitados com campos.
- Motivos de saída agrupados por texto e fonte. O motivo copiado da Nuvemshop tem prioridade sobre a resposta recebida na reconquista do mesmo episódio. Respostas de saídas anteriores não são reaproveitadas.
- Distribuição do tempo entre primeira instalação e última desinstalação, em dias completos. Datas inconsistentes ficam em uma categoria própria.
- Comparação mensal por primeira instalação registrada e lista por loja com configuração, exibição, vendas, assinatura e justificativa.
- Totais históricos agregados de saídas, incluindo cadastros apagados. Esses totais não são usados como denominador de conversão e não seguem os filtros da lista.

## Filtros e interpretação

Busca por nome/Store ID e intervalo inclusivo de primeira instalação selecionam a base para todos os indicadores. O filtro de situação afeta somente a lista detalhada. Datas de filtragem usam America/Sao_Paulo; as datas exibidas são adaptadas ao fuso do navegador.

As consultas usam contagens agrupadas para evitar uma consulta por loja ou por produto. Não consultam gateways nem a API da Nuvemshop durante a abertura do relatório.

Os percentuais de assinatura são **participação atual**, não conversão histórica de instalação para primeira compra. Uma loja que pagou e depois cancelou continua no denominador, mas não no numerador atual. Não comparar diretamente esses números com benchmarks de conversão em seis meses.

Produtos e campos refletem o cadastro salvo atualmente. Não são um snapshot do momento da desinstalação. Os logs `storefront.sdk.rendered` e `storefront.sdk.patagonia_transition_rendered` indicam que o script reportou uma exibição; ausência de log não comprova que o aplicativo nunca apareceu na loja. Vendas personalizadas positivas são sinais de uso. Sem vendas e sem sincronização completa, exibimos **Histórico de vendas incompleto**, em vez de concluir que a loja nunca usou o aplicativo.

Uma reinstalação mantém `installed_at` e limpa `uninstalled_at`. Portanto, o tempo mostrado não mede exclusivamente o último ciclo. Cadastros apagados não são reconstituídos, e seus motivos/configurações não podem ser detalhados pelo relatório.

## Motivos da Nuvemshop

Os motivos não chegam nos webhooks. Use **Registrar motivos de saída** para copiar motivo e justificativa do painel da Nuvemshop para a loja correta. Não há importação automática por nome: nomes podem se repetir ou mudar, e algumas lojas possuem várias saídas.

A lista fornecida em 05/10 foi analisada separadamente com o histórico de commits em [desinstalacoes-e-evolucao-do-sistema-2026-10-05.md](analises/desinstalacoes-e-evolucao-do-sistema-2026-10-05.md). Ela não alterou o banco. Para associar as 45 saídas aproximadas a cadastros, confirmar Store ID, país e data/horário do episódio antes de gravar os motivos.

## Medições a desenvolver depois

- Marcos persistentes da primeira configuração e exibição, e snapshot de configuração/plano no momento da saída.
- Primeira compra de produção com timestamp confiável, incluindo canceladas, para medir conversão por grupos de instalação em 30/60/90 dias.
- Histórico de ciclos de instalação/reinstalação para medir tempo até a saída de cada ciclo.
- Associação do SHA publicado e horário de deploy aos eventos de uso/saída. Datas de commits, sozinhas, não identificam a versão vista pelo lojista.
- Histórico de interação com campos e conclusão de personalização, sem valores preenchidos pelo comprador.

O GA4 já tem instrumentação do funil de compra e upgrade, mas precisa estar ativado para começar a gerar histórico. Os novos marcos não recuperam automaticamente a navegação passada.
