# Requisitos tecnicos e cuidados com assinatura de planos pagos

Este artefato atende ao requisito: "Requisitos tecnicos e cuidados com etapas de assinatura, caso o app tenha planos pago".

## Planos do app

| Plano | Cobranca automatica | Limites |
| --- | --- | --- |
| `FREE` | Nao | 1 produto personalizado e 1 campo por produto |
| `PREMIUM` | Sim, quando billing estiver habilitado | Ate 10 produtos personalizados e ate 3 campos por produto |
| `PREMIUM_PLUS` | Sim, quando billing estiver habilitado | Produtos e campos ilimitados |
| `FREE_GRATIS` / Premium Cortesia | Nao | Liberacao manual sem cobranca automatica |

## Requisitos tecnicos

1. O app deve solicitar o escopo `billing` no OAuth antes de oferecer `PREMIUM` e `PREMIUM_PLUS`.
2. `NUVEMSHOP_BILLING_ENABLED` deve estar ativo apenas em ambiente pronto para cobranca real.
3. `NUVEMSHOP_BILLING_CONCEPT_CODE` precisa estar configurado com o conceito de assinatura recorrente aceito para o app.
4. A tabela de precos por pais/moeda precisa estar completa para os mercados liberados: BR, AR, MX, CO e/ou CL.
5. O codigo deve bloquear assinatura quando a moeda da loja nao corresponder a tabela configurada.
6. O plano remoto deve ser garantido antes da assinatura, usando os `external_id` configurados para `PREMIUM` e `PREMIUM_PLUS`.
7. A assinatura da loja deve ser atualizada pela Billing API usando o token da loja.
8. O plano local (`stores.plan`) so deve mudar depois de resposta bem-sucedida da Nuvemshop.
9. Em falha da API, o app deve manter o plano anterior, gravar `billing_last_error` e mostrar erro amigavel ao lojista.
10. `FREE` e `FREE_GRATIS` nao devem gerar cobranca automatica.
11. Loja com `courtesyPremium=true` nao deve conseguir iniciar cobranca automatica enquanto a cortesia estiver ativa.

## Webhooks de assinatura e ciclo de vida

| Evento | Tratamento esperado |
| --- | --- |
| `subscription/updated` | Sincronizar plano, valor, moeda e datas de execucao com a Nuvemshop. |
| `app/suspended` | Marcar `billing_suspended=true` e bloquear acesso premium. |
| `app/resumed` | Marcar `billing_suspended=false` e sincronizar a assinatura remota. |
| `app/uninstalled` | Limpar assinatura local, voltar plano para `FREE`, remover scripts e marcar loja como desinstalada. |

Todos os webhooks devem validar `x-linkedstore-hmac-sha256` antes de alterar estado local.

## Checklist antes de publicar cobranca

- Confirmar no portal da Nuvemshop que o escopo `billing` esta cadastrado.
- Confirmar que os scopes cadastrados batem com `NUVEMSHOP_SCOPES`.
- Confirmar que a URL publica usa HTTPS em `APP_BASE_URL` e no `NUVEMSHOP_REDIRECT_URI`.
- Confirmar que os webhooks apontam para `/webhooks/nuvemshop`.
- Confirmar que `NUVEMSHOP_BILLING_API_BASE_URL` aponta para a versao correta da API.
- Confirmar `NUVEMSHOP_BILLING_CONCEPT_CODE`.
- Confirmar os `external_id` dos planos pagos.
- Confirmar precos por pais/moeda.
- Confirmar que `NUVEMSHOP_CLIENT_SECRET` nao aparece em resposta HTTP, template, JavaScript publico ou log.
- Testar instalacao nova, reconexao, desinstalacao, assinatura, suspensao, retomada e mudanca de plano.

## Comportamento em falhas

- Falha ao criar/atualizar assinatura: manter plano anterior.
- Falha ao consultar assinatura no webhook: registrar erro e nao liberar beneficio indevido.
- Mercado sem preco configurado: bloquear assinatura e informar que o mercado ainda nao esta disponivel.
- Loja suspensa: manter regras salvas, mas aplicar limites de acesso premium conforme `billing_suspended`.
- Desinstalacao: remover acesso local e scripts do app mesmo que a limpeza remota falhe parcialmente.
