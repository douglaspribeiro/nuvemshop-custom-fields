# FAQ — Campos Personalizados

**Última atualização:** 01/09/2026

> Antes de publicar, substitua os campos entre colchetes pelos dados reais de contato, SLA, conta de teste e condições comerciais.

## 1. Gerais

### 1.1. Qual a categoria do aplicativo? Para que serve?

**Categoria:** Personalização de produtos / Gestão de loja.

O Campos Personalizados permite criar campos adicionais nos produtos da Nuvemshop, como nome, número, mensagem, data, medida e seleção de opções. As informações preenchidas pelo comprador acompanham o item no carrinho e no pedido.

### 1.2. Quais problemas o aplicativo resolve e como?

O aplicativo atende lojas que precisam coletar informações específicas para produzir ou preparar pedidos personalizados. Ele reduz o uso de mensagens manuais, planilhas e observações separadas, diminuindo erros no processamento dos pedidos.

### 1.3. Qual o perfil de lojista recomendado?

É indicado para lojas de camisetas personalizadas, canecas, brindes, convites, produtos gravados, itens sob medida, kits e outros produtos que dependam de informações fornecidas pelo comprador.

Requisitos:

- Loja ativa na Nuvemshop;
- Usuário com permissão de administrador;
- Produtos cadastrados;
- Tema compatível com a inserção de campos na página do produto.

### 1.4. Onde funciona o aplicativo?

O aplicativo funciona online, integrado à plataforma Nuvemshop/Tiendanube. A configuração atual contempla lojas nos mercados Brasil, Argentina, Chile, México e Colômbia, conforme disponibilidade comercial e publicação aprovada para cada país.

### 1.5. Quem desenvolveu o aplicativo e como funciona o suporte?

**Desenvolvedor:** `[informar nome da empresa ou responsável legal]`

**POC Nível 1**

