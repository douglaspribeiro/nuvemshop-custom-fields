# Plano: ofertas gratuitas com duração configurável

Registrado em 15/10/2026. Status: planejamento; nenhuma alteração funcional implementada.

## Decisão de produto

- Não criar um novo plano pago de um produto. O Essencial permanece como opção paga de entrada; esta mudança não redefine Pro e Ultra.
- Preservar inicialmente as lojas que já estão no FREE, sem iniciar prazo retroativo ou mudar seus limites.
- Encerrar novas adesões ao FREE atual por configuração no backoffice, mantendo o atendimento das lojas existentes.
- Permitir criar ofertas gratuitas temporárias pela tela de planos: começar com `FREE_30`, de 30 dias, e depois poder experimentar `FREE_14`, de 14 dias, sem alteração de código ou nova publicação para cada duração.
- A eventual migração gradual da base antiga será uma ação futura e separada. Criar uma oferta ou fechar novas adesões não deve migrar lojas existentes.

## Suporte necessário na tela de planos

O backoffice deve permitir criar uma oferta gratuita, e não apenas uma nova versão dos tipos fixos existentes. Cada oferta deve conter:

- Código único, como `FREE_30` ou `FREE_14`, nome e descrição apresentados ao lojista.
- Natureza gratuita temporária e duração em dias, obrigatória e positiva para essa modalidade.
- Limites de produtos, campos e imagens, seguindo as validações do catálogo.
- Controle de disponibilidade para novas adesões, independente da validade dos vínculos existentes.
- Seleção explícita da oferta padrão para novas instalações, sem ambiguidades entre ofertas abertas.
- Histórico de alterações e identificação de quem criou, alterou ou encerrou a disponibilidade da oferta.

A vigência da oferta no catálogo e a duração do acesso de cada loja são conceitos diferentes: uma oferta pode aceitar instalações por meses e conceder apenas 30 dias a cada loja.

Fechar uma oferta para novas adesões deve preservar seu cadastro, histórico e vínculos. Alterar a duração ou os limites não deve mudar silenciosamente o que já foi concedido às lojas.

## Vínculo e ciclo do período gratuito

Proposta para implementação, com os detalhes de expiração a validar antes da entrega:

- Registrar por loja a oferta e versão concedidas, duração contratada, início e término do período, além do estado do acesso.
- Iniciar o prazo na concessão da oferta à nova instalação, sem depender da primeira venda. Persistir instantes e exibir datas no fuso de São Paulo.
- Preservar as condições concedidas: uma loja no `FREE_30` mantém seu término original mesmo que o padrão passe a ser `FREE_14`.
- Não reiniciar o prazo em acessos, reprocessamentos de eventos ou reinstalações. Definir eventual novo teste como concessão administrativa explícita e auditada.
- Permitir contratação de plano pago durante o teste; ao confirmar a assinatura, o acesso passa a seguir o plano contratado. O vencimento do teste antigo não pode bloquear uma loja que já contratou.
- Ao vencer sem contratação, exigir plano pago para continuar a personalização, preservando configurações e histórico. Não retornar automaticamente ao FREE antigo nem cobrar automaticamente pelo fim do teste.
- Definir antes da implementação o comportamento na vitrine, carrinho e checkout durante a expiração, inclusive para personalizações já adicionadas. Preservar os dados de pedidos existentes.
- Exibir prazo e estado no painel do lojista e no backoffice; prever avisos antes do término e orientação de contratação.

## Adequação técnica

Hoje o catálogo usa `PlanType` com identidades fixas e a tela cria versões dessas identidades. Apenas adicionar `FREE_30` e `FREE_14` ao enum não atende ao requisito de criação pelo backoffice.

A implementação deve separar a oferta comercial configurável da identidade usada para permissões e billing. Avaliar a extensão de `PlanAsset` ou uma entidade de ofertas vinculada ao catálogo, preservando a compatibilidade com planos existentes, limites e integrações de pagamento. A duração gratuita pertence à oferta e ao vínculo da loja; não deve ser tratada como uma assinatura recorrente de valor zero em um gateway.

Revisar seleção de plano na instalação e reinstalação, concessão de acesso, cache do catálogo, validações de limites, telas de planos, expiração, relatórios e pontos de retorno ao FREE. O fechamento do FREE para novas adesões precisa valer no servidor e em todos os caminhos de entrada, além de sua apresentação na interface.

## Relatórios e avaliação

- Separar FREE legado, `FREE_30` e `FREE_14`, com identificação da oferta originalmente concedida.
- Medir por grupo de entrada: instalações, produtos habilitados, primeiro pedido personalizado, conversão paga e desinstalações, considerando o tempo de observação de cada grupo.
- Mostrar testes em andamento, expirados e convertidos, sem incluir gratuidade temporária no MRR.
- Preservar a distinção do relatório atual entre plano atual e oferta de origem; o plano atual sozinho não permite comparar a conversão dos testes.

## Etapas propostas

1. Implementar ofertas configuráveis e vínculos com condições preservadas, sem migrar a base.
2. Disponibilizar criação e gestão na tela de planos, seleção do padrão e encerramento de novas adesões.
3. Implementar prazo, comunicação, expiração e contratação paga, validando os efeitos na vitrine.
4. Implementar relatórios por oferta e testes de regressão do FREE legado e dos planos pagos.
5. Com a funcionalidade validada, cadastrar `FREE_30` e fechar novas adesões ao FREE atual por decisão operacional explícita.
6. Posteriormente, cadastrar `FREE_14` pelo backoffice e alterar a oferta padrão para comparar os resultados, preservando os testes de 30 dias em andamento.

## Critérios de aceite

- Um administrador cria `FREE_30` com 30 dias e `FREE_14` com 14 dias sem mudança de código.
- A oferta padrão é inequívoca; não é possível fechar a oferta padrão sem definir como serão atendidas novas instalações.
- Ao fechar o FREE legado, novas lojas não o recebem e lojas existentes continuam com suas condições.
- Trocar o padrão de 30 para 14 dias afeta somente novas concessões.
- Alterações no catálogo não recalculam prazos nem limites já concedidos silenciosamente.
- Expiração funciona mesmo sem novo login e sem depender apenas de um processamento agendado; verificações de acesso consideram o término persistido.
- Expiração preserva dados e não bloqueia assinaturas pagas confirmadas; reinstalação não renova o teste automaticamente.
- Não existe cobrança automática ao final do período gratuito sem contratação.
- O backoffice identifica oferta, prazo, estado e conversão de cada loja.

## Definições pendentes para implementação

- Limites de produtos, campos e imagens de cada oferta e quais recursos poderão ser experimentados.
- Tratamento exato da vitrine e de carrinhos em andamento no vencimento.
- Calendário e canais de avisos, aproveitando as preferências e canais disponíveis.
- Política futura de migração da base antiga, fora do escopo inicial.
