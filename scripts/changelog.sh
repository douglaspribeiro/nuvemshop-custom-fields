#!/usr/bin/env bash

# Gera/atualiza a secao de uma versao no CHANGELOG.md a partir dos commits do range.
# Usado por release-version.sh e tambem sozinho para refazer versoes antigas.

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHANGELOG="${CHANGELOG_FILE:-${PROJECT_ROOT}/CHANGELOG.md}"

VERSION=""
FROM_REF=""
TO_REF="HEAD"
DATE=""
PRINT_ONLY="false"

usage() {
  cat <<'USAGE'
Uso:
  ./scripts/changelog.sh --version X.Y.Z [--from <ref>] [--to <ref>] [opcoes]

Agrupa os commits do range por tipo (Conventional Commits) e escreve a secao da
versao no topo do CHANGELOG.md. Rodar de novo com a mesma versao substitui a
secao existente, entao e seguro repetir.

Opcoes:
  --version X.Y.Z   Versao da secao (obrigatorio)
  --from <ref>      Inicio exclusivo do range; vazio = desde o primeiro commit
  --to <ref>        Fim do range (default: HEAD)
  --date YYYY-MM-DD Data da secao (default: data do commit --to)
  --print-only      Imprime a secao e nao toca no arquivo
  -h, --help        Esta ajuda
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version) VERSION="${2:?--version exige X.Y.Z}"; shift 2 ;;
    --from) FROM_REF="${2-}"; shift 2 ;;
    --to) TO_REF="${2:?--to exige um ref}"; shift 2 ;;
    --date) DATE="${2:?--date exige YYYY-MM-DD}"; shift 2 ;;
    --print-only) PRINT_ONLY="true"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Opcao invalida: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ -z "$VERSION" ]]; then
  echo "--version e obrigatorio" >&2
  usage >&2
  exit 2
fi

cd "$PROJECT_ROOT"

RANGE="${FROM_REF:+${FROM_REF}..}${TO_REF}"
[[ -z "$DATE" ]] && DATE="$(git log -1 --date=short --format='%ad' "$TO_REF")"

# %x1f separa campos, %x1e separa commits: assunto e corpo podem ter qualquer coisa.
LOG="$(git log --no-merges --format='%h%x1f%s%x1f%b%x1e' "$RANGE")"

CHANGELOG="$CHANGELOG" VERSION="$VERSION" DATE="$DATE" RANGE="$RANGE" \
PRINT_ONLY="$PRINT_ONLY" LOG="$LOG" python3 <<'PY'
import os, re, sys

path = os.environ['CHANGELOG']
version, date, rng = os.environ['VERSION'], os.environ['DATE'], os.environ['RANGE']
print_only = os.environ['PRINT_ONLY'] == 'true'

HEADER = ('# Changelog\n\n'
          'Gerado por `scripts/changelog.sh` a partir das mensagens de commit.\n'
          'A versão vem do `pom.xml` e é a mesma da tag git e da imagem Docker.\n')

# Ordem das secoes = ordem de exibicao. "add" e o prefixo legado do projeto para feat.
GROUPS = [
    ('feat', 'Features'), ('add', 'Features'),
    ('fix', 'Correções'),
    ('perf', 'Performance'),
    ('refactor', 'Refatoração'),
    ('docs', 'Documentação'),
    ('test', 'Testes'), ('tests', 'Testes'),
    ('style', 'Manutenção'), ('chore', 'Manutenção'),
    ('build', 'Manutenção'), ('ci', 'Manutenção'), ('deps', 'Manutenção'),
]
LABELS = {}
for prefix, label in GROUPS:
    LABELS[prefix] = label
ORDER = []
for _, label in GROUPS:
    if label not in ORDER:
        ORDER.append(label)
ORDER.append('Outros')

SUBJECT_RE = re.compile(r'^([a-zA-Z]+)(\(([^)]*)\))?(!)?:\s*(.+)$', re.DOTALL)

sections, breaking, seen = {}, [], set()

for raw in os.environ['LOG'].split('\x1e'):
    raw = raw.strip('\n')
    if not raw.strip():
        continue
    parts = raw.split('\x1f')
    if len(parts) < 2:
        continue
    sha, subject, body = parts[0].strip(), parts[1].strip(), (parts[2] if len(parts) > 2 else '')

    m = SUBJECT_RE.match(subject)
    prefix = m.group(1).lower() if m else ''
    scope = (m.group(3) or '').strip() if m else ''
    text = m.group(5).strip() if m else subject

    # O proprio commit de release nao descreve mudanca de produto.
    if prefix == 'chore' and scope == 'release':
        continue

    is_breaking = bool(m and m.group(4)) or re.search(r'^BREAKING[ -]CHANGE', body, re.M | re.I) is not None
    label = LABELS.get(prefix, 'Outros') if m else 'Outros'
    entry = f"{scope}: {text}" if scope and scope != 'release' else text

    # Mesma mensagem repetida em varios commits (WIP) vira uma linha so.
    key = (label, entry.lower())
    if key in seen:
        continue
    seen.add(key)

    line = f"- {entry} (`{sha}`)"
    if is_breaking:
        breaking.append(line)
    sections.setdefault(label, []).append(line)

body_lines = [f"## [{version}] - {date}", '']
if breaking:
    body_lines += ['### BREAKING CHANGES', ''] + breaking + ['']
for label in ORDER:
    if sections.get(label):
        body_lines += [f"### {label}", ''] + sections[label] + ['']
if not breaking and not sections:
    body_lines += ['_Sem commits com mudança de produto neste range._', '']

section = '\n'.join(body_lines).rstrip() + '\n'

if print_only:
    sys.stdout.write(section)
    raise SystemExit(0)

existing = ''
if os.path.exists(path):
    with open(path, encoding='utf-8') as fh:
        existing = fh.read()

if existing.startswith('# Changelog'):
    rest = existing.split('\n', 1)[1] if '\n' in existing else ''
    # Tudo antes da primeira "## [" e preambulo e fica preservado.
    idx = rest.find('\n## [')
    if rest.lstrip('\n').startswith('## ['):
        preamble, tail = '# Changelog\n', rest.lstrip('\n')
    elif idx >= 0:
        preamble, tail = '# Changelog\n' + rest[:idx + 1], rest[idx + 1:]
    else:
        preamble, tail = existing.rstrip('\n') + '\n', ''
else:
    preamble, tail = HEADER, existing.strip('\n')
    if tail:
        tail += '\n'

# Reescrever a mesma versao em vez de duplicar a secao.
tail = re.sub(r'(?ms)^## \[' + re.escape(version) + r'\][^\n]*\n.*?(?=^## \[|\Z)', '', tail)

out = preamble.rstrip('\n') + '\n\n' + section + ('\n' + tail.lstrip('\n') if tail.strip() else '')
with open(path, 'w', encoding='utf-8') as fh:
    fh.write(out.rstrip('\n') + '\n')

print(f"CHANGELOG.md: secao [{version}] escrita ({rng})", file=sys.stderr)
PY
