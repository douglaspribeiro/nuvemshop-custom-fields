#!/usr/bin/env python3
"""Build editable gallery layouts from exported production screens and real storefront JS.

Export merchant screens first with MarketingScreensExportTest. Capture the resulting
1920x1080 HTML files with Chromium. All demonstration data are fictional.
"""
import base64
import html
import json
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'docs/marketing/loja-aplicativos'
SOURCES = OUTPUT / 'fontes'
ASSETS = SOURCES / 'assets'
ASSETS.mkdir(parents=True, exist_ok=True)
(OUTPUT / 'imagens').mkdir(parents=True, exist_ok=True)
STATIC = ROOT / 'src/main/resources/static'
for path in [STATIC / 'styles/app.css', STATIC / 'styles/merchant.css',
             STATIC / 'assets/nuvemshop-personalizer.js', STATIC / 'assets/local-time.js',
             STATIC / 'assets/option-images-admin.js', STATIC / 'assets/product-fields-editor.js']:
    shutil.copyfile(path, ASSETS / path.name)

for name in ['campos', 'aparencia', 'pedidos']:
    path = SOURCES / 'telas' / (name + '.html')
    text = path.read_text()
    text = re.sub(r'(href|src)="/(?:styles|assets)/([^?"<>]+)(?:\?[^"<>]*)?"', r'\1="../assets/\2"', text)
    text = re.sub(r'<script\b[^>]*src="https?://[^>]+>.*?</script>', '', text, flags=re.S)
    # Do not initialize app integration, analytics or remote bridges in offline captures.
    text = re.sub(r'<script\b[^>]*>[^<]*(?:import\(|window\.Nexo|GoogleAnalytics|gtag\()[\s\S]*?</script>', '', text)
    text = re.sub(r'<script\b[^>]*src="[^"]*nuvemshop-admin-nexo.js[^"]*"[^>]*>.*?</script>', '', text, flags=re.S)
    text = re.sub(r'<script data-gallery-focus>.*?</script>', '', text, flags=re.S)
    offset = {'campos': 240, 'aparencia': 115, 'pedidos': 80}[name]
    setup = "const form=document.querySelector('.preview-source');if(form){form.querySelector('[name=label]').value='Mensagem na embalagem';form.querySelector('[name=placeholder]').value='Ex.: Feito com carinho';form.querySelector('[name=maxLength]').value='80';form.dispatchEvent(new Event('input',{bubbles:true}));}" if name == 'campos' else ''
    text = text.replace('</body>', f'<script data-gallery-focus>setTimeout(()=>{{{setup}window.scrollTo(0,{offset});}},100);</script></body>')
    path.write_text('\n'.join(line.rstrip() for line in text.splitlines()) + '\n')

