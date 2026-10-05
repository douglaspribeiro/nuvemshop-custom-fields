#!/usr/bin/env python3
"""Cross a manually supplied Nuvemshop list with reachable Git commits; never infer deploys."""
import csv
import subprocess
from collections import Counter, defaultdict
from datetime import date, datetime, time, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'docs/analises/desinstalacoes-nuvemshop-2026-10-05.csv'
OUTPUT = SOURCE.with_name('desinstalacoes-cruzadas-com-commits-2026-10-05.csv')
REPORT = SOURCE.with_name('desinstalacoes-e-evolucao-do-sistema-2026-10-05.md')
ZONE = ZoneInfo('America/Sao_Paulo')
LABELS = {'uninstallReasons.i-am-testing-the-application': 'Estava testando o aplicativo'}

def escaped(value):
    return str(value).replace('|', '\\|').replace('\n', ' ')

def period(day):
    if day < date(2026, 9, 23):
        return 'Até 22/09: experiência anterior aos novos gateways e à revisão visual'
    if day < date(2026, 9, 25):
        return '23–24/09: introdução de Mercado Pago/Efí e correções de checkout'
    if day < date(2026, 9, 29):
        return '25–28/09: revisão visual, primeiro uso, Paddle e Patagonia'
    if day < date(2026, 10, 2):
        return '29/09–01/10: métricas, vendas personalizadas e histórico de saídas'
    return '02–05/10: Ultra, cupons, revisão dos planos e GA4'

rows = list(csv.DictReader(SOURCE.open(encoding='utf-8'), delimiter=';'))
groups = defaultdict(list)
for row in rows:
    groups[(row['loja'].strip().casefold(), row['pais'], row['data'])].append(row)
commits = []
log = subprocess.check_output(['git', 'log', 'HEAD', '--format=%h%x09%ct%x09%s'], cwd=ROOT, text=True)
for line in log.splitlines():
    sha, stamp, subject = line.split('\t', 2)
    commits.append((datetime.fromtimestamp(int(stamp), ZONE), sha, subject))
commits.sort()
normalized = []
for grouped in groups.values():
    first = grouped[0]
    day = date.fromisoformat(first['data'])
    start = datetime.combine(day, time.min, ZONE)
    end = start + timedelta(days=1)
    preceding = [c for c in commits if c[0] < start]
    previous = preceding[-1] if preceding else None
    on_day = [c for c in commits if start <= c[0] < end
              and not c[2].startswith(('chore(release)', 'Merge '))]
    reasons = sorted({LABELS.get(r['motivo'], r['motivo']) for r in grouped if r['motivo']})
    normalized.append({
        'loja': first['loja'], 'pais': first['pais'], 'data': first['data'],
        'motivos': ' / '.join(reasons),
        'justificativas': ' / '.join(sorted({r['justificativa'] for r in grouped if r['justificativa']})),
        'linhas_originais': len(grouped), 'periodo': period(day),
        'ultimo_commit_antes_do_dia': previous[1] if previous else '',
        'data_commit_anterior': previous[0].isoformat() if previous else '',
        'assunto_commit_anterior': previous[2] if previous else '',
        'commits_no_mesmo_dia': ' / '.join(f'{c[1]} {c[0]:%H:%M} {c[2]}' for c in on_day),
    })
normalized.sort(key=lambda r: (r['data'], r['loja'].casefold()))
with OUTPUT.open('w', encoding='utf-8', newline='') as handle:
    writer = csv.DictWriter(handle, fieldnames=list(normalized[0]), delimiter=';', lineterminator='\n')
    writer.writeheader()
    writer.writerows(normalized)
reason_counts = Counter()
for grouped in groups.values():
    reason_counts.update({LABELS.get(r['motivo'], r['motivo']) for r in grouped if r['motivo']})
