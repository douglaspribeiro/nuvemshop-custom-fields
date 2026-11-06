# GA4: compra, upgrade e cupons

A aplicação mede o comportamento no navegador e confirma operações no backend. Não basta chegar à tela de processamento para registrar uma compra.

## Ativação

1. Na propriedade GA4, abra **Administrador → Fluxos de dados → fluxo Web → Segredos da API do Measurement Protocol** e crie um segredo.
2. Configure no servidor `GA4_MEASUREMENT_ID` e `GA4_API_SECRET`. O segredo pertence somente ao backend: não o coloque no HTML, no JavaScript ou no Git.
3. Publique a aplicação com a migração `V46__ga4_billing_events.sql`. O envio do backend só fica habilitado quando o ID e o segredo estão configurados.
4. Nas definições personalizadas do GA4, crie dimensões com escopo **Evento** para `flow_type`, `source_plan`, `target_plan`, `payment_provider`, `coupon` e `failure_stage`. Crie uma métrica personalizada `recurring_amount` do tipo moeda, se desejar comparar a mensalidade do novo plano.
5. Marque `purchase` e `upgrade_completed` como eventos principais, conforme o objetivo do relatório. Um upgrade proporcional gera ambos: analise-os separadamente para não somar duas conversões da mesma operação.
6. Nas configurações de medição otimizada do fluxo, desative interações de formulário se quiser manter somente os eventos explícitos desta instrumentação. Campos do pagador e cartão não fazem parte dos eventos implementados.

Deixar `GA4_MEASUREMENT_ID` vazio desativa a tag. Sem `GA4_API_SECRET`, o navegador continua medindo as etapas, mas as confirmações não são enviadas. Pagamentos continuam funcionando quando o GA está bloqueado ou indisponível.

## Identificar lojas no Tempo real do admin

As páginas autenticadas do admin enviam a propriedade de usuário **`store_id`**, com o ID da loja obtido da sessão, e o evento **`admin_page_view`** com `store_id`, `app_area=merchant_admin` e `admin_screen`. O contexto também acompanha os eventos posteriores do navegador, como início de checkout e escolha de plano. Não usa nome/e-mail do lojista nem transforma o ID da loja em User-ID de uma pessoa. Uma troca de loja na sessão atualiza o contexto; páginas sem contexto do admin limpam a propriedade da loja. O backoffice em modo loja não carrega a tag de navegação do lojista.

Depois do deploy, na propriedade GA4 que recebe `GA4_MEASUREMENT_ID`:

1. Acesse **Administrador → Exibição de dados → Definições personalizadas → Criar dimensão personalizada**.
2. Crie **Loja do admin (ID)**, escopo **Usuário**, propriedade de usuário **`store_id`**.
3. Opcionalmente, crie dimensões de escopo **Evento** para `store_id`, `admin_screen` e `app_area`, para relatórios e explorações das etapas.
4. Em **Relatórios → Tempo real**, use o cartão **Usuários por propriedade de usuário** e selecione a dimensão da loja. Na visão do usuário, confira a propriedade `store_id` e os eventos `admin_page_view`.
5. Para conferir uma navegação, abra o app como lojista e use, por exemplo, produtos, campos e planos. Os valores de `admin_screen` incluem `products`, `product_fields`, `plans`, `upgrade`, `subscription_payment` e `reactivation`.

O ID identifica a conta da loja (por exemplo, `959276` = LAB Cafe), não cada pessoa que acessa essa conta. A tela do GA mostra atividade recente, sem provar que a aba continua aberta. Bloqueadores e indisponibilidade da tag podem impedir a coleta. Para acompanhar visitas ao admin, use `admin_page_view`; eventos financeiros do backend não comprovam navegação do lojista.

Os eventos financeiros novos do Measurement Protocol também recebem o parâmetro de evento `store_id`. Eles não atualizam a propriedade de usuário do navegador durante renovações em segundo plano. Não há atualização retroativa de eventos já enviados.

A aplicação prepara a instrumentação; as dimensões precisam ser cadastradas no GA4. A entrega e o processamento devem ser conferidos no Tempo real/DebugView após a publicação; testes locais não confirmam ingestão no GA4.