def notebook(color, accent, pattern):
    decoration = ''.join(f'<circle cx="{x}" cy="{y}" r="13" fill="{accent}" opacity=".65"/>'
                         for x, y in [(120,110),(245,130),(170,190),(250,260),(115,285),(185,350)]) if pattern == 'floral' else (
        f'<path d="M90 100L270 220L90 340Z M280 90L120 230L280 365Z" fill="{accent}" opacity=".45"/>' if pattern == 'geo' else '')
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="360" height="460" viewBox="0 0 360 460">
    <defs><filter id="s"><feDropShadow dx="0" dy="12" stdDeviation="10" flood-opacity=".14"/></filter></defs>
    <rect width="360" height="460" fill="#f4f0e8"/><g filter="url(#s)">
    <rect x="71" y="57" width="238" height="352" rx="12" fill="#ddd7cc"/>
    <rect x="62" y="47" width="238" height="352" rx="12" fill="{color}"/>{decoration}
    <rect x="98" y="203" width="163" height="52" rx="4" fill="#fffdf7"/>
    <text x="179" y="235" text-anchor="middle" font-family="Georgia,serif" font-size="22" fill="#314b44">Meu caderno</text>
    {''.join(f'<path d="M50 {y} q-12 -10 0 -18 h27" fill="none" stroke="#ab8b52" stroke-width="5"/>' for y in range(85,385,26))}
    </g></svg>'''
    return 'data:image/svg+xml;base64,' + base64.b64encode(svg.encode()).decode()

art = [notebook('#dce6d4','#799775','floral'), notebook('#deb7a4','#b28168','plain'), notebook('#cfdfeb','#638e9e','geo')]
for mode in ['texto', 'imagens']:
    fields = [dict(id=1, label='Nome na capa', propertyName='Nome na capa', fieldType='TEXT', required=True,
                   maxLength=30, placeholder='Ex.: Ana Clara')]
    if mode == 'imagens':
        fields += [dict(id=2, label='Escolha sua capa', propertyName='Capa', fieldType='IMAGE_SELECT', required=True,
                        options=['Floral', 'Terracota', 'Geométrica'], imageOptions=[dict(id=str(i), label=label,
                        thumbnailUrl=url, imageUrl=url) for i, (label, url) in enumerate(zip(['Floral','Terracota','Geométrica'],art))])]
    else:
        fields += [dict(id=2,label='Acabamento',propertyName='Acabamento',fieldType='SELECT',required=True,
                        options=['Espiral dourado','Espiral branco','Espiral preto']),
                   dict(id=3,label='Mensagem para presente',fieldType='TEXTAREA',required=False,maxLength=180,
                        placeholder='Escreva uma mensagem especial')]
    config = dict(enabled=True, fields=fields, style=dict(productTextColor='#176b57'))
    page = '''<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><style>
    *{box-sizing:border-box}body{margin:0;background:#fff;color:#283e36;font-family:Arial,sans-serif}
    header{padding:16px 38px;border-bottom:1px solid #e9eee6;display:flex;justify-content:space-between;align-items:center}
    header strong{font-family:Georgia,serif;font-size:28px}header span{font-size:14px;color:#6b7d72}
    main{padding:22px 36px;display:grid;grid-template-columns:340px 1fr;gap:38px}.photo{background:#f4f0e8;border-radius:12px;overflow:hidden}
    .photo img{width:100%;height:440px;object-fit:contain}.breadcrumb{font-size:12px;color:#809086;margin-bottom:16px}
    h1{font-size:30px;line-height:1.15;margin:0 0 14px}p{font-size:15px;color:#69796e;line-height:1.6;margin:0 0 12px}
    .price{font-size:25px;font-weight:bold;color:#2c463b;margin-bottom:14px}
    input,select,textarea{border:1px solid #d4ddd4;border-radius:6px;padding:11px;font:16px Arial;background:#fff;color:#31473b;width:100%}
    textarea{height:66px;resize:none}button[type=submit]{background:#176b57;color:white;border:0;width:100%;font-weight:bold;font-size:17px;padding:16px;border-radius:6px;margin-top:8px}
    .ncf-field{margin-bottom:14px}.ncf-label{font-weight:600;font-size:15px}a{font-size:12px;color:#577b68}
    </style></head><body><header><strong>Ateliê Aurora</strong><span>Loja demonstrativa · Papelaria personalizada</span></header>
    <main><div><div class="photo"><img src="ART" alt="Caderno ilustrativo"></div><p style="margin-top:12px;text-align:center">Um presente pensado para cada pessoa.</p></div>
    <div><div class="breadcrumb">Início / Papelaria / Cadernos</div><h1>Caderno personalizado</h1><p>Escolha os detalhes e deixe seu caderno único.</p><div class="price">R$ 49,90</div>
    <form action="/cart/add" data-product-id="7001"><input type="hidden" name="product_id" value="7001"><button type="submit">Adicionar ao carrinho</button></form></div></main>
    <script>window.LS={storeId:990077001,theme:{code:'morelia'}};const config=CONFIG;
    window.fetch=async (url)=>({ok:true,json:async()=>String(url).includes('/personalization')?config:config.style});
    </script><script src="assets/nuvemshop-personalizer.js?store=990077001"></script>
    <script>setTimeout(()=>{for(const input of document.querySelectorAll('.ncf-field input'))input.value='Ana Clara';
    const text=document.querySelector('.ncf-field textarea');if(text)text.value='Que suas ideias ganhem vida!';
    const select=document.querySelector('.ncf-field select');if(select){select.value=select.options[1]?.value||'';select.dispatchEvent(new Event('change',{bubbles:true}));}},300);</script>
    </body></html>'''.replace('ART', art[0]).replace('CONFIG', json.dumps(config, ensure_ascii=False))
    (SOURCES / (mode + '.html')).write_text(page)

order_item = dict(name='Caderno personalizado', quantity=1, price='49.90', properties=[
    dict(name='Nome na capa', value='Ana Clara'),
    dict(name='Capa', value='Floral'),
    dict(name='Acabamento', value='Espiral dourado'),
    dict(name='Mensagem para presente', value='Que suas ideias ganhem vida!')
])
# Illustrate the saved item properties without imitating or claiming a real Nuvemshop admin capture.
(SOURCES / 'pedido-concluido.html').write_text(f'''<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><style>
*{{box-sizing:border-box}}body{{margin:0;background:#f4f7f3;color:#263f34;font-family:Arial,sans-serif;padding:32px 40px}}
.eyebrow{{font-size:13px;letter-spacing:1.8px;text-transform:uppercase;color:#738678}}h1{{font-size:32px;margin:12px 0 6px}}
.intro{{font-size:16px;color:#6b7f70;margin:0 0 25px}}.card{{background:white;border:1px solid #dbe5d8;border-radius:14px;padding:26px}}
.head{{display:flex;align-items:center;justify-content:space-between;margin-bottom:20px;font-size:17px;font-weight:bold}}
.status{{font-size:13px;font-weight:normal;color:#47714b;background:#edf4e9;padding:8px 12px;border-radius:30px}}
.item{{display:grid;grid-template-columns:145px 1fr;gap:25px}}img{{width:145px;height:180px;object-fit:cover;border-radius:9px}}
h2{{font-size:25px;margin:1px 0 8px}}.meta{{font-size:15px;color:#738277;margin-bottom:20px}}h3{{font-size:17px;margin:0 0 15px}}
.properties{{display:grid;grid-template-columns:1fr 1fr;gap:12px}}.property{{background:#f0f5ed;border:1px solid #dfe9da;border-radius:9px;padding:14px 16px}}
.property span{{display:block;font-size:13px;color:#6d836d;margin-bottom:7px}}.property strong{{font-size:18px;line-height:1.3;color:#274d34}}
.note{{font-size:15px;color:#587159;margin:22px 0 0;border-top:1px solid #e8eee4;padding-top:18px}}
.disclaimer{{font-size:12px;color:#819080;text-align:center;margin-top:20px}}
</style></head><body><div class="eyebrow">Exemplo de compra finalizada</div><h1>Pedido #1042</h1>
<p class="intro">Os detalhes escolhidos pelo cliente ficam associados ao produto comprado.</p>
<section class="card"><div class="head">Item vendido <span class="status">Personalização registrada</span></div>
<div class="item"><img src="{art[0]}" alt="Produto ilustrativo"><div><h2>{html.escape(order_item['name'])}</h2>
<div class="meta">Quantidade: 1 &nbsp; · &nbsp; R$ 49,90</div><h3>Detalhes da personalização</h3><div class="properties">
{''.join(f'<div class="property"><span>{html.escape(p["name"])}</span><strong>{html.escape(p["value"])}</strong></div>' for p in order_item['properties'])}
</div></div></div><p class="note">Confira nomes, mensagens e opções ao consultar o item da venda.</p></section>
<p class="disclaimer">Representação ilustrativa de um item do pedido. Não é uma captura do painel da Nuvemshop.</p>
</body></html>''')
(SOURCES / 'pedido-concluido-dados.json').write_text(json.dumps(order_item, ensure_ascii=False, indent=2) + '\n')

slides = [
    ('01-personalizacao-na-vitrine','01 / PERSONALIZAÇÃO','Cada pedido,\ndo jeito do cliente.',
     'Receba nomes, mensagens e escolhas antes de adicionar ao carrinho.',
     ['Texto, número e seleção','Campos obrigatórios e limites','Informações junto ao pedido'],'texto.html','Vitrine demonstrativa · Campos renderizados pelo aplicativo'),
    ('02-opcoes-com-imagens','02 / ESCOLHA VISUAL','Mostre as opções.\nFacilite a escolha.',
     'Ofereça capas, estampas e modelos com imagens cadastradas por você.',
     ['Miniaturas para comparar','Ampliação da imagem','Disponibilidade conforme o plano'],'imagens.html','Vitrine demonstrativa · Seleção de imagens já disponível'),
    ('03-configuracao-dos-campos','03 / CONFIGURAÇÃO','Você define\nos detalhes.',
     'Configure os campos de cada produto em um painel organizado.',
     ['Nome, tipo e obrigatoriedade','Mensagens de orientação','Ordem e limite de caracteres'],'telas/campos.html','Tela do aplicativo · Loja e dados demonstrativos'),
    ('04-aparencia','04 / APARÊNCIA','Personalização\ncom a sua identidade.',
     'Ajuste as cores dos textos nos pontos de compra da sua loja.',
     ['Produto, carrinho e checkout','Cores automáticas ou próprias','Prévia visual no painel'],'telas/aparencia.html','Tela do aplicativo · Loja e dados demonstrativos'),
    ('05-pedidos-personalizados','05 / OPERAÇÃO','Os detalhes\nacompanham o pedido.',
     'Consulte pedidos recentes e as informações de personalização.',
     ['Nomes e escolhas organizados','Consulta pelo painel do app','Mais clareza para preparar'],'telas/pedidos.html','Tela do aplicativo · Pedidos fictícios para demonstração'),
    ('06-personalizacao-no-item-vendido','06 / PEDIDO CONCLUÍDO','Cada detalhe,\nsalvo no pedido.',
     'Depois da compra, confira a personalização no item vendido e prepare tudo como seu cliente escolheu.',
     ['Detalhes no próprio item','Consulta à venda na Nuvemshop','Clareza na hora de preparar'],'pedido-concluido.html',
     'Representação ilustrativa · Não é captura do painel da Nuvemshop')
]
css = '''*{box-sizing:border-box}html,body{margin:0;width:1920px;height:1080px;overflow:hidden}body{font-family:Arial,sans-serif;background:#eef2e8;color:#173b2e}
    .canvas{position:relative;width:1600px;height:900px;transform:scale(1.2);transform-origin:top left;background:radial-gradient(ellipse at 95% 10%,#d6e5ce 0,transparent 55%),#f4f6ef}
    .brand{position:absolute;left:62px;top:46px;display:flex;align-items:center;gap:13px;font-size:23px;font-weight:700;letter-spacing:-.6px}
    .mark{height:44px;width:44px;display:grid;place-items:center;background:#176b57;color:#fff;border-radius:12px;font-size:26px}
    .overline{color:#537c60;letter-spacing:2.5px;font-size:13px;font-weight:bold;margin-bottom:23px}
    .copy{position:absolute;left:62px;top:210px;width:430px}h1{font-size:51px;line-height:1.07;letter-spacing:-2px;font-weight:700;margin:0 0 27px}
    .lead{font-size:22px;line-height:1.45;color:#5a7160;margin:0 0 30px}.point{display:flex;align-items:center;gap:12px;font-size:17px;color:#31523f;margin:15px 0}
    .check{height:23px;width:23px;border-radius:50%;background:#dce8d6;display:grid;place-items:center;color:#176b57;font-size:14px;font-weight:bold}
    .screen{position:absolute;left:537px;top:170px;width:1010px;height:650px;border:1px solid #cfdbc9;border-radius:18px;background:#fff;overflow:hidden;box-shadow:0 25px 65px #233c2920}
    .browser{height:37px;padding:12px 16px;border-bottom:1px solid #e0e7db;background:#fafbf8;display:flex;gap:6px;align-items:center}.browser i{width:8px;height:8px;border-radius:50%;background:#cdd7c6}.browser span{font-size:12px;margin:auto;color:#879880;padding-right:40px}
    iframe{border:0;width:1180px;height:716px;transform:scale(.856);transform-origin:top left;background:white;pointer-events:none}
    .footer{position:absolute;left:62px;bottom:39px;font-size:14px;color:#6c806c}.caption{position:absolute;right:54px;bottom:34px;font-size:12px;color:#849381}
    .pill{position:absolute;right:54px;top:49px;border:1px solid #bfd0b6;padding:10px 15px;border-radius:30px;color:#456541;font-size:13px}
    '''
for slug, eyebrow, title, lead, points, screen, caption in slides:
    destination = SOURCES
    if slug.startswith('04-'):
        destination = SOURCES / 'alternativas'
        destination.mkdir(exist_ok=True)
        screen = '../' + screen
    page = f'''<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><title>{slug}</title><style>{css}</style></head>
    <body><div class="canvas"><div class="brand"><span class="mark">C</span>Campos Personalizados</div><div class="pill">Para lojas Nuvemshop</div>
    <div class="copy"><div class="overline">{eyebrow}</div><h1>{html.escape(title).replace(chr(10),'<br>')}</h1><p class="lead">{lead}</p>
    {''.join(f'<div class="point"><span class="check">✓</span>{p}</div>' for p in points)}</div>
    <div class="screen"><div class="browser"><i></i><i></i><i></i><span>{'Sua loja · Exemplo de uso' if screen in ['texto.html','imagens.html'] else 'Item do pedido · Exemplo ilustrativo' if screen == 'pedido-concluido.html' else 'Campos Personalizados · Painel da loja'}</span></div><iframe src="{screen}"></iframe></div>
    <div class="footer">Comece com o plano gratuito.</div><div class="caption">{caption}</div></div></body></html>'''
    (destination / (slug + '.html')).write_text(page)

print('5 layouts principais e 1 alternativa gerados em', SOURCES)
