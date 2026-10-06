# Material para a loja de aplicativos Nuvemshop / Tiendanube

Pacote preparado em 05/10/2026. O conteúdo não foi publicado no Partner Portal.
O vídeo será produzido pelo responsável usando o sistema.

## Arquivos para publicar

- `descricao-pt-BR.md`: descrição curta e longa para o Brasil.
- `descricao-completa.txt` e `descricao-curta.txt`: versões brasileiras em texto simples.
- `descricao-es-AR.md`: Argentina, em espanhol, com preços em ARS.
- `descricao-es-MX.md`: México, em espanhol, com preços em MXN.
- `descricao-es-CL.md`: Chile, em espanhol, com preços em CLP.
- `descricao-es-CO.md`: Colômbia, em espanhol, com preços em COP.
- `descricao-en-US.md`: versão em inglês, com preços em USD para demais países.
- `descricao-completa-{idioma}.txt` e `descricao-curta-{idioma}.txt`: textos separados para cada uma dessas versões.
- `imagens/01-personalizacao-na-vitrine.png`: texto, mensagens e escolhas na página do produto.
- `imagens/02-opcoes-com-imagens.png`: seleção de capas e modelos cadastrados pelo lojista.
- `imagens/03-configuracao-dos-campos.png`: editor de campos por produto.
- `imagens/05-pedidos-personalizados.png`: consulta às informações dos pedidos recentes.
- `imagens/06-personalizacao-no-item-vendido.png`: representação ilustrativa dos detalhes registrados no item após a compra.

O pacote principal contém **cinco imagens: 01, 02, 03, 05 e 06**, respeitando o
limite do Portal. A numeração preserva a identificação dos arquivos.

A imagem `imagens/alternativas/04-aparencia.png` foi retirada do pacote principal
e permanece separada como alternativa. Ela pode substituir uma das
cinco imagens principais.

As cinco imagens principais e a alternativa são PNG de **1920 × 1080 pixels**, cada uma com até **5 MB**,
conforme as especificações informadas para o campo do Portal. Os formatos aceitos
são **JPEG, PNG ou WebP**. O pacote usa PNG.

As descrições estão localizadas por país, com nomes e valores dos planos. As imagens
continuam em português; para publicar em outros países, adaptar também os textos
das imagens. Usar a descrição e a moeda correspondentes ao mercado.

As descrições completas têm no máximo **2.000 caracteres**, contando espaços e
quebras de linha. Os arquivos `.txt` estão prontos para copiar no Portal; os `.md`
incluem também a descrição curta e permanecem dentro do mesmo limite.

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

As descrições apresentam os recursos disponíveis, incluindo a seleção de imagens
cadastradas pelo lojista. A funcionalidade de envio de arquivos pelo comprador
não faz parte deste material promocional.

## Antes de atualizar o perfil

Confirmar no ambiente publicado os scripts de vitrine e checkout com suporte às
imagens. O recurso mostrado precisa funcionar para as lojas que instalarem o app.
Os valores apresentados correspondem ao `application.yml` atual, na seção
`nuvemshop.billing.prices` (revisão editorial em 06/10/2026). A página pública não
pôde ser consultada nesta revisão; confirmar os valores publicados antes de enviar
as descrições ao Portal. As propostas de
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

Capturar os cinco HTMLs numerados na raiz de `fontes/` com Chromium em 1920 × 1080,
permitindo leitura de arquivos locais e tempo de execução para os campos renderizarem:

```sh
/opt/google/chrome/chrome --headless --no-sandbox --disable-gpu --disable-dev-shm-usage \
  --hide-scrollbars --allow-file-access-from-files --virtual-time-budget=7000 \
  --window-size=1920,1080 --screenshot=/caminho/01-personalizacao-na-vitrine.png \
  file:///caminho/fontes/01-personalizacao-na-vitrine.html
```
