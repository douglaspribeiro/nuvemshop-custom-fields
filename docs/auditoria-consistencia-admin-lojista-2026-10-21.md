# Auditoria de consistência — 21/10/2026

## Correções preparadas

- Relatórios, resumo e CSV distinguem histórico desconhecido, parcial e completo. Histórico pendente ou com erro não é apresentado como zero vendas; taxas ficam indisponíveis sem cobertura completa.
- Pedidos personalizados exigem correspondência entre produto e nome de campo conhecido, com valor preenchido. Metadados genéricos não contam. Valores nulos e objetos não contam; múltiplas opções e zero são preservados.
- Nomes anteriores são preservados ao renomear ou excluir campos, regras e produtos. O catálogo também consulta snapshots históricos existentes, compartilhando o reconhecimento entre relatórios do lojista e backoffice.
- Falhas de API aparecem como indisponibilidade, sem virar lista vazia ou orientar reinstalação indevida. Erros de sincronização distinguem token inválido, bloqueio da plataforma, permissão e limite de API.
- Sincronização retoma o histórico por checkpoints, distribui o processamento entre lojas e prioriza planos pagos em empates. Interrupções não exigem repetir todo o histórico.
- Lojas com exclusão de dados solicitada ficam fora dos totais de lojas ativas. Exclusão de dados também remove os nomes históricos.
- MRR exige assinatura de produção ou assinatura legada com valor registrado; preço de catálogo e plano manual não inventam receita.

## Conferência de produção somente por leitura

Foram consultadas as 16 lojas em planos pagos. No período de 22/09/2026 a
21/10/2026, quatro lojas tinham 17 pedidos pagos e não cancelados, com 32 unidades
personalizadas reconhecidas. O dia 21 ainda estava em andamento na consulta.

| Plano atual | Lojas consultadas | Com pedidos personalizados | Pedidos | Unidades |
|---|---:|---:|---:|---:|
| Essencial | 5 | 1 | 2 | 2 |
| Pro | 7 | 1 | 5 | 19 |
| Ultra | 4 | 2 | 10 | 11 |

A loja da Jersey tinha 38 pedidos pagos e não cancelados com 43 unidades
personalizadas no histórico consultado, mas nenhum no período acima. Uma loja
Pro excedeu o prazo da consulta de histórico completo; sua consulta específica
do período terminou sem pedidos personalizados. Os totais da tabela representam
o período, não uma auditoria completa de todo o passado de todas as lojas.

Havia 102 lojas ativas: 86 Free, 5 Essencial, 7 Pro e 4 Ultra. Três lojas Free
tinham impedimentos externos confirmados: uma com token inválido (401), duas
com acesso ao aplicativo bloqueado pela plataforma (402). A aplicação passa a
exibir esses impedimentos; reconexão ou regularização ainda são necessárias.

Nenhuma migração, escrita, implantação ou reinicialização foi feita em produção.

## Validação concluída

- Suíte Java final: 665 testes, sem falhas, erros ou testes ignorados.
- Inclui três contratos de migração em MySQL real, atualização de base populada,
  instalação das 60 migrações, validação Hibernate e idempotência.
- Cobertura de renomeação/exclusão de campos, recuperação por snapshots, eliminação
  de dados, valores preenchidos, falhas de API, checkpoint, CSV e exclusão pendente.
- Frontend: 108 testes aprovados, verificação de tipos e build aprovados.
- Scripts: 23 testes aprovados.
- Aplicação empacotada inicializada com MySQL descartável; conferência HTTP local
  com 21 verificações aprovadas. Gateways desativados, sem cobrança real.
- Ambiente temporário encerrado após a validação. Não se realizou nova inspeção
  visual, conforme orientação do usuário para concentrar a conclusão nos dados.

## Publicação e limites

Publicar a imagem da aplicação com V59 e V60; o recálculo histórico será realizado
pela sincronização após a atualização. A conferência por API acima não gravou os
resultados no banco de produção. Nenhum bundle de vitrine foi alterado nesta revisão.

Nomes antigos sem qualquer registro sobrevivente não podem ser reconstruídos.
As novas gravações preservam nomes anteriores, e os snapshots existentes ajudam
a reconhecer parte do passado. Os testes de pagamento são simulados; o bloqueio
ARS relatado pela Stripe depende de esclarecimento do provedor e não foi resolvido
por esta revisão. Não se afirma ausência de qualquer outro defeito no produto.
