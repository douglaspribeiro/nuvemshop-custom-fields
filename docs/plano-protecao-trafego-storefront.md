# Proteção do tráfego da vitrine

Data: 21/10/2026.

## Objetivo

Reduzir consultas, gravações e logs causados por lojas sem personalização utilizável, preservando capacidade para as lojas que usam campos.

## Etapa atual

1. Cache de estado por loja: loja ativa e existência de campos visíveis em pelo menos uma regra habilitada. Respeita filtros de plano, inclusive imagens. Uma loja sem campos recebe resposta desabilitada antes do cache e da consulta por produto. Somente valores imutáveis entram no cache; erros de consulta propagam e não são armazenados como ausência de campos.
2. Invalidação por loja após commit para alterações na loja, produtos e campos, incluindo exclusão de campo por operação bulk. Rollback preserva o cache. Alterações no catálogo de planos continuam invalidando todas as lojas. Gerações impedem que carregamentos concorrentes anteriores ao commit reintroduzam entradas antigas. Tanto entradas quanto gerações têm tamanho limitado.
3. Beacons SDK: persistir no máximo uma amostra por loja por minuto, independentemente de produto ou motivo, por instância da aplicação e enquanto a chave estiver no cache limitado de 10.000 lojas. Eventos suprimidos e amostrados são contabilizados nas métricas agregadas `ncf.storefront.beacon.suppressed` e `ncf.storefront.beacon.sampled`. O backoffice passa a mostrar amostras, não histórico completo de navegação. Logs de leitura de configuração e de beacons passam a DEBUG. A amostragem não é reiniciada pelas alterações de configuração.

O TTL do estado permanece `STOREFRONT_CACHE_TTL` (padrão: 1 minuto). Um primeiro campo confirmado reativa a loja por invalidação local, sem esperar o TTL. Caches são locais: outras instâncias convergem pelo TTL. Não há mudança nos scripts publicados nem necessidade de republicá-los nesta etapa.

## Validação

- Loja sem produtos e loja com regra sem campos: resposta desabilitada sem busca individual de produto.
- Primeiro campo: reativação após commit; rollback mantém estado anterior.
- Último campo removido e regra desabilitada: retorno ao estado desabilitado.
- Restrições de plano continuam aplicadas aos campos.
- Alterar uma loja preserva entradas de outra; alteração de plano invalida globalmente.
- Carga em andamento não pode repovoar a geração atual com resultado antigo.
- Beacons com produtos/motivos variados mantêm uma gravação por janela, contabilizando os demais.

## Próximas etapas, fora desta entrega

4. Cache HTTP/CDN das respostas públicas, com política explícita de expiração e atualização.
5. Proteção de volume por loja na borda.
6. Estado leve no navegador para evitar requisições repetidas, com expiração que permita descobrir novos campos.

## Verificação operacional após implantação

Comparar volume de consultas, gravações em `integration_logs`, taxa de acerto do Caffeine, beacons amostrados/suprimidos e latência da personalização. Separar lojas sem campos das lojas com uso. O objetivo desta etapa é reduzir custo por requisição; o número de requests recebidos permanece até as etapas de CDN/navegador.
