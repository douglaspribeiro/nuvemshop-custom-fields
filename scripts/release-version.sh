#!/usr/bin/env bash

# Sobe a versao do pom a partir das mensagens de commit (estilo semantic-release).
# Imprime a nova versao na ultima linha do stdout para quem chama consumir.

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POM="${PROJECT_ROOT}/pom.xml"

FORCE_LEVEL=""
SET_VERSION=""
DRY_RUN="false"
DO_COMMIT="true"
DO_PUSH="true"
ALLOW_DIRTY="false"
DO_CHANGELOG="true"

usage() {
  cat <<'USAGE'
Uso:
  ./scripts/release-version.sh [opcoes]

Le os commits desde a ultima tag de versao e sobe o <version> do pom.xml.

Regras (Conventional Commits, com os prefixos usados no projeto):
  BREAKING CHANGE no corpo, ou tipo com "!"   -> major   (2.1.6 -> 3.0.0)
  feat: | add:                                -> minor   (2.1.6 -> 2.2.0)
  outro prefixo (fix:, docs:, chore:, ...)    -> patch   (2.1.6 -> 2.1.7)
  sem prefixo ("teste message")               -> build   (2.1.6 -> 2.1.6.1)

Vence o maior nivel presente no range; merges nao contam. O 4o digito e omitido
quando zera, entao 2.1.6.3 + um commit "feat:" vira 2.2.0.

Opcoes:
  --release major|minor|patch|build  Ignora os commits e aplica este nivel
  --set X.Y.Z                  Define a versao exata
  --dry-run                    So mostra o que faria
  --no-commit                  Altera o pom mas nao commita nem cria tag
  --no-push                    Commita mas nao envia o branch para o remoto
  --allow-dirty                Nao aborta com working tree suja
  --no-changelog               Nao atualiza o CHANGELOG.md
  -h, --help                   Esta ajuda
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --release) FORCE_LEVEL="${2:?--release exige major|minor|patch|build}"; shift 2 ;;
    --set) SET_VERSION="${2:?--set exige X.Y.Z[.B]}"; shift 2 ;;
    --dry-run) DRY_RUN="true"; shift ;;
    --no-commit) DO_COMMIT="false"; shift ;;
    --no-push) DO_PUSH="false"; shift ;;
    --allow-dirty) ALLOW_DIRTY="true"; shift ;;
    --no-changelog) DO_CHANGELOG="false"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Opcao invalida: $1" >&2; usage >&2; exit 2 ;;
  esac
done

cd "$PROJECT_ROOT"

# Versao do projeto e a primeira <version> depois de </parent>: a de cima e a do Spring Boot.
current_version() {
  python3 - "$POM" <<'PY'
import re, sys
pom = open(sys.argv[1], encoding='utf-8').read()
tail = pom.split('</parent>', 1)[1]
print(re.search(r'<version>([^<]+)</version>', tail).group(1))
PY
}

CURRENT="$(current_version)"
if [[ ! "$CURRENT" =~ ^[0-9]+\.[0-9]+\.[0-9]+(\.[0-9]+)?$ ]]; then
  echo "Versao atual do pom nao e semver: '$CURRENT'. Use --set X.Y.Z uma vez para normalizar." >&2
  exit 1
fi

# Base do range: a ultima tag semver. Enquanto ela nao existe, cai na ultima tag
# qualquer (as antigas, v327, sao contadores) para nao varrer o historico inteiro.
LAST_TAG="$(git tag --list 'v[0-9]*.[0-9]*.[0-9]*' --sort=-v:refname | head -1 || true)"
if [[ -z "$LAST_TAG" ]]; then
  LAST_TAG="$(git describe --tags --abbrev=0 2>/dev/null || true)"
fi

