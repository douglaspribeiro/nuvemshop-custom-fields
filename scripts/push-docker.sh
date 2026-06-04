#!/usr/bin/env bash
# Builda a imagem SÓ para linux/arm64 (a VM Oracle Always Free é ARM64) e
# envia para o GHCR. Como o JAR já é buildado localmente, o build arm64 é
# rápido mesmo em host amd64 (sem Maven sob QEMU).
# Uso: ./scripts/push-docker.sh [--tag TAG]
#
# Pré-requisitos (uma vez por máquina):
#   docker buildx create --use --name multi-arch-builder
#   echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin

set -euo pipefail

GHCR_USER="${GHCR_USER:-douglaspribeiro}"
IMAGE="ghcr.io/$GHCR_USER/nuvemshop-custom-fields"
CUSTOM_TAG=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tag)
      if [[ $# -lt 2 || -z "${2:-}" ]]; then
        echo "Uso: $0 [--tag TAG]" >&2
        exit 2
      fi
      CUSTOM_TAG="$2"
      shift 2
      ;;
    *)
      echo "Argumento desconhecido: $1" >&2
      echo "Uso: $0 [--tag TAG]" >&2
      exit 2
      ;;
  esac
done

# Epoch FIXO p/ build reproduzível (deve casar com o ARG SOURCE_DATE_EPOCH do
# Dockerfile). Junto com rewrite-timestamp=true, reescreve TODOS os timestamps das
# camadas → camada de deps byte-idêntica entre builds → o registry deduplica (não
# reenvia ~93 MB num deploy que mexe só no código). 2020-01-01 UTC.
export SOURCE_DATE_EPOCH=1577836800

# --- Versão a partir do git --------------------------------------------------
# VERSION = build number monotônico (nº de commits). Ex: v487.
GIT_SHA=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
GIT_MSG=$(git log -1 --pretty=%s 2>/dev/null || echo "")
VERSION="${CUSTOM_TAG:-v$(git rev-list --count HEAD 2>/dev/null || echo 0)}"

# Aviso se há mudanças não commitadas — a imagem incluiria esses arquivos, mas a
# tag git $VERSION aponta só pro commit $GIT_SHA (imagem não reproduzível pela tag).
if [[ -n "$(git status --porcelain 2>/dev/null || true)" ]]; then
  echo "⚠️  Working tree com mudanças não commitadas. A imagem vai incluí-las,"
  echo "    mas a tag git $VERSION aponta só pro commit $GIT_SHA. Commite antes pra rastreabilidade."
fi

# --- Gate de testes: roda mvn clean package COM testes. Aborta o push se falhar.
echo "▶ Rodando testes (mvn clean package, sem skip)..."
MVN=$([ -x ./mvnw ] && echo "./mvnw" || echo "mvn")
MVN_VERSION=$("$MVN" -version)
if ! grep -q "Java version: 25" <<< "$MVN_VERSION"; then
  echo "❌ Maven precisa rodar com Java 25, pois o pom.xml compila com release 25." >&2
  echo "   Ajuste JAVA_HOME/PATH antes de publicar a imagem." >&2
  echo "$MVN_VERSION" >&2
  exit 1
fi
if ! "$MVN" clean package; then
  echo "❌ Build/testes falharam — push abortado (imagem NÃO publicada)." >&2
  exit 1
fi
echo "✅ Testes passaram."

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

# Tag git no commit que gerou a imagem (só se ainda não existir)
if git rev-parse "$VERSION" >/dev/null 2>&1; then
  echo "ℹ️  Tag git $VERSION já existe — pulando"
else
  git tag -a "$VERSION" -m "build $VERSION — $GIT_MSG" \
    && git push origin "$VERSION" \
    && echo "🏷️  Tag git $VERSION criada e pushada"
fi

echo "   O sync-infra detectará o novo digest de :latest e fará o deploy."

# Para o container do builder (libera RAM/CPU); retomado no próximo build.
echo "▶ Parando builder multi-arch..."
docker buildx stop multi-arch-builder >/dev/null 2>&1 || true
