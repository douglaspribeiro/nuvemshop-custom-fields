#!/usr/bin/env bash
# Builda a imagem SÓ para linux/arm64 (a VM Oracle Always Free é ARM64) e
# envia para o GHCR. Como o JAR já é buildado localmente, o build arm64 é
# rápido mesmo em host amd64 (sem Maven sob QEMU).
# Uso: ./scripts/push-docker.sh [--release major|minor|patch|build] [--no-bump] [--tag TAG]
#
# Pré-requisitos (uma vez por máquina):
#   docker buildx create --use --name multi-arch-builder
#   echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin

set -euo pipefail

GHCR_USER="${GHCR_USER:-douglaspribeiro}"
IMAGE="ghcr.io/$GHCR_USER/nuvemshop-custom-fields"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"
CUSTOM_TAG=""
BUMP=true
PRECHECK=true
CLEANUP_GHCR=false
CLEANUP_APPLY=false
CLEANUP_KEEP=20
CLEANUP_MIN_AGE=30
CLEANUP_PROTECTED_FILE="${GHCR_PROTECTED_REFS_FILE:-}"
BUMP_ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag) CUSTOM_TAG="${2:?--tag exige TAG}"; BUMP=false; shift 2 ;;
    --no-bump) BUMP=false; shift ;;
    --skip-precheck) PRECHECK=false; shift ;;
    --release) BUMP_ARGS+=(--release "${2:?--release exige major|minor|patch|build}"); shift 2 ;;
    --set-version) BUMP_ARGS+=(--set "${2:?--set-version exige X.Y.Z[.B]}"); shift 2 ;;
    --allow-dirty) BUMP_ARGS+=(--allow-dirty); shift ;;
    --cleanup-ghcr) CLEANUP_GHCR=true; shift ;;
    --cleanup-ghcr-apply) CLEANUP_GHCR=true; CLEANUP_APPLY=true; shift ;;
    --ghcr-keep) CLEANUP_KEEP="${2:?--ghcr-keep exige quantidade}"; shift 2 ;;
    --ghcr-min-age-days) CLEANUP_MIN_AGE="${2:?--ghcr-min-age-days exige dias}"; shift 2 ;;
    --ghcr-protected-refs) CLEANUP_PROTECTED_FILE="${2:?--ghcr-protected-refs exige arquivo}"; shift 2 ;;
    -h|--help)
      echo "Uso: $0 [--release major|minor|patch|build] [--set-version X.Y.Z[.B]] [--no-bump] [--tag TAG] [--allow-dirty] [--skip-precheck]"
      echo "GHCR: --cleanup-ghcr simula a limpeza após publicar; --cleanup-ghcr-apply exclui com arquivo de proteção."
      echo "      --ghcr-keep N (20), --ghcr-min-age-days N (30), --ghcr-protected-refs arquivo.json"
      exit 0 ;;
    *) echo "Argumento desconhecido: $1" >&2; exit 2 ;;
  esac
done

if [[ "$CLEANUP_GHCR" == true ]]; then
  if [[ ! "$CLEANUP_KEEP" =~ ^[1-9][0-9]*$ || ! "$CLEANUP_MIN_AGE" =~ ^[1-9][0-9]*$ ]]; then
    echo "Retenção GHCR exige quantidades positivas." >&2
    exit 2
  fi
  if [[ -z "${GHCR_TOKEN:-}" ]]; then
    echo "Defina GHCR_TOKEN para consultar/limpar o pacote; o login Docker sozinho não basta." >&2
    exit 2
  fi
  if [[ "$CLEANUP_APPLY" == true && ( -z "$CLEANUP_PROTECTED_FILE" || ! -f "$CLEANUP_PROTECTED_FILE" ) ]]; then
    echo "Exclusão GHCR exige --ghcr-protected-refs com produção e rollback revisados." >&2
    exit 2
  fi
fi

# Epoch FIXO p/ build reproduzível (deve casar com o ARG SOURCE_DATE_EPOCH do
# Dockerfile). Junto com rewrite-timestamp=true, reescreve TODOS os timestamps das
# camadas → camada de deps byte-idêntica entre builds → o registry deduplica (não
# reenvia ~93 MB num deploy que mexe só no código). 2020-01-01 UTC.
export SOURCE_DATE_EPOCH=1577836800