if [[ -n "$SET_VERSION" ]]; then
  if [[ ! "$SET_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(\.[0-9]+)?$ ]]; then
    echo "--set exige X.Y.Z[.B], recebido: $SET_VERSION" >&2
    exit 2
  fi
  NEW="$SET_VERSION"
  LEVEL="manual"
else
  RANGE="${LAST_TAG:+${LAST_TAG}..HEAD}"
  # Merge nao descreve mudanca: os commits que ele traz ja estao no range.
  COMMITS="$(git log --no-merges --format='%B%n' ${RANGE:-} 2>/dev/null || true)"
  SUBJECTS="$(git log --no-merges --format='%s' ${RANGE:-} 2>/dev/null || true)"

  if [[ -n "$FORCE_LEVEL" ]]; then
    LEVEL="$FORCE_LEVEL"
  elif [[ -z "$(echo "$COMMITS" | tr -d '[:space:]')" ]]; then
    echo "Nenhum commit novo desde ${LAST_TAG:-o inicio}; nada a versionar."
    echo "$CURRENT"
    exit 0
  elif grep -qiE '^BREAKING[ -]CHANGE|^[a-z]+(\([^)]*\))?!:' <<<"$COMMITS"; then
    LEVEL="major"
  elif grep -qiE '^(feat|add)(\([^)]*\))?!?:' <<<"$SUBJECTS"; then
    LEVEL="minor"
  elif grep -qE '^[a-zA-Z]+(\([^)]*\))?!?:' <<<"$SUBJECTS"; then
    LEVEL="patch"
  else
    # Nenhum commit com prefixo convencional: mexeu em algo, mas nao se declarou.
    LEVEL="build"
  fi

  IFS='.' read -r MAJOR MINOR PATCH BUILD <<<"$CURRENT"
  BUILD="${BUILD:-0}"
  case "$LEVEL" in
    major) NEW="$((MAJOR + 1)).0.0" ;;
    minor) NEW="${MAJOR}.$((MINOR + 1)).0" ;;
    patch) NEW="${MAJOR}.${MINOR}.$((PATCH + 1))" ;;
    build) NEW="${MAJOR}.${MINOR}.${PATCH}.$((BUILD + 1))" ;;
    *) echo "--release invalido: $LEVEL" >&2; exit 2 ;;
  esac
fi

echo "▶ Versao atual: $CURRENT"
echo "▶ Bump ($LEVEL): $NEW"

if [[ "$DRY_RUN" == "true" ]]; then
  echo "(dry-run, pom.xml intacto)"
  if [[ "$DO_CHANGELOG" == "true" ]]; then
    echo "▶ Secao do CHANGELOG.md:"
    "${PROJECT_ROOT}/scripts/changelog.sh" --version "$NEW" --from "$LAST_TAG" --to HEAD --print-only
  fi
  echo "$NEW"
  exit 0
fi

if [[ "$DO_COMMIT" == "true" && "$ALLOW_DIRTY" != "true" ]]; then
  # Commitar o pom junto de mudancas soltas faria a tag apontar para um estado misturado.
  if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
    echo "Working tree com mudancas nao commitadas. Commite antes ou use --allow-dirty/--no-commit." >&2
    exit 1
  fi
fi

python3 - "$POM" "$CURRENT" "$NEW" <<'PY'
import sys
pom_path, current, new = sys.argv[1], sys.argv[2], sys.argv[3]
pom = open(pom_path, encoding='utf-8').read()
head, tail = pom.split('</parent>', 1)
tag = f'<version>{current}</version>'
if tag not in tail:
    raise SystemExit(f'nao encontrei {tag} depois de </parent>')
open(pom_path, 'w', encoding='utf-8').write(head + '</parent>' + tail.replace(tag, f'<version>{new}</version>', 1))
PY
echo "✅ pom.xml: $CURRENT -> $NEW"

if [[ "$DO_CHANGELOG" == "true" ]]; then
  "${PROJECT_ROOT}/scripts/changelog.sh" --version "$NEW" --from "$LAST_TAG" --to HEAD
  echo "✅ CHANGELOG.md atualizado"
fi

if [[ "$DO_COMMIT" != "true" ]]; then
  echo "$NEW"
  exit 0
fi

git add pom.xml
if [[ "$DO_CHANGELOG" == "true" ]]; then git add CHANGELOG.md; fi
git commit -q -m "chore(release): ${NEW}"
git tag -a "v${NEW}" -m "release ${NEW}"
echo "🏷️  commit + tag v${NEW} criados"

if [[ "$DO_PUSH" == "true" ]]; then
  BRANCH="$(git rev-parse --abbrev-ref HEAD)"
  if git rev-parse --abbrev-ref --symbolic-full-name '@{u}' >/dev/null 2>&1; then
    git push -q origin "$BRANCH" && git push -q origin "v${NEW}"
    echo "⬆️  ${BRANCH} e v${NEW} enviados"
  else
    # Sem upstream a tag apontaria para um commit que so existe aqui.
    echo "⚠️  Branch ${BRANCH} sem upstream: commit e tag ficaram locais."
  fi
fi

echo "$NEW"