period_counts = Counter(r['periodo'] for r in normalized)
country_counts = Counter(r['pais'] for r in normalized)
lines = [
    '# Desinstalações e evolução do sistema — lista recebida em 05/10/2026', '',
    '## Método e limites', '',
    f'- Lista fornecida pelo proprietário: **{len(rows)} linhas**, **{len(normalized)} episódios de saída** e **{len({(r["loja"].casefold(), r["pais"]) for r in normalized})} lojas distintas por nome e país**.',
    '- Um episódio agrupa nome + país + dia. Sem Store ID e horário, isso é uma aproximação: duas saídas reais da mesma loja no mesmo dia podem ter sido agrupadas.',
    '- Duplicatas idênticas e linhas sem motivo não adicionam uma saída quando já existe outra linha da mesma loja no mesmo dia. Motivos diferentes são preservados.',
    '- MSE_sports aparece em 23 e 30/09; Lavie Lite em 02 e 03/10. São episódios distintos, sem assumir que houve reinstalação sem confirmar os eventos.',
    '- Datas de commits são as de gravação no Git, convertidas para America/Sao_Paulo (UTC−03). O horário da desinstalação não foi informado.',
    '- Para cada episódio, o CSV mostra o último commit anterior ao início do dia e os commits feitos naquele dia. Nenhum deles prova qual versão estava publicada.',
    '- A workflow deploy.yml publica imagens em pushes para deploy-*. Commit em main, tag ou imagem publicada não comprova quando o container de produção foi atualizado. Há ainda versões repetidas no histórico; usar SHA junto à versão.',
    '- Não se calcula taxa de desinstalação por período: faltam instalações e exposição em cada período. Redução/aumento na quantidade de saídas não comprova efeito das melhorias.',
    '- A lista não equivale às 53 desinstalações citadas no painel: são 45 episódios aproximados, não 55 lojas nem necessariamente o histórico completo.', '',
    '## Motivos declarados', '', '| Motivo | Episódios com esse motivo | % dos 45 episódios |', '|---|---:|---:|',
]
for reason, count in reason_counts.most_common():
    lines.append(f'| {escaped(reason)} | {count} | {count / len(normalized) * 100:.1f}% |')
lines += ['', 'Há 47 associações de motivo para 45 episódios: Emerc4dor Artesanatos (24/09) e Manillas de identificación A tu ladoo (29/09) têm duas respostas diferentes. Percentuais não somam 100%. Nenhum episódio ficou inteiramente sem motivo após agrupar as linhas.', '',
          '## Distribuição por fase do código', '', '| Fase | Episódios |', '|---|---:|']
for label in sorted(period_counts, key=lambda p: min(r['data'] for r in normalized if r['periodo'] == p)):
    lines.append(f'| {label} | {period_counts[label]} |')
