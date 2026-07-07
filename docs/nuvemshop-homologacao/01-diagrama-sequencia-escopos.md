# Diagrama de sequencia e escopos utilizados

Este artefato atende ao requisito: "Diagrama de sequencia e como ele deve representar os escopos utilizados".

O diagrama deve deixar explicito quais escopos sao usados em cada etapa. A autorizacao acontece uma vez no OAuth, mas a homologacao precisa enxergar por que cada permissao e necessaria.

## Diagrama

```mermaid
sequenceDiagram
    autonumber
    actor Lojista
    participant Nuvemshop as Nuvemshop
    participant App as Campos Personalizados
    participant DB as MySQL
    participant Storefront as Vitrine
    participant Cliente

    Lojista->>App: GET /install
    App->>Nuvemshop: Redireciona para OAuth com scopes\nread_store, read_products, read_orders,\nread_scripts, write_scripts, billing
    Nuvemshop-->>App: GET /oauth/callback?code&state
    App->>Nuvemshop: Troca code por access_token
    Nuvemshop-->>App: access_token, store_id, scope
    App->>Nuvemshop: Consulta dados da loja\nscope: read_store
    App->>DB: Salva store_id, token, scope e dados da loja
    App->>Nuvemshop: Lista/cria webhooks\napp/uninstalled, product/deleted,\nsubscription/updated, app/suspended, app/resumed
    App->>Nuvemshop: Lista/cria scripts de vitrine/checkout\nscopes: read_scripts, write_scripts
    App-->>Lojista: Redireciona para /admin

    Lojista->>App: Abre produtos e configura campos
    App->>Nuvemshop: Lista produtos\nscope: read_products
    Nuvemshop-->>App: Produtos
    App->>DB: Salva regras e campos por produto

    Cliente->>Storefront: Abre pagina de produto
    Storefront->>App: GET /public/stores/{storeId}/personalization
    App->>DB: Busca regras ativas e limites do plano
    App-->>Storefront: Campos configurados
    Storefront-->>Cliente: Renderiza campos no formulario do produto
    Cliente->>Nuvemshop: Adiciona item ao carrinho com properties[...]
    Nuvemshop-->>Lojista: Pedido contem dados de personalizacao

    opt Relatorios no painel
        Lojista->>App: Abre /admin/dashboard
        App->>Nuvemshop: Consulta pedidos\nscope: read_orders
        Nuvemshop-->>App: Pedidos com properties
    end

    opt Assinatura de plano pago
        Lojista->>App: POST /admin/billing/subscribe
        App->>Nuvemshop: Garante plano remoto do app\ncredencial do app
        App->>Nuvemshop: Atualiza assinatura da loja\nscope: billing
        Nuvemshop-->>App: Subscription atualizada
        App->>DB: Atualiza plano local somente apos sucesso 2xx
    end

    Nuvemshop-->>App: POST /webhooks/nuvemshop
    App->>App: Valida x-linkedstore-hmac-sha256
    App->>DB: Sincroniza ciclo de vida, produto removido ou billing
```

## Escopos OAuth

| Escopo | Onde aparece no fluxo | Motivo tecnico | Cuidado de homologacao |
| --- | --- | --- | --- |
| `read_store` | Pos-OAuth e billing | Ler nome, pais e moeda da loja para identificar a conta e aplicar preco correto por mercado. | Usar somente dados minimos da loja; nao expor token ou dados internos em logs. |
| `read_products` | `/admin/products`, `/admin/onboarding` | Listar produtos para o lojista escolher onde aplicar campos personalizados. | Nao alterar catalogo com esse escopo; apenas leitura. |
| `read_orders` | `/admin/dashboard` | Ler pedidos para mostrar relatorios de personalizacoes ja recebidas via `properties[...]`. | A leitura deve ser limitada ao painel/logica de relatorios; evitar armazenar dados de comprador desnecessariamente. |
| `read_scripts` | Instalacao/reinstalacao | Verificar se os scripts do app ja existem antes de criar duplicatas. | Idempotencia na instalacao e reconexao. |
| `write_scripts` | Instalacao/reinstalacao e desinstalacao | Registrar/remover o JavaScript que injeta os campos na vitrine/checkout. | Criar apenas scripts do proprio app e remover apenas scripts reconhecidos pelo app. |
| `billing` | `/admin/billing/subscribe` e sincronizacao | Criar/atualizar assinatura recorrente dos planos pagos do app. | Usar apenas para planos pagos aceitos; manter plano local inalterado se a API de billing falhar. |

## Como representar os escopos no diagrama

- Mostrar todos os escopos na etapa de redirecionamento OAuth.
- Repetir o escopo especifico ao lado da chamada em que ele e usado.
- Agrupar chamadas opcionais em blocos `opt`, como relatorios (`read_orders`) e assinatura (`billing`).
- Diferenciar chamadas feitas com token da loja das chamadas feitas com credencial do app.
- Indicar que webhooks sao validados antes de alterar estado local.