# Testes antes de criar o commit de release.
MVN=$([ -x ./mvnw ] && echo "./mvnw" || echo "mvn")
MVN_VERSION=$("$MVN" -version)
if ! grep -q "Java version: 25" <<< "$MVN_VERSION"; then
  echo "❌ Maven precisa rodar com Java 25, pois o pom.xml compila com release 25." >&2
  echo "   Ajuste JAVA_HOME/PATH antes de publicar a imagem." >&2
  echo "$MVN_VERSION" >&2
  exit 1
fi
if [[ "$PRECHECK" == true ]]; then
  echo "▶ Pre-check: Maven clean test"
  if ! "$MVN" -B -ntp clean test; then
    echo "❌ Pre-check falhou: nada foi versionado nem publicado." >&2
    exit 1
  fi
fi

# Commit e tag ficam locais ate a imagem ser publicada com sucesso.
if [[ "$BUMP" == true ]]; then
  RELEASE_VERSION="$(./scripts/release-version.sh --no-push "${BUMP_ARGS[@]}" | tee /dev/stderr | tail -1)"
  VERSION="v${RELEASE_VERSION}"
else
  RELEASE_VERSION="$(python3 -c 'import xml.etree.ElementTree as E; print(E.parse("pom.xml").getroot().find("{http://maven.apache.org/POM/4.0.0}version").text)')"
  VERSION="${CUSTOM_TAG:-v${RELEASE_VERSION}}"
fi
GIT_SHA=$(git rev-parse HEAD)
GIT_MSG=$(git log -1 --pretty=%s)

# Reempacota com a nova versao; os testes ja passaram antes do bump.
if ! "$MVN" -B -ntp clean package -DskipTests -Dskip.frontend.tests=true; then
  echo "❌ Build falhou — imagem nao publicada; release permanece local." >&2
  exit 1
fi

echo "▶ Empacotando ${IMAGE}  versão ${VERSION}  (linux/arm64)"
echo "  commit: ${GIT_SHA} — ${GIT_MSG}"
echo "  JAR Spring Boot em Java 25 + Tomcat embarcado 10.1.41 (sem compilar no Docker) — rápido."

# Garante que o builder multi-arch existe
if ! docker buildx inspect multi-arch-builder > /dev/null 2>&1; then
  echo "  Criando builder multi-arch..."
  docker buildx create --use --name multi-arch-builder
else
  docker buildx use multi-arch-builder
fi

docker buildx build \
  --platform linux/arm64 \
  --build-arg APP_VERSION="${VERSION}" \
  --build-arg SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH}" \
  -t "${IMAGE}:latest" \
  -t "${IMAGE}:${VERSION}" \
  --label "org.opencontainers.image.revision=${GIT_SHA}" \
  --label "org.opencontainers.image.version=${VERSION}" \
  --label "app.commit.message=${GIT_MSG}" \
  --output type=image,push=true,rewrite-timestamp=true \
  .

echo ""
echo "✅ Imagem publicada: ${IMAGE}:${VERSION}  (e :latest)"

# Publica o commit que contem pom/changelog e sua tag.
if [[ "$BUMP" == true ]]; then
  BRANCH="$(git symbolic-ref --short HEAD)"
  git push origin "$BRANCH"
fi
if ! git show-ref --verify --quiet "refs/tags/$VERSION"; then
  git tag -a "$VERSION" -m "build $VERSION — $GIT_MSG"
fi
git push origin "refs/tags/$VERSION"

echo "   O sync-infra detectará o novo digest de :latest e fará o deploy."

# Para o container do builder (libera RAM/CPU); retomado no próximo build.
echo "▶ Parando builder multi-arch..."
docker buildx stop multi-arch-builder >/dev/null 2>&1 || true

if [[ "$CLEANUP_GHCR" == true ]]; then
  CLEANUP_ARGS=(--owner "$GHCR_USER" --keep "$CLEANUP_KEEP" --min-age-days "$CLEANUP_MIN_AGE" --protect-ref "$VERSION" --protect-ref latest)
  if [[ -n "$CLEANUP_PROTECTED_FILE" ]]; then CLEANUP_ARGS+=(--protected-refs-file "$CLEANUP_PROTECTED_FILE"); fi
  if [[ "$CLEANUP_APPLY" == true ]]; then CLEANUP_ARGS+=(--apply); fi
  if ! GHCR_TOKEN="$GHCR_TOKEN" python3 scripts/cleanup-ghcr.py "${CLEANUP_ARGS[@]}"; then
    echo "A imagem já foi publicada, mas a limpeza GHCR não concluiu. Revise e execute scripts/cleanup-ghcr.py separadamente." >&2
    exit 1
  fi
fi