lines += ['', 'Por país: ' + '; '.join(f'{country}: {count}' for country, count in country_counts.most_common()) + '.', '',
          '## Marcos verificados no código', '',
          '| Data e horário (São Paulo) | Commit | Mudança e interpretação |', '|---|---|---|',
          '| 01/09 23:59 | `66a8a8d` | Documentação de homologação. A comparação anterior tinha Free, Essencial e Pro; Ultra não existia. Havia billing nativo configurável, sem afirmar sua ativação no servidor. |',
          '| 23/09 10:40–11:21 | `b419d8e`, `b09ace0`, `2790383` | Ajustes do botão de upgrade e cortesia temporária. Mercado Pago entrou às 21:31 (`437bb19`), seguido de correções de renderização/e-mail/checkout até 23:12. |',
          '| 24/09 23:00–25/09 02:35 | `47fffa5`, `811980a`, `880c16f`, `7c9662f`, `6134b83` | Entrada da Efí e várias correções de webhook/conciliação de pagamento. Um relato em 24/09 pode anteceder a Efí, adicionada tarde no dia. |',
          '| 25/09 00:30–01:30 | `448beb7`, `78fbef9`, `7c9662f` | Revisão visual, versionamento de CSS e orientação de primeiro uso. Não atribuir as saídas de 13/09 e 18/09 a esta interface posterior. |',
          '| 25/09 10:13–17:18 | `d912b72`, `8173675`, `d6b48bb`, `e270cfc` | Notificações de pagamento, expiração de tentativas pendentes, site institucional/visão operacional e canal de suporte. |',
          '| 26/09 02:18–27/09 23:27 | `3401a2a`, `bda70e5`, `460a175`, `0fe6a80` | Paddle e ajustes de catálogo, retentativa e mensagens. Relatos em 26/09 e 27/09 coincidem com essa evolução, sem provar problema de gateway. |',
          '| 28/09 21:11–21:15 | `a4a6968`, `83cf5ce` | Adaptador para Patagonia. A investigação posterior `1e6d335` descreve uma loja sem entrega do script legado pela plataforma. Tema das lojas desta lista não foi confirmado. |',
          '| 29/09 00:51–11:56 | `28a475c`, `09807fc`, `c67cb48`, `7892efa`, `c70b5f7` | Métricas de vitrine, lista de desinstaladas, priorização de produtos configurados e medição de vendas personalizadas. Visibilidade interna maior não é correção automática da experiência. |',
          '| 01/10 00:19 e 21:28–21:36 | `add0e0f`, `8526960`, `1e6d294` | Histórico de saídas/reconquista e correções de migrações/esquema. O relato de instabilidade de Kubik nesse dia merece confronto com horários de deploy/logs; o motivo não comprova que a loja foi afetada por uma migração. |',
          '| 02/10 21:23–23:12 | `9e90ffb`, `180c54b`, `b4a6261` | Ultra, fluxo de upgrade, cupons e notificações. Saídas de 02/10 sem horário não comprovam contato com essas funcionalidades. |',
          '| 03/10 13:02–14:18 | `cb6a7f5`, `d2007fb`, `7271d9c` | Catálogo versionado, fuso do navegador e nova comparação dos planos. Lavie Lite saiu nesse dia, mas não sabemos se antes ou depois das mudanças. |',
          '| 05/10 10:04 | `5d443ef` | Instrumentação de funil GA4. Precisa de configuração e publicação; não recupera a navegação anterior. |', '',
          '## Conclusões acionáveis', '',
          '1. **Preço não é o motivo mais frequente declarado:** 3 de 45 episódios (6,7%), contra 8 de falta de funcionalidades e 4 de dificuldade de configuração. A categoria Outros (14) é vaga; não reinterpretar esses casos como preço ou bug.',
          '2. **Não atribuir as queixas antigas ao Ultra:** La Nuestra (14/09) e Nexora 3d Studio (19/09) antecedem o commit que introduz o Ultra em 02/10. Festarola (05/10) é posterior ao commit, mas não informa qual plano ou preço foi visto.',
          '3. **Configuração merece revisão de experiência:** Ateliê Algodão Doce (13/09) e Loja Debuteen (18/09) antecedem a orientação/revisão visual de 25/09. Manillas (29/09) e Lavie Lite (03/10) são posteriores ao commit; isso justifica investigar seus fluxos, sem afirmar que usaram a versão nova.',
          '4. **Separar funcionalidades ausentes de funcionalidades difíceis de descobrir:** Papel e Propósito (16/09) pediu escolha de estampa/capa. SELECT já existia no código de 20/08, mas escolha visual por imagens não aparece entre os tipos de campo. A justificativa não diz se opções textuais resolveriam sua necessidade.',
          '5. **Upload de imagem é uma lacuna concreta a avaliar:** Láser Mataderos (01/10) pediu envio de imagem pelo comprador. FieldType tem TEXT, NUMBER, SELECT e TEXTAREA, sem upload; as mudanças posteriores examinadas não adicionaram esse tipo. Registrar como oportunidade, sem compromisso de implementação.',
          '6. **Instabilidade precisa de evidência técnica:** María Candela Joyas (23/09) e Kubik (01/10) coincidem com dias de mudanças relevantes. Faltam hora da saída, versão publicada, erros da loja e tema para confirmar a causa.',
          '7. **Teste não equivale a rejeição definitiva:** 11 episódios declaram que estavam testando. Verificar ativação/configuração antes da saída e expectativa do lojista; não excluir esses episódios do denominador apenas para elevar a conversão.', '',
          '## Episódios e contexto temporal', '',
          '| Data | Loja | País | Motivo(s) | Último commit antes do dia | Commits no dia (ordem temporal desconhecida em relação à saída) |',
          '|---|---|---|---|---|---|']
for row in normalized:
    lines.append('| ' + ' | '.join(escaped(row[key]) for key in ('data','loja','pais','motivos','ultimo_commit_antes_do_dia','commits_no_mesmo_dia')) + ' |')
lines += ['', '## Próxima validação', '',
          'Confirmar Store ID, horário de saída e primeira instalação por loja; associar os motivos aos cadastros corretos. Consultar histórico de deploy/container e logs, com atenção a 23/09, 01/10 e à saída de Lavie Lite em 03/10. A análise não alterou cadastros no banco nem preencheu motivos automaticamente por nome.', '',
          'O relatório `/backoffice/reports/adoption` complementa esta análise com configuração salva, vendas personalizadas sincronizadas, situação de assinatura e motivos já cadastrados. Dados de saída apagados permanecem apenas nos totais agregados; o aplicativo não restaura cadastros a partir deste CSV.', '',
          'Artefatos: lista original em `desinstalacoes-nuvemshop-2026-10-05.csv`; agrupamento e cruzamento em `desinstalacoes-cruzadas-com-commits-2026-10-05.csv`. Regeneração: `python3 scripts/analyze-uninstall-history.py`.']
REPORT.write_text('\n'.join(lines) + '\n', encoding='utf-8')
print(f'{len(rows)} linhas → {len(normalized)} episódios; relatório: {REPORT.relative_to(ROOT)}')
print(dict(period_counts))
