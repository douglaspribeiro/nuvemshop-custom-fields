# Acesso universal de homologação

A única variável de ambiente para listas de lojas em homologação é `HOMOLOG_ALLOWED_STORE_IDS`.

| Valor | Comportamento |
|---|---|
| `0` | Bloqueia todas as lojas nas funcionalidades controladas |
| `*` | Autoriza todas as lojas, mantendo os demais requisitos |
| `123456,789012` | Autoriza somente esses IDs exatos |
| Ausente ou vazio | Bloqueia todas |
| Inválido, lista com `0` ou lista misturando `*` e IDs | Bloqueia todas |

Espaços ao redor dos valores são aceitos. IDs devem ser inteiros positivos sem zeros à esquerda. IDs não identificados nunca são autorizados.

## Funcionalidades controladas

- Biblioteca de templates, editor, revisão, gravação de produto como template e aplicação individual.
- Upload do comprador: criação/configuração de campos, interfaces administrativas, endpoints públicos e visibilidade do campo na vitrine. As cotas Pro/Ultra, armazenamento e validações continuam obrigatórios.
- Dodo Test: seleção e compras para lojas autorizadas, mantendo o catálogo e a exclusão do Brasil.
- Stripe sandbox e Creem sandbox: novas compras e upgrades onde suportados.
- Winback: criação e envio de mensagens na fila, mantendo `WINBACK_ENABLED` e demais condições.

Não é um bloqueio geral do aplicativo. Campos tradicionais, pagamentos em produção, webhooks, conciliação e cancelamento dos contratos existentes não recebem uma nova restrição. Rotinas de limpeza dos arquivos existentes continuam funcionando; os dados não são excluídos ao retirar uma loja da lista.

## Configuração e migração

```dotenv
HOMOLOG_ALLOWED_STORE_IDS=123456
```

Substitua pelo ID real da loja demo. Para liberar todas, configure explicitamente `HOMOLOG_ALLOWED_STORE_IDS=*`; para fechar a homologação, use `0`.

As antigas variáveis específicas de Stripe, Dodo Test e Winback não são mais lidas. Migre os IDs desejados para a variável universal antes de publicar. A lista agora é a mesma para todas as funcionalidades acima; não há seleção independente por funcionalidade.

Recrie/reinicie a aplicação com o novo ambiente para aplicar uma alteração. A configuração não muda automaticamente em instâncias já iniciadas. Um script compartilhado pode ser publicado para todas as lojas; o servidor continua filtrando o acesso e os campos de arquivo por ID. O teste ponta a ponta na loja real continua necessário.
