# Material para a loja de aplicativos Nuvemshop — Brasil

Pacote preparado em 05/10/2026. O conteúdo não foi publicado no Partner Portal.
O vídeo será produzido pelo responsável usando o sistema.

## Arquivos para publicar

- `descricao-pt-BR.md`: descrição curta e longa para copiar nos campos do perfil.
- `imagens/01-personalizacao-na-vitrine.png`: texto, mensagens e escolhas na página do produto.
- `imagens/02-opcoes-com-imagens.png`: seleção de capas e modelos cadastrados pelo lojista.
- `imagens/03-configuracao-dos-campos.png`: editor de campos por produto.
- `imagens/04-aparencia.png`: cores no produto, carrinho e checkout.
- `imagens/05-pedidos-personalizados.png`: consulta às informações dos pedidos recentes.
- `imagens/06-personalizacao-no-item-vendido.png`: representação ilustrativa dos detalhes registrados no item após a compra.

Se o Portal aceitar apenas cinco imagens, recomendamos usar 01, 02, 03, 04 e 06.
A 05 fica como alternativa para destacar o relatório do aplicativo.

As seis imagens são PNG de **1600 × 800 pixels**, no formato indicado no
[guia de publicação](https://dev.nuvemshop.com.br/en/docs/homologation/publication).
Os [requisitos atuais](https://nuvemshop.dev/apps/publish/app-publication/requirements)
pedem telas e funcionalidades do app, JPEG/PNG e pacotes por país. Conferir o tamanho
solicitado no campo do Portal antes de subir, pois o painel pode atualizar requisitos.

Este pacote é em português e apresenta preços brasileiros. Para outros países,
localizar textos e preços e criar o pacote correspondente. Não reutilizar a tabela BR.

## Origem e limites das imagens

As telas administrativas usam os templates e CSS reais do aplicativo, renderizados
pelo Spring/Thymeleaf com uma loja fictícia e pedidos demonstrativos. As duas cenas
de vitrine usam uma loja ilustrativa e o JavaScript real `nuvemshop-personalizer.js`
para renderizar os campos. Os cadernos são ilustrações estáticas: não representam
prévia automática de impressão ou edição de arte no produto.

A imagem 06 é uma ilustração dos dados que ficam vinculados ao item do pedido;
não é um print real nem uma reprodução da interface administrativa da Nuvemshop.
Seu exemplo usa as propriedades do item conforme a
[API de pedidos](https://nuvemshop.dev/api/resources/2025-03/order). Para um print
real dessa interface, será necessário capturar uma venda demonstrativa no painel
da loja, sem divulgar dados pessoais de clientes.

Os layouts promocionais foram produzidos em HTML/CSS e capturados pelo Chromium.
As fontes editáveis estão em `fontes/`; não foi usado gerador de imagens por IA.
As capturas contêm apenas dados fictícios. Não anunciar esses pedidos como vendas
reais ou usar esses números como prova social.

O upload pelo comprador aparece **somente na descrição, como em desenvolvimento**.
Nenhuma imagem mostra uma interface de upload já disponível. Seus limites futuros
ainda não foram definidos. O roteiro técnico está em
[planejamento do upload](../../plano-upload-imagens-comprador.md).

## Antes de atualizar o perfil

Confirmar no ambiente publicado os scripts de vitrine e checkout com suporte às
imagens. O recurso mostrado precisa funcionar para as lojas que instalarem o app.
Os valores apresentados correspondem ao `application.yml` atual. As propostas de
reajuste para novos assinantes não alteraram esses valores nem criaram uma promessa
de preço vitalício para os primeiros assinantes.

Horários de suporte e SLA não foram inventados. Preencher esses dados no Portal e
na FAQ quando houver uma política operacional definida.

## Referências editoriais

- [Concorrente analisado](https://www.nuvemshop.com.br/loja-aplicativos-nuvem/campo-personalizado):
  galeria, vídeo e descrição organizada por uso, recursos, planos e instalação.
  O texto e a identidade visual deste pacote são próprios.
- [Estrutura recomendada pela Nuvemshop](https://nuvemshop.dev/apps/publish/app-publication/requirements):
  introdução, definição, funcionamento, funcionalidades, vantagens, planos, instalação e suporte.
- [Fotografia operacional informada](../../analises/fotografia-operacional-2026-10-05.md):
  referência interna; histórico de vendas ainda parcial, sem números divulgados na galeria.

## Como regenerar

Exportar os templates em uma base H2 de teste isolada:

```sh
mvn -o -Dskip.frontend=true -Dtest=MarketingScreensExportTest -Dmarketing.export=true test
python3 scripts/build-app-store-gallery.py
```

Capturar cada um dos seis HTMLs numerados em `fontes/` com Chromium em 1600 × 800,
permitindo leitura de arquivos locais e tempo de execução para os campos renderizarem:

```sh
/opt/google/chrome/chrome --headless --no-sandbox --disable-gpu --disable-dev-shm-usage \
  --hide-scrollbars --allow-file-access-from-files --virtual-time-budget=7000 \
  --window-size=1600,800 --screenshot=/caminho/01-personalizacao-na-vitrine.png \
  file:///caminho/fontes/01-personalizacao-na-vitrine.html
```
