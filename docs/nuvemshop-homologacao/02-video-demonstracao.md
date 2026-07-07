# Roteiro do video de demonstracao

Este artefato atende ao requisito: "Video de demonstracao com os cenarios que precisam ser exibidos, como instalacao via Nuvemshop, login, reinstalacao, entre outros".

## Objetivo do video

Demonstrar que o app instala corretamente pela Nuvemshop, autentica a loja, permite configurar campos personalizados, reflete os campos na vitrine, preserva o fluxo de compra e trata reinstalacao/reconexao.

## Cenas obrigatorias

### 1. Instalacao via Nuvemshop

1. Abrir a loja Nuvemshop logada como administrador.
2. Acessar o link de instalacao do app.
3. Mostrar a tela de autorizacao da Nuvemshop com as permissoes solicitadas.
4. Confirmar a instalacao.
5. Mostrar o redirecionamento para o painel do app.

### 2. Login e acesso ao painel

1. Mostrar o app aberto no painel da loja.
2. Confirmar que a loja correta esta conectada.
3. Abrir a tela inicial do app.
4. Navegar para `Produtos` ou `Comecar rapido`.

### 3. Configuracao de campos personalizados

1. Listar produtos carregados da Nuvemshop.
2. Escolher um produto.
3. Criar pelo menos um campo obrigatorio.
4. Criar um campo de selecao ou area de texto, se aplicavel.
5. Salvar a configuracao.

### 4. Validacao na vitrine

1. Abrir a pagina do produto configurado na loja.
2. Mostrar os campos renderizados no formulario do produto.
3. Preencher os campos.
4. Adicionar o produto ao carrinho.
5. Mostrar os dados da personalizacao no carrinho ou no pedido, conforme o tema da loja.

### 5. Dashboard e leitura de pedidos

1. Voltar ao painel do app.
2. Abrir o dashboard.
3. Mostrar os pedidos/personalizacoes recentes quando houver pedido de teste disponivel.

### 6. Reinstalacao ou reconexao

1. Acessar novamente o link `/install`.
2. Confirmar a autorizacao com a mesma loja.
3. Mostrar que o app retorna ao painel sem duplicar configuracoes.
4. Mostrar que os campos ja configurados continuam disponiveis.

### 7. Planos e assinatura, se billing estiver ativo

1. Abrir `/admin/billing`.
2. Mostrar os planos disponiveis e limites.
3. Selecionar um plano pago em loja de teste.
4. Mostrar mensagem de sucesso ou erro amigavel.
5. Confirmar que o plano local muda somente apos retorno bem-sucedido da Nuvemshop.

### 8. Suporte e paginas publicas

1. Abrir `/admin/help`.
2. Mostrar eventos recentes da loja.
3. Abrir `/privacy/`.
4. Abrir `/support/`.

## Cuidados para gravacao

- Usar loja de teste, sem dados reais de clientes.
- Nao mostrar `client_secret`, tokens, variaveis de ambiente ou logs sensiveis.
- Cortar ou ocultar dados pessoais de pedidos.
- Mostrar a URL HTTPS publica usada pelo app apenas quando necessario.
- Se billing real estiver habilitado, usar ambiente/loja aprovados para teste de cobranca.

## Evidencias esperadas no video

- OAuth concluido.
- Produto carregado pela API.
- Campo criado no painel.
- Campo renderizado na vitrine.
- Dados enviados no carrinho/pedido via `properties[...]`.
- Reinstalacao sem duplicar configuracao.
- Tela de planos, caso o app seja apresentado com planos pagos.