- Canal: [central de suporte](https://campos-personalizados.wzhub.pro/support/);
- Dias e horários: `[informar]`;
- SLA de primeira resposta: `[informar]`.

**POC Nível 2**

- Contato: `[nome e e-mail do responsável pela escalação]`;
- Dias e horários: `[informar]`;
- SLA: `[informar]`.

**POC Comercial**

- Contato: `[nome, e-mail e telefone]`;
- Dias e horários: `[informar]`;
- SLA: `[informar]`.

**POC Comercial exclusivo Nuvemshop Next**

- Contato: `[nome e e-mail]`;
- SLA: `[informar]`;
- Possibilidade de negociar taxas: `[Sim/Não — explicar condições]`.

**POC Técnico**

- Contato: `[nome e e-mail]`;
- Dias e horários: `[informar]`;
- SLA: `[informar]`.

### 1.6. Temos uma conta teste disponível?

`[Sim/Não]`

Se houver conta de teste, informar URL da loja, usuário, senha, restrições e validade.

## 2. Planos e Preços

### 2.1. Qual a tabela de preços?

| Plano | Preço mensal | Limites |
|---|---:|---|
| FREE | R$ 0 | 1 produto personalizado e 1 campo por produto |
| Essencial | R$ 19,99/mês | 10 produtos personalizados e 3 campos por produto |
| Pro | R$ 29,99/mês | Até 50 produtos personalizados e campos ilimitados |

O plano Pro também inclui templates por nicho e relatórios operacionais. Não existe cobrança adicional por campo ou opção personalizada. O aplicativo coleta e transmite as informações, mas não altera o preço do produto.

Não há landing page pública de preços configurada atualmente. Os valores devem ser confirmados comercialmente antes da publicação.

Valores padrão configurados para outros mercados:

- Argentina: ARS 5.747 e ARS 8.622;
- Chile: CLP 3.566 e CLP 5.350;
- México: MXN 67,49 e MXN 101,26;
- Colômbia: COP 13.146 e COP 19.723.

### 2.2. Existe desconto para lojista Nuvemshop?

Não há desconto específico configurado atualmente. `[Confirmar condição comercial antes da publicação.]`

### 2.3. O aplicativo possui trial?

Não há período de trial configurado. O plano FREE pode ser utilizado sem cobrança.

### 2.4. Existe taxa de setup?

Não há taxa de setup configurada.

### 2.5. Existe preço diferenciado para Nuvemshop Next?

Não há precificação diferenciada configurada para a Nuvemshop Next. `[Confirmar eventual condição especial.]`

## 3. Instalação

### 3.1. URL da área de login

URL de instalação: [https://campos-personalizados.wzhub.pro/install](https://campos-personalizados.wzhub.pro/install)

O aplicativo não possui login e senha próprios. A autenticação é feita pela conta do lojista na Nuvemshop por OAuth.

### 3.2. Existem requisitos prévios?

- Loja ativa na Nuvemshop;
- Acesso de administrador;
- Usuário logado no painel da Nuvemshop;
- Produtos cadastrados;
- Autorização das permissões solicitadas.

O lojista não precisa gerar API keys. A integração utiliza OAuth.

Permissões utilizadas: `read_store`, `read_products`, `read_orders`, `read_scripts`, `write_scripts` e `billing` para os planos pagos.

### 3.3. Como é o processo de instalação?

1. Acesse [https://campos-personalizados.wzhub.pro/install](https://campos-personalizados.wzhub.pro/install).
2. Faça login na Nuvemshop, caso solicitado.
3. Revise as permissões apresentadas.
4. Clique em **Autorizar instalação**.
5. Aguarde o retorno ao aplicativo.
6. O aplicativo salvará a loja e instalará os scripts necessários.
7. Acesse **Configurar produtos** ou **Começar rápido**.
8. Selecione um produto.
9. Crie os campos ou aplique um template.
10. Salve a configuração.
11. Abra o produto na vitrine e adicione-o ao carrinho para testar.
12. Confirme se os dados aparecem no carrinho e no pedido.

### 3.4. O aplicativo possui tutoriais próprios?

Não há, no momento, tutorial público separado. Esta FAQ contém o tutorial de instalação.

Também estão disponíveis:

- Ajuda dentro do app: `/admin/help`;
- Central de suporte: [https://campos-personalizados.wzhub.pro/support/](https://campos-personalizados.wzhub.pro/support/).

### 3.5. Tutorial de instalação Nuvemshop

#### 3.5.1. Como instalar o aplicativo?

A instalação é feita pelo OAuth da Nuvemshop, utilizando a URL [https://campos-personalizados.wzhub.pro/install](https://campos-personalizados.wzhub.pro/install).

#### 3.5.2. Prints das etapas

Inserir capturas de tela da autorização, confirmação, painel inicial, lista de produtos, configuração dos campos, vitrine e carrinho/pedido.

#### 3.5.3. Direcionamento para suporte

Em caso de dúvidas, acesse [https://campos-personalizados.wzhub.pro/support/](https://campos-personalizados.wzhub.pro/support/). Após instalar ou reconectar o aplicativo, o lojista pode abrir e acompanhar chamados pela central de suporte.

#### 3.5.4. Considerações gerais

- O aplicativo não substitui o checkout nem processa pagamentos.
- Os dados são enviados como propriedades do item por meio de `properties[...]`.
- Os campos só aparecem em produtos com configuração ativa.
- Pode ser necessário atualizar a página ou limpar o cache da vitrine após a instalação.
- Ao desinstalar, a Nuvemshop remove os scripts registrados pelo aplicativo.

### Problemas comuns

| Problema | Solução |
|---|---|
| Campos não aparecem | Confira se o produto possui regra ativa, se o limite do plano não foi atingido e reconecte o app se necessário. |
| Produto não aparece | Reinstale o app e tente novamente; pode haver falha temporária de acesso à API. |
| Pedido não aparece no dashboard | Faça um pedido de teste e confirme a permissão `read_orders`. |
| Assinatura não atualiza | Confira o billing, os logs e a disponibilidade do mercado. |
| Plano premium bloqueado | Regularize a assinatura; o acesso é reativado após o evento `app/resumed`. |

## 4. Funcionamento

O lojista seleciona um produto e configura os campos que deseja exibir para o comprador.

Tipos disponíveis: texto curto, número, área de texto e lista de seleção. Cada campo pode conter rótulo, obrigatoriedade, placeholder, limite de caracteres, máscara, validação e opções de seleção.

Na vitrine, os campos aparecem na página do produto. Quando o comprador preenche as informações e adiciona o produto ao carrinho, os dados acompanham o item até o pedido da Nuvemshop.

O aplicativo também oferece templates por nicho, painel de uso, dashboard de pedidos personalizados, configuração de cores e registros de integração.
