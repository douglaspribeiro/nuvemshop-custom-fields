# Remodelação do painel do lojista

## Objetivo

Depois da instalação, o lojista deve entender em menos de um minuto se a
personalização está funcionando, qual é a próxima ação e onde acompanhar os
pedidos personalizados. O painel deve ter aparência consistente com um produto
estável e ser confortável em desktop e celular.

## Diagnóstico do painel atual

- A página inicial mostra dados internos como Store ID e seis botões de peso
  visual parecido; falta uma ação principal clara.
- Produtos, campos, estilo, pedidos, planos e ajuda têm cabeçalhos e navegação
  repetidos, mas não compartilham uma estrutura fixa de navegação.
- O dashboard apresenta contadores e uma tabela, sem contextualizar os números
  nem orientar o que fazer quando a loja ainda não tem pedidos.
- O onboarding é uma página separada, enquanto a configuração real acontece em
  produtos e campos. O progresso da implantação não fica visível.
- Os estados de carregamento, vazio, erro e sucesso não seguem uma linguagem
  visual única. Tabelas ficam densas em telas estreitas.

## Estrutura proposta

Navegação principal persistente: **Visão geral**, **Produtos e campos**,
**Pedidos personalizados**, **Aparência**, **Planos e cobrança** e **Ajuda**.
O nome da loja, o plano atual e o acesso ao suporte ficam no topo. Cada tela
tem uma ação primária e, quando necessário, ações secundárias discretas.

### Visão geral

```text
Loja: Minha Loja                    Plano Essencial       Ajuda
Visão geral
Personalização ativa em 3 produtos                 [Configurar produto]

[3 produtos configurados] [8 campos ativos] [12 pedidos personalizados]

Próximos passos
✓ Integração conectada
✓ Primeiro produto configurado
○ Revisar aparência na loja                         [Abrir aparência]

Atividade recente                   Estado da integração
Pedido #1234 ...                    Scripts instalados e funcionando
```

Dados internos, como Store ID e IDs de script, vão para uma seção técnica
expansível em Ajuda. Os contadores devem indicar período e origem dos dados.

### Produtos e campos

Lista com busca, estado de configuração e ação **Editar campos** por produto.
No editor, organizar a criação em três blocos: tipo de campo, regras de
preenchimento e prévia. Mostrar claramente o limite do plano e salvar com
feedback sem deslocar a página. Em celular, usar cartões em vez de tabela larga.

### Pedidos personalizados

Resumo dos pedidos com filtros de período e busca por número. Cada pedido abre
os valores enviados pelo comprador em uma visualização legível, com opção de
copiar os dados. Estado vazio explica quando o primeiro pedido aparecerá; erro
de permissão aponta a ação de reconectar a loja.

### Aparência

Prévia ao lado dos controles em desktop e abaixo em celular. Nomear os controles
pelo local onde a cor aparece: produto, carrinho e checkout. Oferecer valores
padrão e uma ação clara para restaurar cada cor.

### Planos e cobrança

Mostrar plano atual, uso do limite, próxima cobrança e status do pagamento em
uma área única. Comparação entre planos abaixo. O formulário de cartão Efí deve
seguir a mesma identidade visual, com resumo do preço e feedback de cobrança
pendente, aprovada ou recusada.

## Sistema visual

- Tipografia com escala simples para título da página, título de seção, corpo e
  rótulos. Contraste AA para texto e controles.
- Fundo neutro claro, superfícies brancas, cor de ação verde escuro e cores de
  estado reservadas a sucesso, atenção e erro.
- Espaçamento em múltiplos de 4 px, cartões com raio consistente e sombras
  discretas. A navegação e os botões devem ser reconhecíveis sem excesso de
  bordas.
- Componentes reutilizáveis: cabeçalho, navegação, botão, aviso, cartão de
  métrica, estado vazio, campo, tabela responsiva e painel de suporte.
- Mobile primeiro para leitura e ações; teclado, foco visível e textos de erro
  associados ao campo para acessibilidade.

## Entrega por etapas

1. Criar a estrutura compartilhada e os tokens visuais; migrar Visão geral e
   navegação. Critério: uma ação primária clara e acesso a todas as telas em
   dois cliques ou menos.
2. Migrar Produtos e campos com prévia e estados de salvamento. Critério:
   configurar um produto do zero sem recorrer à página de Ajuda.
3. Migrar Pedidos, Aparência, Planos e Ajuda. Critério: estados vazios, erros e
   cobrança pendente têm uma ação útil e texto compreensível.
4. Validar com lojas de teste em desktop e celular, teclado e leitores de tela;
   medir tempo até o primeiro produto configurado e taxa de conclusão do fluxo.

## Dependências e cuidados

- Preservar as rotas e formulários usados pela integração Nuvemshop enquanto a
  interface muda.
- Manter português e espanhol equivalentes. O novo formulário Efí precisa da
  tradução completa antes de ser oferecido a lojas fora do Brasil; por ora,
  assinaturas Efí ficam restritas a lojas brasileiras.
- Não afirmar certificação PCI DSS nem usar selo de conformidade sem validação.
- Não migrar automaticamente cartões ou assinaturas existentes no Mercado Pago;
  cada lojista terá de autorizar uma nova assinatura na Efí.