Referências: [propriedades dos usuários no GA4](https://support.google.com/analytics/answer/12370404), [relatório de Tempo real](https://support.google.com/analytics/answer/9271392).

## Eventos e significado

| Evento | Momento | Origem |
|---|---|---|
| `view_item_list` | Exibição da comparação de planos | Navegador |
| `select_item` | Escolha de um plano para compra ou upgrade | Navegador |
| `upgrade_view` | Exibição do resumo de upgrade | Navegador |
| `coupon_applied` | Resumo de upgrade exibido com cupom validado | Navegador |
| `begin_checkout` | Abertura do formulário de assinatura Efí; saída para checkout externo; confirmação/continuação do resumo de upgrade | Navegador |
| `payment_view` | Formulário de pagamento do upgrade Efí | Navegador |
| `add_payment_info` | Efí tokenizou o cartão e vai enviar o pagamento; Paddle iniciou uma tentativa de pagamento | Navegador |
| `checkout_error` | Retorno ao formulário de pagamento com erro da aplicação | Navegador |
| `payment_failed` | Erro na tokenização, falha informada pelo Paddle ou falha confirmada pelo backend | Ambos |
| `purchase` | Primeira mensalidade confirmada; ajuste Efí com upgrade concluído, inclusive ajuste zero | Backend |
| `upgrade_completed` | Novo plano confirmado | Backend |
| `subscription_renewal` | Mensalidade confirmada após a primeira | Backend |

`flow_type` distingue `subscription`, `upgrade` e `renewal` nos eventos financeiros. Os parâmetros `source_plan`, `target_plan`, `payment_provider`, `currency`, `coupon` e `recurring_amount` são enviados quando aplicáveis. Cupom de primeira mensalidade aparece na compra inicial; não nas renovações.

`value` e o preço do item no ajuste Efí correspondem ao **ajuste cobrado**, não à nova mensalidade. A mensalidade fica em `recurring_amount`. Upgrade em outros provedores gera `upgrade_completed` após a confirmação do plano; a mudança de plano, sozinha, não estabelece receita de ajuste. Eventos de falha distinguem `tokenization`, `server`, `provider_browser` e `provider`. Eles contam ocorrências, não cobranças únicas: uma falha pode aparecer no navegador e depois no backend.

## Relatórios

Em **Explorar → Exploração de funil**, crie:

- **Compra:** `view_item_list` → `select_item` → `begin_checkout` → `purchase`; filtre `flow_type = subscription`. Aplique `target_plan` ou `payment_provider` como detalhamento.
- **Upgrade:** `select_item` → `upgrade_view` → `begin_checkout` → `upgrade_completed`; filtre `flow_type = upgrade` para as etapas após a escolha. Na etapa de comparação de planos, `view_item_list` é comum aos dois fluxos.
- **Pagamento Efí/Paddle:** `begin_checkout` → `add_payment_info` → `purchase`. Filtre o provedor. Mercado Pago não oferece uma etapa de preenchimento observável no nosso domínio; use o funil sem `add_payment_info` para ele.

O abandono é a diferença entre os usuários de etapas consecutivas. Use um funil aberto para explorar acessos diretos às telas de pagamento e um fechado para estudar a jornada completa a partir da comparação.

Para **conversão por plano**, compare usuários que selecionaram o item e usuários com compra, detalhando `target_plan`; separe assinatura e upgrade. Para **cupons**, crie uma exploração de tabela com `coupon`, `flow_type` e `target_plan`, filtrando `purchase`, e use compras e receita de compras. Compare também usuários com `coupon_applied` e `upgrade_completed` no funil de upgrade; `coupon_applied` conta visualizações do resumo válido, não aplicações únicas.

Para **falhas**, crie uma tabela filtrando `payment_failed`/`checkout_error` e detalhe provedor, plano e `failure_stage`. Para **renovações**, filtre `subscription_renewal` e use a contagem de eventos; esse evento próprio não alimenta a receita de compras padrão. A receita recorrente e a conciliação continuam no backoffice.

## Validação

- Verifique no navegador as chamadas `gtag` e, no tempo real do GA4, os eventos de início do fluxo. Para usar o DebugView com os eventos do navegador, habilite o modo de depuração pela ferramenta de teste da tag.
- Valide o JSON do backend no endpoint de depuração do Measurement Protocol antes de uma verificação de ponta a ponta. O endpoint de coleta pode retornar sucesso HTTP mesmo que um payload seja inválido; isso não comprova que o evento apareceu no relatório.
- Confirme uma operação de produção controlada e verifique `transaction_id`, plano, moeda e valor. Não use cobranças de sandbox para validar relatórios de produção: os eventos de funil e backend de sandbox são ignorados.
- Repetir a conciliação ou atualizar a tela não deve criar outra compra. A fila `ga4_outbox` usa uma chave única por evento e o `transaction_id` é estável nas compras.
- A entrega tem reenvio com intervalo progressivo. Eventos com mais de 71 horas expiram, para não alterar a data de uma operação antiga. `delivered_at` indica resposta HTTP aceita, e não validação analítica do GA4.

## Limites do histórico e da atribuição

Os eventos começam após a publicação e ativação; não há importação de vendas antigas. A confirmação utiliza o `client_id` e, na primeira compra/upgrade, o `session_id` obtidos da tag e persistidos antes do pagamento. Sem identificador do navegador, a aplicação não inventa um usuário para enviar a compra. Bloqueadores, restrições de cookies e saída muito rápida da página podem diminuir a cobertura; o GA não é a referência financeira.

Os identificadores aceitos são numéricos. Nome, e-mail, CPF, cartão, tokens, mensagens de erro do provedor e parâmetros da URL não são enviados pela instrumentação. A tag existente mantém a URL sem query string e o referenciador vazio: atribuição detalhada por campanhas/UTM não está incluída neste trabalho. Eventos do Measurement Protocol muito atrasados também podem não se associar à navegação original como esperado.

Referências: [ecommerce GA4](https://developers.google.com/analytics/devguides/collection/ga4/ecommerce), [envio pelo Measurement Protocol](https://developers.google.com/analytics/devguides/collection/protocol/ga4/sending-events), [referência do protocolo](https://developers.google.com/analytics/devguides/collection/protocol/ga4/reference), [eventos do Paddle.js](https://developer.paddle.com/paddle-js/events/).
